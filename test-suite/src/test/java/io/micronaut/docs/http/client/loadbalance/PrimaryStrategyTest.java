package io.micronaut.docs.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.LoadBalancerResolver;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrimaryStrategyTest {

    @Test
    void theNamedStrategyBeanPicksTheInstance() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "PrimaryStrategyTest",
            "micronaut.http.services.foo.urls", List.of("http://foo1", "http://foo2"),
            "micronaut.http.services.foo.load-balancer-strategy", "primary"))) {
            var loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("foo").orElseThrow();
            for (int i = 0; i < 3; i++) {
                assertEquals("foo1", Mono.from(loadBalancer.select()).block().getURI().getHost());
            }
        }
    }
}
