package io.micronaut.http.client.loadbalance;

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

/**
 * The power of two choices strategy forgets instances that went away, but not while their
 * exchanges are in flight.
 */
class PowerOfTwoChoicesChurnTest {

    private static ServiceInstance instance(int n) {
        return ServiceInstance.of("svc", URI.create("http://host-" + n + ":8080"));
    }

    @Test
    void departedInstancesAreForgotten() {
        LoadBalancerStrategies.PowerOfTwoChoices strategy = new LoadBalancerStrategies.PowerOfTwoChoices();
        for (int generation = 0; generation < 100; generation++) {
            List<ServiceInstance> instances = List.of(instance(generation * 2), instance(generation * 2 + 1));
            for (int i = 0; i < 4; i++) {
                strategy.report(strategy.select(instances, null), LoadBalancer.Outcome.SUCCESS);
            }
        }
        Assertions.assertTrue(strategy.size() <= 3, "retained " + strategy.size());
    }

    @Test
    void aDepartedInstanceKeepsItsCountWhileInFlight() {
        LoadBalancerStrategies.PowerOfTwoChoices strategy = new LoadBalancerStrategies.PowerOfTwoChoices();
        ServiceInstance old = instance(0);
        Assertions.assertSame(old, strategy.select(List.of(old), null));
        // the instance goes away while its exchange is in flight
        for (int i = 1; i <= 10; i++) {
            strategy.report(strategy.select(List.of(instance(i), instance(i + 100)), null), LoadBalancer.Outcome.SUCCESS);
        }
        Assertions.assertEquals(1, strategy.inFlight(old));
        strategy.report(old, LoadBalancer.Outcome.SUCCESS);
        Assertions.assertEquals(0, strategy.inFlight(old));
        strategy.select(List.of(instance(1), instance(101)), null);
        Assertions.assertTrue(strategy.size() <= 3, "retained " + strategy.size());
    }
}
