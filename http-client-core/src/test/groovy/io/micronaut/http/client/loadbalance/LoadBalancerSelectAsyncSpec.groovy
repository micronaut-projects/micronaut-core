package io.micronaut.http.client.loadbalance

import io.micronaut.core.async.publisher.CompletionStagePublishers
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.DefaultCompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.discovery.exceptions.NoAvailableServiceException
import io.micronaut.health.HealthStatus
import io.micronaut.http.client.LoadBalancer
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LoadBalancerSelectAsyncSpec extends Specification {

    static final ServiceInstance ONE = ServiceInstance.of('svc', URI.create('http://one:8080'))
    static final ServiceInstance TWO = ServiceInstance.of('svc', URI.create('http://two:8080'))

    void 'the default selectAsync adapts the first instance of select'() {
        given:
        def discriminators = []
        LoadBalancer loadBalancer = { discriminator ->
            discriminators << discriminator
            Flux.just(ONE, TWO)
        } as LoadBalancer

        expect:
        loadBalancer.selectAsync('key').toCompletableFuture().getNow(null).is(ONE)
        loadBalancer.selectAsync().toCompletableFuture().getNow(null).is(ONE)
        discriminators == ['key', null]
    }

    void 'the default selectAsync completes with null when select is empty'() {
        given:
        LoadBalancer loadBalancer = { discriminator -> Publishers.empty() } as LoadBalancer

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()

        then:
        future.isDone()
        !future.isCompletedExceptionally()
        future.getNow(ONE) == null
    }

    void 'the default selectAsync fails with the error of select'() {
        when:
        LoadBalancer.empty().selectAsync(null).toCompletableFuture().get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof NoAvailableServiceException
        e.cause.message == 'No available services for ID: Load balancer contains no servers'
    }

    void 'cancelling the default selectAsync cancels the subscription to select'() {
        given:
        def cancelled = new AtomicInteger()
        LoadBalancer loadBalancer = { discriminator -> Flux.never().doOnCancel(() -> cancelled.incrementAndGet()) } as LoadBalancer

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()

        then:
        !future.isDone()

        when:
        future.cancel(false)

        then:
        cancelled.get() == 1
    }

    void 'the fixed load balancer selects its instance right away'() {
        given:
        def loadBalancer = new FixedLoadBalancer(URI.create('http://fixed:8080/ctx'))

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()

        then:
        future.getNow(null).is(loadBalancer.serviceInstance)
        loadBalancer.selectAsync().toCompletableFuture().getNow(null).is(loadBalancer.serviceInstance)
    }

    void 'the service instance list load balancer selects right away, round robin'() {
        given:
        def loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(
                new StaticServiceInstanceList('svc', [URI.create('http://one:8080'), URI.create('http://two:8080')]))

        when:
        def hosts = (1..4).collect { loadBalancer.selectAsync(null).toCompletableFuture().getNow(null).URI.host }

        then:
        hosts == ['one', 'two', 'one', 'two']
    }

    void 'the service instance list load balancer fails the stage when no instance is available'() {
        given:
        def loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(new StaticServiceInstanceList('svc', []))

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()

        then:
        future.isCompletedExceptionally()

        when:
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof NoAvailableServiceException
        e.cause.message == 'No available services for ID: svc'
    }

    void 'the discovery client load balancer selects from getInstancesAsync'() {
        given:
        def discoveryClient = new AsyncOnlyDiscoveryClient()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        when:
        def first = loadBalancer.selectAsync(null).toCompletableFuture()

        then:
        !first.isDone()
        discoveryClient.requested == ['svc']

        when:
        discoveryClient.futures[0].complete([ONE, TWO])

        then:
        first.getNow(null).is(ONE)

        when:
        def second = loadBalancer.selectAsync(null).toCompletableFuture()
        discoveryClient.futures[1].complete([ONE, TWO])

        then:
        second.getNow(null).is(TWO)
    }

    void 'the discovery client load balancer skips the instances that are down'() {
        given:
        def down = ServiceInstance.builder('svc', URI.create('http://down:8080')).status(HealthStatus.DOWN).build()
        def discoveryClient = new AsyncOnlyDiscoveryClient()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()
        discoveryClient.futures[0].complete([down, TWO])

        then:
        future.getNow(null).is(TWO)
    }

    void 'the discovery client load balancer fails when no instance is available'() {
        given:
        def discoveryClient = new AsyncOnlyDiscoveryClient()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()
        discoveryClient.futures[0].complete([])
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof NoAvailableServiceException
        e.cause.message == 'No available services for ID: svc'
    }

    void 'the discovery client load balancer fails with the error of the discovery client'() {
        given:
        def discoveryClient = new AsyncOnlyDiscoveryClient()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)
        def error = new IllegalStateException('discovery down')

        when:
        def future = loadBalancer.selectAsync(null).toCompletableFuture()
        discoveryClient.futures[0].completeExceptionally(error)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    void 'cancelling the discovery client selection cancels the lookup'() {
        given:
        def discoveryClient = new AsyncOnlyDiscoveryClient()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        when:
        loadBalancer.selectAsync(null).toCompletableFuture().cancel(false)

        then:
        discoveryClient.futures[0].isCancelled()
    }

    void 'the discovery client load balancer adapts a discovery client that only has publishers'() {
        given:
        DiscoveryClient discoveryClient = new PublisherOnlyDiscoveryClient(instances: [ONE])
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        expect:
        loadBalancer.selectAsync(null).toCompletableFuture().getNow(null).is(ONE)
        Flux.from(loadBalancer.select(null)).blockFirst().is(ONE)
    }

    void 'the discovery client load balancer fails when the publisher of the discovery client is empty'() {
        given:
        DiscoveryClient discoveryClient = new PublisherOnlyDiscoveryClient(instances: null)
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        when:
        loadBalancer.selectAsync(null).toCompletableFuture().get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof NoAvailableServiceException
    }

    void 'the factories create the built-in load balancers, which select without a publisher'() {
        expect:
        new DiscoveryClientLoadBalancerFactory(new AsyncOnlyDiscoveryClient()).create('svc').getClass() == DiscoveryClientRoundRobinLoadBalancer
        new ServiceInstanceListLoadBalancerFactory().create(new StaticServiceInstanceList('svc', [URI.create('http://one:8080')])).getClass() == ServiceInstanceListRoundRobinLoadBalancer
        LoadBalancer.fixed(URI.create('http://fixed:8080')).getClass() == FixedLoadBalancer
        LoadBalancer.fixed(URI.create('http://fixed:8080').toURL()).getClass() == FixedLoadBalancer
    }

    void 'a discovery client mock that only stubs getInstances is selected through it'() {
        given:
        DiscoveryClient discoveryClient = Mock(DiscoveryClient)
        discoveryClient.getInstances('svc') >> Flux.just([ONE])
        def loadBalancer = new DiscoveryClientLoadBalancerFactory(new DefaultCompositeDiscoveryClient(discoveryClient)).create('svc')

        expect:
        loadBalancer.selectAsync().toCompletableFuture().get(5, TimeUnit.SECONDS).is(ONE)
    }

    void 'a discovery client stage that completes with null is replaced by the publisher'() {
        given:
        DiscoveryClient discoveryClient = new PublisherOnlyDiscoveryClient() {
            @Override
            CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
                CompletableFuture.completedFuture(null)
            }
        }
        discoveryClient.instances = [TWO]

        expect:
        new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient).selectAsync().toCompletableFuture().getNow(null).is(TWO)
    }

    void 'cancelling one selection does not cancel a shared stage of the discovery client'() {
        given:
        def shared = new CompletableFuture<List<ServiceInstance>>()
        DiscoveryClient discoveryClient = new PublisherOnlyDiscoveryClient() {
            @Override
            CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
                shared
            }
        }
        def loadBalancer = new DiscoveryClientLoadBalancerFactory(new DefaultCompositeDiscoveryClient(discoveryClient)).create('svc')

        when:
        loadBalancer.selectAsync().toCompletableFuture().cancel(false)
        def second = loadBalancer.selectAsync().toCompletableFuture()
        shared.complete([ONE])

        then:
        !shared.isCancelled()
        second.getNow(null).is(ONE)
    }

    void 'a subclass of the fixed load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new FixedLoadBalancer(URI.create('http://fixed:8080')) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Publishers.just(TWO)
            }
        }

        expect:
        loadBalancer.selectAsync(null).toCompletableFuture().getNow(null).is(TWO)
        calls.get() == 1
    }

    void 'a subclass of the service instance list load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(new StaticServiceInstanceList('svc', [URI.create('http://one:8080')])) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Publishers.just(TWO)
            }
        }

        expect:
        loadBalancer.selectAsync(null).toCompletableFuture().getNow(null).is(TWO)
        calls.get() == 1
    }

    void 'a subclass of the discovery client load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', new AsyncOnlyDiscoveryClient()) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Publishers.just(TWO)
            }
        }

        expect:
        loadBalancer.selectAsync(null).toCompletableFuture().getNow(null).is(TWO)
        calls.get() == 1
    }

    void 'the discovery client load balancer selects from the publisher of the discovery client, which it cancels'() {
        given:
        def cancelled = new AtomicInteger()
        DiscoveryClient discoveryClient = new PublisherOnlyDiscoveryClient() {
            @Override
            Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                Flux.<List<ServiceInstance>> never().doOnCancel(() -> cancelled.incrementAndGet())
            }
        }

        when:
        def disposable = Flux.from(new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient).select(null)).subscribe()
        disposable.dispose()

        then:
        cancelled.get() == 1

        when:
        new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient).selectAsync(null).toCompletableFuture().cancel(false)

        then:
        cancelled.get() == 2
    }

    void 'the discovery client load balancer selects right away from instances that are already known'() {
        given:
        DiscoveryClient discoveryClient = new PublisherOnlyDiscoveryClient(instances: [ONE, TWO])
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient)

        expect:
        (1..3).collect { loadBalancer.selectAsync(null).toCompletableFuture().getNow(null) } == [ONE, TWO, ONE]
    }

    /**
     * Fails the publisher methods, so that only the stages can serve. Its stages are new for each
     * call, so the framework may cancel them.
     */
    static class AsyncOnlyDiscoveryClient implements DiscoveryClient {
        final List<String> requested = []
        final List<CompletableFuture<List<ServiceInstance>>> futures = []

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            throw new UnsupportedOperationException('publisher')
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            throw new UnsupportedOperationException('publisher')
        }

        @Override
        CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            requested << serviceId
            CompletableFuture<List<ServiceInstance>> future = CompletionStagePublishers.future()
            futures << future
            return future
        }

        @Override
        String getDescription() {
            'async-only'
        }

        @Override
        void close() {
        }
    }

    static class PublisherOnlyDiscoveryClient implements DiscoveryClient {
        @Nullable
        List<ServiceInstance> instances

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            instances == null ? Publishers.empty() : Publishers.just(instances)
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            Publishers.just(['svc'])
        }

        @Override
        String getDescription() {
            'publisher-only'
        }

        @Override
        void close() {
        }
    }
}
