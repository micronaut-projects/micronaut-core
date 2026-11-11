package io.micronaut.docs.http.client.loadbalance

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.LoadBalancerResolver
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

import static org.junit.jupiter.api.Assertions.assertEquals

class PrimaryStrategyTest {

    @Test
    void theNamedStrategyBeanPicksTheInstance() {
        try (ApplicationContext ctx = ApplicationContext.run(
                'spec.name': 'PrimaryStrategyTest',
                'micronaut.http.services.foo.urls': ['http://foo1', 'http://foo2'],
                'micronaut.http.services.foo.load-balancer-strategy': 'primary')) {
            def loadBalancer = ctx.getBean(LoadBalancerResolver).resolve('foo').orElseThrow()
            3.times {
                assertEquals('foo1', Mono.from(loadBalancer.select()).block().URI.host)
            }
        }
    }
}
