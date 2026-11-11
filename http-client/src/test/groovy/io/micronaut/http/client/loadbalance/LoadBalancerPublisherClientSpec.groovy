package io.micronaut.http.client.loadbalance

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.LoadBalancer
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * The Netty client selects through the publisher of a load balancer that overrides it, and a
 * discovery client with publishers finds the propagated context of the request in its Reactor
 * context.
 */
class LoadBalancerPublisherClientSpec extends Specification {

    static final String SPEC = 'LoadBalancerPublisherClientSpec'

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC])

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': SPEC])

    String hello(LoadBalancer loadBalancer) {
        HttpClient client = ctx.createBean(HttpClient, loadBalancer)
        try {
            return Mono.from(client.retrieve(HttpRequest.GET('/lb-publisher/hello'), String)).block()
        } finally {
            client.close()
        }
    }

    def 'a mock load balancer that only stubs select is selected through it'() {
        given:
        LoadBalancer loadBalancer = Mock(LoadBalancer)
        loadBalancer.select(_) >> Publishers.just(ServiceInstance.of('server', server.URI))
        loadBalancer.getContextPath() >> Optional.empty()

        expect:
        hello(loadBalancer) == 'hello'
    }

    def 'a subclass of the fixed load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new FixedLoadBalancer(URI.create('http://unreachable.invalid:1')) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Publishers.just(ServiceInstance.of('server', server.URI))
            }
        }

        expect:
        hello(loadBalancer) == 'hello'
        calls.get() == 1
    }

    def 'a subclass of the service instance list load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(new StaticServiceInstanceList('svc', [URI.create('http://unreachable.invalid:1')])) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Mono.just(ServiceInstance.of('server', server.URI))
            }
        }

        expect:
        hello(loadBalancer) == 'hello'
        calls.get() == 1
    }

    def 'a subclass of the discovery client load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new DiscoveryClientRoundRobinLoadBalancer('svc', ctx.getBean(DiscoveryClient)) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Mono.delay(Duration.ofMillis(10)).map { ServiceInstance.of('server', server.URI) }
            }
        }

        expect:
        hello(loadBalancer) == 'hello'
        calls.get() == 1
    }

    def 'a discovery client with publishers finds the propagated context of the request in its Reactor context'() {
        given:
        ContextDiscoveryClient discoveryClient = ctx.getBean(ContextDiscoveryClient)
        discoveryClient.uri = server.URI
        HttpClient client = ctx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, 'context-service', null)

        when:
        def propagatedContext = PropagatedContext.empty().plus(new TestElement('request'))
        // a request sent while the context is bound, as a controller of the server does
        String body = propagatedContext.propagate({ Mono.from(client.retrieve(HttpRequest.GET('/lb-publisher/hello'), String)).block() } as Supplier<String>)

        then:
        body == 'hello'
        discoveryClient.seen == ['request']
    }

    static class TestElement implements PropagatedContextElement {
        final String value

        TestElement(String value) {
            this.value = value
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'LoadBalancerPublisherClientSpec')
    static class ContextDiscoveryClient implements DiscoveryClient {
        final List<String> seen = new CopyOnWriteArrayList<>()
        volatile URI uri

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            if (serviceId != 'context-service') {
                return Mono.just([])
            }
            Mono.deferContextual { view ->
                seen << ReactorPropagation.findContextElement(view, TestElement).map { it.value }.orElse('none')
                Mono.just([ServiceInstance.of(serviceId, uri)])
            }
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            Mono.just(['context-service'])
        }

        @Override
        String getDescription() {
            'context'
        }

        @Override
        void close() {
            // The fixture has no resources; the application context owns its client.
        }
    }

    @Controller('/lb-publisher')
    @Requires(property = 'spec.name', value = 'LoadBalancerPublisherClientSpec')
    static class HelloController {
        @Get('/hello')
        String hello() {
            'hello'
        }
    }
}
