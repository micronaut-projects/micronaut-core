package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancerResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * The names of the built-in strategies, the error for an unknown one, and large weights.
 */
class LoadBalancerStrategyNameTest {

    @Test
    void builtInNamesIgnoreCase() {
        Assertions.assertInstanceOf(LoadBalancerStrategies.PowerOfTwoChoices.class, LoadBalancerStrategy.of("P2C"));
        Assertions.assertInstanceOf(LoadBalancerStrategies.RoundRobin.class, LoadBalancerStrategy.of("Round-Robin"));
        Assertions.assertInstanceOf(LoadBalancerStrategies.Sticky.class, LoadBalancerStrategy.of("STICKY"));
    }

    @Test
    void anUnknownStrategyNamesTheServiceAndTheProperty() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.http.services.foo.urls", List.of("http://127.0.0.1:1"),
            "micronaut.http.services.foo.load-balancer-strategy", "nope"
        ))) {
            LoadBalancerResolver resolver = ctx.getBean(LoadBalancerResolver.class);
            Exception e = Assertions.assertThrows(Exception.class, () -> resolver.resolve("foo"));
            Assertions.assertTrue(e.getMessage().contains("nope"), e.getMessage());
            Assertions.assertTrue(e.getMessage().contains("micronaut.http.services.foo.load-balancer-strategy"), e.getMessage());
        }
    }

    @Test
    void aWeightAboveTheIntRangeIsCapped() {
        ServiceInstance heavy = ServiceInstance.builder("svc", URI.create("http://heavy:8080")).metadata(Map.of("weight", "3000000000")).build();
        Assertions.assertEquals(Integer.MAX_VALUE, LoadBalancerStrategies.Weighted.weight(heavy));
    }
}
