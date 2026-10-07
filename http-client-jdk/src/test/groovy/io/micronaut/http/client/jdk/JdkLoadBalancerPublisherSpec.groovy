package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.client.loadbalance.FixedLoadBalancer
import io.micronaut.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory
import io.micronaut.http.client.loadbalance.ServiceInstanceListRoundRobinLoadBalancer
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * The JDK client selects through the publisher of a load balancer that overrides it, and a
 * discovery client with publishers finds the propagated context of the request in its Reactor
 * context.
 */
class JdkLoadBalancerPublisherSpec extends Specification {

    static final String SPEC = 'JdkLoadBalancerPublisherSpec'

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC])

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'spec.name': SPEC,
            'micronaut.http.services.custom-service.url': 'http://unreachable.invalid:1'
    ])

    HttpClient client(String serviceId) {
        ctx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, serviceId, null)
    }

    def 'a subclass of the fixed load balancer that overrides select is selected through it'() {
        given:
        def calls = new AtomicInteger()
        def loadBalancer = new FixedLoadBalancer(URI.create('http://unreachable.invalid:1')) {
            @Override
            Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                calls.incrementAndGet()
                Mono.just(ServiceInstance.of('server', server.URI))
            }
        }

        expect:
        loadBalancer.selectAsync(null).toCompletableFuture().join().URI == server.URI
        calls.get() == 1
    }

    def 'a replaced load balancer factory with a subclass that overrides select is selected through it'() {
        given:
        CustomFactory factory = ctx.getBean(CustomFactory)
        factory.uri = server.URI

        expect:
        Mono.from(client('custom-service').retrieve(HttpRequest.GET('/jdk-lb-publisher/hello'), String)).block() == 'hello'
        factory.calls.get() == 1
    }

    def 'a discovery client with publishers finds the propagated context of the request in its Reactor context'() {
        given:
        ContextDiscoveryClient discoveryClient = ctx.getBean(ContextDiscoveryClient)
        discoveryClient.uri = server.URI
        HttpClient client = client('context-service')
        def propagatedContext = PropagatedContext.empty().plus(new TestElement('request'))

        when:
        // a request sent while the context is bound, as a controller of the server does
        String body = propagatedContext.propagate({ Mono.from(client.retrieve(HttpRequest.GET('/jdk-lb-publisher/hello'), String)).block() } as Supplier<String>)

        then:
        body == 'hello'
        discoveryClient.seen == ['request']

        when:
        // the context of a Reactor subscriber
        discoveryClient.seen.clear()
        body = Mono.from(client.retrieve(HttpRequest.GET('/jdk-lb-publisher/hello'), String))
            .contextWrite { context -> ReactorPropagation.addPropagatedContext(context, propagatedContext) }
            .block()

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
    @Replaces(ServiceInstanceListLoadBalancerFactory)
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerPublisherSpec')
    static class CustomFactory extends ServiceInstanceListLoadBalancerFactory {
        final AtomicInteger calls = new AtomicInteger()
        volatile URI uri

        @Override
        LoadBalancer create(ServiceInstanceList serviceInstanceList) {
            new ServiceInstanceListRoundRobinLoadBalancer(serviceInstanceList) {
                @Override
                Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                    calls.incrementAndGet()
                    Mono.just(ServiceInstance.of('server', uri))
                }
            }
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerPublisherSpec')
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
        }
    }

    @Controller('/jdk-lb-publisher')
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerPublisherSpec')
    static class HelloController {
        @Get('/hello')
        String hello() {
            'hello'
        }
    }
}
