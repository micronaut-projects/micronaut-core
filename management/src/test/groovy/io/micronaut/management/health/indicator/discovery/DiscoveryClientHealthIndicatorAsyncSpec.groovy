package io.micronaut.management.health.indicator.discovery

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.async.publisher.CompletionStagePublishers
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

class DiscoveryClientHealthIndicatorAsyncSpec extends Specification {

    void 'a publisher-only discovery client is reported through the default async methods'() {
        given:
        DiscoveryClient client = new PublisherOnlyClient('publisher', [
                foo: [ServiceInstance.of('foo', new URI('http://foo:8080'))],
                bar: [ServiceInstance.of('bar', new URI('http://bar:8080')), ServiceInstance.of('bar', new URI('http://bar:8081'))]
        ])
        def indicator = new DiscoveryClientHealthIndicator(client)

        when:
        HealthResult async = indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()
        HealthResult published = Mono.from(indicator.result).block()

        then:
        async.name == 'publisher'
        async.status == HealthStatus.UP
        async.details == [services: [foo: [new URI('http://foo:8080')], bar: [new URI('http://bar:8080'), new URI('http://bar:8081')]]]
        published.name == async.name
        published.status == async.status
        published.details == async.details
    }

    void 'a native async discovery client is not subscribed to'() {
        given:
        DiscoveryClient client = new AsyncOnlyClient('async', CompletableFuture.completedFuture(['foo']),
                [foo: CompletableFuture.completedFuture([ServiceInstance.of('foo', new URI('http://foo:8080'))])])

        when:
        HealthResult result = new DiscoveryClientHealthIndicator(client).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()

        then:
        result.name == 'async'
        result.status == HealthStatus.UP
        result.details == [services: [foo: [new URI('http://foo:8080')]]]
    }

    void 'a configuration error of the composite client is retried with the uncached child clients'() {
        given:
        DiscoveryClient child = new AsyncOnlyClient('child', CompletableFuture.completedFuture(['foo']),
                [foo: CompletableFuture.completedFuture([ServiceInstance.of('foo', new URI('http://foo:8080'))])])
        DiscoveryClient composite = new FailingCompositeClient(child)

        when:
        HealthResult result = new DiscoveryClientHealthIndicator(composite).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()

        then:
        result.status == HealthStatus.UP
        result.details == [services: [foo: [new URI('http://foo:8080')]]]
    }

    void 'a failure of the discovery client is reported as DOWN'() {
        given:
        DiscoveryClient client = new AsyncOnlyClient('failing', CompletableFuture.completedFuture(['foo']),
                [foo: CompletableFuture.failedFuture(error)])

        when:
        HealthResult async = new DiscoveryClientHealthIndicator(client).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()

        then:
        async.name == 'failing'
        async.status == HealthStatus.DOWN
        async.details == [error: error.class.name + ': ' + error.message]

        where:
        error << [new IllegalStateException('unreachable'), new ConfigurationException('not composite, so not retried')]
    }

    void 'a discovery client that throws is reported as DOWN'() {
        given:
        DiscoveryClient client = new AsyncOnlyClient('throwing', null, [:]) {
            @Override
            CompletionStage<List<String>> getServiceIdsAsync() {
                throw new IllegalStateException('thrown')
            }
        }

        when:
        HealthResult result = new DiscoveryClientHealthIndicator(client).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()

        then:
        result.status == HealthStatus.DOWN
        result.details == [error: 'java.lang.IllegalStateException: thrown']
    }

    void 'a subclass of the indicator that overrides getResult is called through it'() {
        given:
        def indicator = new DiscoveryClientHealthIndicator(new AsyncOnlyClient('async', new CompletableFuture<List<String>>(), [:])) {
            @Override
            Publisher<HealthResult> getResult() {
                return Mono.just(HealthResult.builder('overridden', HealthStatus.UP).build())
            }
        }

        expect:
        indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)*.name == ['overridden']
    }

    void 'cancelling the publisher of the indicator cancels the discovery client'() {
        given:
        def cancelled = new java.util.concurrent.atomic.AtomicInteger()
        DiscoveryClient publisherClient = new PublisherOnlyClient('publisher', [:]) {
            @Override
            Publisher<List<String>> getServiceIds() {
                return Mono.<List<String>> never().doOnCancel { cancelled.incrementAndGet() }
            }
        }
        CompletableFuture<List<String>> pending = CompletionStagePublishers.future()
        DiscoveryClient asyncClient = new AsyncOnlyClient('async', pending, [:])
        def shared = new CompletableFuture<List<String>>()
        DiscoveryClient sharedClient = new AsyncOnlyClient('shared', shared, [:])

        when:
        Mono.from(new DiscoveryClientHealthIndicator(publisherClient).result).subscribe().dispose()
        new DiscoveryClientHealthIndicator(publisherClient).resultAsync.toCompletableFuture().cancel(false)
        new DiscoveryClientHealthIndicator(asyncClient).resultAsync.toCompletableFuture().cancel(false)
        new DiscoveryClientHealthIndicator(sharedClient).resultAsync.toCompletableFuture().cancel(false)

        then:
        cancelled.get() == 2
        pending.cancelled
        !shared.done
    }

    void 'cancelling the indicator cancels the lookups of the instances and of the retry'() {
        given:
        def cancelled = new java.util.concurrent.atomic.AtomicInteger()
        DiscoveryClient publisherClient = new PublisherOnlyClient('publisher', [foo: []]) {
            @Override
            Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                return Mono.<List<ServiceInstance>> never().doOnCancel { cancelled.incrementAndGet() }
            }
        }

        when: 'the instances are being looked up'
        def result = new DiscoveryClientHealthIndicator(publisherClient).resultAsync.toCompletableFuture()
        result.cancel(false)

        then:
        cancelled.get() == 1

        when: 'the uncached child clients are being looked up'
        result = new DiscoveryClientHealthIndicator(new FailingCompositeClient(publisherClient)).resultAsync.toCompletableFuture()
        result.cancel(false)

        then:
        cancelled.get() == 2
    }

    void 'a mock discovery client that only stubs the publishers is called through them'() {
        given:
        DiscoveryClient client = Mock(DiscoveryClient)
        client.getDescription() >> 'mock'
        client.getServiceIds() >> Mono.just(['foo'])
        client.getInstances('foo') >> Mono.just([ServiceInstance.of('foo', new URI('http://foo:8080'))])

        when:
        HealthResult result = new DiscoveryClientHealthIndicator(client).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()

        then:
        result.status == HealthStatus.UP
        result.details == [services: [foo: [new URI('http://foo:8080')]]]
    }

    static class PublisherOnlyClient implements DiscoveryClient {
        final String description
        final Map<String, List<ServiceInstance>> instances

        PublisherOnlyClient(String description, Map<String, List<ServiceInstance>> instances) {
            this.description = description
            this.instances = instances
        }

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return Flux.just(instances[serviceId])
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            return Flux.just(instances.keySet().toList())
        }

        @Override
        String getDescription() {
            return description
        }

        @Override
        void close() {
        }
    }

    static class AsyncOnlyClient implements DiscoveryClient {
        final String description
        final CompletableFuture<List<String>> serviceIds
        final Map<String, CompletableFuture<List<ServiceInstance>>> instances

        AsyncOnlyClient(String description, CompletableFuture<List<String>> serviceIds, Map<String, CompletableFuture<List<ServiceInstance>>> instances) {
            this.description = description
            this.serviceIds = serviceIds
            this.instances = instances
        }

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            throw new UnsupportedOperationException('the indicator calls getInstancesAsync')
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            throw new UnsupportedOperationException('the indicator calls getServiceIdsAsync')
        }

        @Override
        CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            return instances[serviceId]
        }

        @Override
        CompletionStage<List<String>> getServiceIdsAsync() {
            return serviceIds
        }

        @Override
        String getDescription() {
            return description
        }

        @Override
        void close() {
        }
    }

    static class FailingCompositeClient extends CompositeDiscoveryClient {
        FailingCompositeClient(DiscoveryClient child) {
            super([child] as DiscoveryClient[])
        }

        @Override
        CompletionStage<List<String>> getServiceIdsAsync() {
            return CompletableFuture.failedFuture(new ConfigurationException('No cache configured for name: discovery-client'))
        }
    }
}
