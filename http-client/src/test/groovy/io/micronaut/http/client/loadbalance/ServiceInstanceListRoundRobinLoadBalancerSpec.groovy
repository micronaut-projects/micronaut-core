package io.micronaut.http.client.loadbalance

import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceList
import org.reactivestreams.Publisher
import spock.lang.Specification

class ServiceInstanceListRoundRobinLoadBalancerSpec extends Specification {

    void "test exception is not thrown when a service is not available"() {
        given:
        ServiceInstanceListRoundRobinLoadBalancer balancer = new ServiceInstanceListRoundRobinLoadBalancer(new ServiceInstanceList() {
            @Override
            String getID() {
                return "test"
            }

            @Override
            List<ServiceInstance> getInstances() {
                return Collections.emptyList()
            }
        })

        when:
        Publisher<ServiceInstanceList> service = balancer.select(null)

        then:
        noExceptionThrown()
    }
}

