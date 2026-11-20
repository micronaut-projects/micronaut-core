package io.micronaut.http.client.loadbalance

import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.http.HttpRequest
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

class RoundRobinNextAvailableOverrideSpec extends Specification {

    static final ServiceInstance ONE = ServiceInstance.of('svc', URI.create('http://one:8080'))
    static final ServiceInstance TWO = ServiceInstance.of('svc', URI.create('http://two:8080'))

    void "a subclass overriding getNextAvailable(List) of a service instance list balancer is still called"() {
        given:
        def balancer = new ServiceInstanceListRoundRobinLoadBalancer(new StaticServiceInstanceList('svc', [ONE.URI, TWO.URI])) {
            @Override
            protected ServiceInstance getNextAvailable(List<ServiceInstance> serviceInstances) {
                return serviceInstances.last()
            }
        }

        expect:
        Mono.from(balancer.select(HttpRequest.GET('/'))).block().URI == TWO.URI
        Mono.from(balancer.select(HttpRequest.GET('/'))).block().URI == TWO.URI
        Mono.from(balancer.select(null)).block().URI == TWO.URI
        balancer.selectAsync(HttpRequest.GET('/')).toCompletableFuture().get().URI == TWO.URI
    }

    void "a subclass overriding getNextAvailable(List) of a discovery client balancer is still called"() {
        given:
        DiscoveryClient discoveryClient = [getInstances: { String id -> Publishers.just([ONE, TWO]) }] as DiscoveryClient
        def balancer = new DiscoveryClientRoundRobinLoadBalancer('svc', discoveryClient) {
            @Override
            protected ServiceInstance getNextAvailable(List<ServiceInstance> serviceInstances) {
                return serviceInstances.last()
            }
        }

        expect:
        Mono.from(balancer.select(HttpRequest.GET('/'))).block().is(TWO)
        Mono.from(balancer.select(HttpRequest.GET('/'))).block().is(TWO)
        Mono.from(balancer.select(null)).block().is(TWO)
    }
}
