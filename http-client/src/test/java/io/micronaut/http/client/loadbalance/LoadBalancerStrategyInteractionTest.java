package io.micronaut.http.client.loadbalance;

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.client.LoadBalancer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The strategies of a round-robin load balancer, with the outlier detection and with instances
 * that change at runtime.
 */
class LoadBalancerStrategyInteractionTest {

    private static final List<String> STRATEGIES = List.of(
        LoadBalancerStrategy.ROUND_ROBIN,
        LoadBalancerStrategy.RANDOM,
        LoadBalancerStrategy.POWER_OF_TWO_CHOICES,
        LoadBalancerStrategy.WEIGHTED,
        LoadBalancerStrategy.STICKY
    );

    private static ServiceInstance instance(String name) {
        return ServiceInstance.of("svc", URI.create("http://" + name + ":8080"));
    }

    private static String select(LoadBalancer loadBalancer, Object discriminator) {
        return Mono.from(loadBalancer.select(discriminator)).block().getURI().getHost();
    }

    private static OutlierDetectionConfiguration ejectAfterOneFailure() {
        OutlierDetectionConfiguration configuration = new OutlierDetectionConfiguration();
        configuration.setEnabled(true);
        configuration.setConsecutiveFailures(1);
        configuration.setBaseEjectionTime(Duration.ofMinutes(10));
        configuration.setMaxEjectionPercent(100);
        return configuration;
    }

    private static final class MutableList implements ServiceInstanceList {
        final List<ServiceInstance> instances = new CopyOnWriteArrayList<>();

        @Override
        public String getID() {
            return "svc";
        }

        @Override
        public List<ServiceInstance> getInstances() {
            return instances;
        }
    }

    @Test
    void anEjectedInstanceIsNeverPickedByAnyStrategy() {
        for (String name : STRATEGIES) {
            MutableList list = new MutableList();
            list.instances.addAll(List.of(instance("a"), instance("b"), instance("c")));
            ServiceInstanceListRoundRobinLoadBalancer loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(list, ejectAfterOneFailure());
            loadBalancer.setStrategy(LoadBalancerStrategy.of(name));
            // select once so that the detector knows the instances
            select(loadBalancer, "key-0");
            loadBalancer.report(instance("b"), LoadBalancer.Outcome.CONNECT_FAILURE);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                seen.add(select(loadBalancer, "key-" + i));
            }
            Assertions.assertFalse(seen.contains("b"), name + " picked the ejected instance: " + seen);
            if (!name.equals(LoadBalancerStrategy.STICKY)) {
                Assertions.assertEquals(Set.of("a", "c"), seen, name);
            }
        }
    }

    @Test
    void theStrategyFollowsTheInstancesAsTheyChange() {
        for (String name : STRATEGIES) {
            MutableList list = new MutableList();
            list.instances.addAll(List.of(instance("a"), instance("b")));
            ServiceInstanceListRoundRobinLoadBalancer loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(list);
            loadBalancer.setStrategy(LoadBalancerStrategy.of(name));
            for (int i = 0; i < 20; i++) {
                select(loadBalancer, "key-" + i);
            }
            // a refresh: b goes, c and d arrive
            list.instances.clear();
            list.instances.addAll(List.of(instance("a"), instance("c"), instance("d")));
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 300; i++) {
                seen.add(select(loadBalancer, "key-" + i));
            }
            Assertions.assertEquals(Set.of("a", "c", "d"), seen, name);
            // down to one instance
            list.instances.clear();
            list.instances.add(instance("d"));
            for (int i = 0; i < 10; i++) {
                Assertions.assertEquals("d", select(loadBalancer, "key-" + i), name);
            }
        }
    }

    @Test
    void withoutAStrategyRoundRobinAndTheOutlierDetectionAreUnchanged() {
        MutableList list = new MutableList();
        list.instances.addAll(List.of(instance("a"), instance("b"), instance("c")));
        ServiceInstanceListRoundRobinLoadBalancer loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(list, ejectAfterOneFailure());
        Assertions.assertNull(loadBalancer.getStrategy());
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            picks.add(select(loadBalancer, null));
        }
        Assertions.assertEquals(List.of("a", "b", "c", "a", "b", "c"), picks);
        loadBalancer.report(instance("b"), LoadBalancer.Outcome.CONNECT_FAILURE);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            seen.add(select(loadBalancer, null));
        }
        Assertions.assertEquals(Set.of("a", "c"), seen);
    }
}
