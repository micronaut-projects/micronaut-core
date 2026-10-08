package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.client.websocket.WebSocketConnectCancelSpec
import io.micronaut.websocket.context.WebSocketBeanRegistry
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A websocket connect and the load balancer that selects its instance.
 */
class WebSocketLoadBalancerSpec extends Specification {

    void 'the reactive connect selects the instance when it is called, as before'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(URI.create('http://127.0.0.1:1'))
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when: 'connect is called without a subscription'
        client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')

        then:
        balancer.selections.get() == 1

        cleanup:
        client?.close()
        ctx?.close()
    }

    void 'by default a handshake that succeeds only releases the selection'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(true)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        def endpoint = Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).block(Duration.ofSeconds(10))

        then:
        endpoint != null
        balancer.outcomes == [LoadBalancer.Outcome.CANCELLED]

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'with report-handshake-outcome a handshake that succeeds is reported as a success'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(true)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def configuration = new DefaultHttpClientConfiguration()
        configuration.reportHandshakeOutcome = true
        def client = client(ctx, balancer, configuration)

        when:
        def endpoint = Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).block(Duration.ofSeconds(10))

        then:
        endpoint != null
        balancer.outcomes == [LoadBalancer.Outcome.SUCCESS]

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a handshake timeout carries the service id, and is reported as a timeout with report-handshake-outcome #report'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(false)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def configuration = new DefaultHttpClientConfiguration()
        configuration.handshakeTimeout = Duration.ofSeconds(1)
        configuration.reportHandshakeOutcome = report
        def client = client(ctx, balancer, configuration)

        when:
        Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof ReadTimeoutException
        ((ReadTimeoutException) e.cause).serviceId == 'raw'
        balancer.outcomes == [outcome]

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()

        where:
        report | outcome
        false  | LoadBalancer.Outcome.CANCELLED
        true   | LoadBalancer.Outcome.TIMEOUT
    }

    void 'a cancelled connect releases the selection'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(false)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        def subscription = Flux.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).subscribe()
        server.awaitUpgradeRequest()
        subscription.dispose()

        then:
        server.awaitClosed()
        balancer.outcomes == [LoadBalancer.Outcome.CANCELLED]

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    private static DefaultHttpClient client(ApplicationContext ctx, LoadBalancer balancer, DefaultHttpClientConfiguration configuration) {
        return DefaultHttpClient.builder()
            .loadBalancer(balancer)
            .configuration(configuration)
            .informationalServiceId('raw')
            .webSocketBeanRegistry(WebSocketBeanRegistry.forClient(ctx))
            .build()
    }

    static class RecordingLoadBalancer implements LoadBalancer {
        final List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>()
        final AtomicInteger selections = new AtomicInteger()
        final ServiceInstance instance

        RecordingLoadBalancer(URI uri) {
            instance = ServiceInstance.of('raw', uri)
        }

        @Override
        Publisher<ServiceInstance> select(@Nullable Object discriminator) {
            return Mono.fromSupplier {
                selections.incrementAndGet()
                instance
            }
        }

        @Override
        void report(ServiceInstance serviceInstance, LoadBalancer.Outcome outcome) {
            outcomes.add(outcome)
        }
    }
}
