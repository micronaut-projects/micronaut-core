package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built-in load balancer strategies, and their selection per service.
 */
class LoadBalancerStrategyTest {

    private static ServiceInstance instance(String name) {
        return ServiceInstance.of("svc", URI.create("http://" + name + ":8080"));
    }

    private static ServiceInstance weighted(String name, int weight) {
        return ServiceInstance.builder("svc", URI.create("http://" + name + ":8080")).metadata(Map.of("weight", String.valueOf(weight))).build();
    }

    private static String host(ServiceInstance instance) {
        return instance.getURI().getHost();
    }

    @Test
    void roundRobinTakesEachInstanceInTurn() {
        LoadBalancerStrategy strategy = LoadBalancerStrategy.of(LoadBalancerStrategy.ROUND_ROBIN);
        List<ServiceInstance> instances = List.of(instance("a"), instance("b"), instance("c"));
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            picks.add(host(strategy.select(instances, null)));
        }
        Assertions.assertEquals(List.of("a", "b", "c", "a", "b", "c"), picks);
    }

    @Test
    void randomPicksEveryInstanceSometimes() {
        LoadBalancerStrategy strategy = LoadBalancerStrategy.of(LoadBalancerStrategy.RANDOM);
        List<ServiceInstance> instances = List.of(instance("a"), instance("b"), instance("c"));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            seen.add(host(strategy.select(instances, null)));
        }
        Assertions.assertEquals(Set.of("a", "b", "c"), seen);
    }

    @Test
    void powerOfTwoChoicesPrefersTheInstanceWithFewerExchangesInFlight() {
        LoadBalancerStrategy strategy = LoadBalancerStrategy.of(LoadBalancerStrategy.POWER_OF_TWO_CHOICES);
        List<ServiceInstance> instances = List.of(instance("a"), instance("b"));
        Map<String, Integer> counts = new HashMap<>();
        // nothing completes: the two instances stay balanced
        for (int i = 0; i < 100; i++) {
            counts.merge(host(strategy.select(instances, null)), 1, Integer::sum);
        }
        Assertions.assertTrue(Math.abs(counts.get("a") - counts.get("b")) <= 1, counts.toString());
        // the exchanges of a complete: a is preferred until it has as many in flight as b
        for (int i = 0; i < 50; i++) {
            strategy.report(instances.get(0), LoadBalancer.Outcome.SUCCESS);
        }
        for (int i = 0; i < 10; i++) {
            Assertions.assertEquals("a", host(strategy.select(instances, null)));
        }
    }

    @Test
    void weightedFollowsTheWeightsSmoothly() {
        LoadBalancerStrategy strategy = LoadBalancerStrategy.of(LoadBalancerStrategy.WEIGHTED);
        List<ServiceInstance> instances = List.of(weighted("a", 5), weighted("b", 1), weighted("c", 1));
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            picks.add(host(strategy.select(instances, null)));
        }
        // the sequence of nginx's smooth weighted round robin
        Assertions.assertEquals(List.of("a", "a", "b", "a", "c", "a", "a"), picks);
    }

    @Test
    void weightedExcludesAZeroWeightUnlessAllAreZero() {
        LoadBalancerStrategy strategy = LoadBalancerStrategy.of(LoadBalancerStrategy.WEIGHTED);
        List<ServiceInstance> instances = List.of(weighted("a", 0), weighted("b", 2));
        for (int i = 0; i < 10; i++) {
            Assertions.assertEquals("b", host(strategy.select(instances, null)));
        }
        List<ServiceInstance> allZero = List.of(weighted("a", 0), weighted("b", 0));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            seen.add(host(strategy.select(allZero, null)));
        }
        Assertions.assertEquals(Set.of("a", "b"), seen);
        // no weight is a weight of 1
        Assertions.assertNotNull(strategy.select(List.of(instance("x")), null));
    }

    @Test
    void aBackupInstanceIsSelectedOnlyWhenNoOtherIsLeft() {
        List<ServiceInstance> instances = new ArrayList<>(List.of(instance("a"), instance("b"),
            ServiceInstance.builder("svc", URI.create("http://backup:8080")).metadata(Map.of("backup", "true")).build()));
        ServiceInstanceListRoundRobinLoadBalancer balancer = new ServiceInstanceListRoundRobinLoadBalancer(new io.micronaut.discovery.ServiceInstanceList() {
            @Override
            public String getID() {
                return "svc";
            }

            @Override
            public List<ServiceInstance> getInstances() {
                return instances;
            }
        });
        for (int i = 0; i < 6; i++) {
            Assertions.assertNotEquals("backup", Mono.from(balancer.select()).block().getURI().getHost());
        }
        // a and b were tried
        ExcludedInstances tried = new ExcludedInstances(Set.of(URI.create("http://a:8080"), URI.create("http://b:8080")), null);
        Assertions.assertEquals("backup", Mono.from(balancer.select(tried)).block().getURI().getHost());
        // only a was tried
        ExcludedInstances triedA = new ExcludedInstances(Set.of(URI.create("http://a:8080")), null);
        for (int i = 0; i < 4; i++) {
            Assertions.assertEquals("b", Mono.from(balancer.select(triedA)).block().getURI().getHost());
        }
        // every instance was tried: one of the primaries again
        ExcludedInstances all = new ExcludedInstances(Set.of(URI.create("http://a:8080"), URI.create("http://b:8080"), URI.create("http://backup:8080")), null);
        Assertions.assertNotEquals("backup", Mono.from(balancer.select(all)).block().getURI().getHost());
        // the primaries go down
        instances.set(0, ServiceInstance.builder("svc", URI.create("http://a:8080")).status(io.micronaut.health.HealthStatus.DOWN).build());
        instances.set(1, ServiceInstance.builder("svc", URI.create("http://b:8080")).status(io.micronaut.health.HealthStatus.DOWN).build());
        Assertions.assertEquals("backup", Mono.from(balancer.select()).block().getURI().getHost());
    }

    @Test
    void stickyKeepsADiscriminatorOnItsInstance() {
        LoadBalancerStrategy strategy = LoadBalancerStrategy.of(LoadBalancerStrategy.STICKY);
        List<ServiceInstance> instances = List.of(instance("a"), instance("b"), instance("c"));
        Map<String, String> assigned = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String key = "session-" + i;
            String host = host(strategy.select(instances, key));
            assigned.put(key, host);
            used.add(host);
            Assertions.assertEquals(host, host(strategy.select(instances, key)));
        }
        Assertions.assertEquals(Set.of("a", "b", "c"), used, "the keys spread over the instances");
        // c goes away: only its keys move
        List<ServiceInstance> withoutC = List.of(instance("a"), instance("b"));
        for (Map.Entry<String, String> e : assigned.entrySet()) {
            String host = host(strategy.select(withoutC, e.getKey()));
            if (!e.getValue().equals("c")) {
                Assertions.assertEquals(e.getValue(), host, e.getKey());
            }
        }
        // without a discriminator: round robin
        Assertions.assertEquals(List.of("a", "b"), List.of(host(strategy.select(withoutC, null)), host(strategy.select(withoutC, null))));
    }

    @Test
    void anUnknownStrategyIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> LoadBalancerStrategy.of("nope"));
    }

    @Test
    void theStrategyIsSelectedPerService() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "LoadBalancerStrategyTest",
            "micronaut.http.services.sticky.urls", List.of("http://a:1", "http://b:1", "http://c:1"),
            "micronaut.http.services.sticky.load-balancer-strategy", "sticky",
            "micronaut.http.services.custom.urls", List.of("http://a:1", "http://b:1"),
            "micronaut.http.services.custom.load-balancer-strategy", "last",
            "micronaut.http.services.plain.urls", List.of("http://a:1", "http://b:1")))) {
            LoadBalancerResolver resolver = ctx.getBean(LoadBalancerResolver.class);
            LoadBalancer sticky = resolver.resolve("sticky").orElseThrow();
            String first = Mono.from(sticky.select("user-1")).block().getURI().getHost();
            for (int i = 0; i < 5; i++) {
                Assertions.assertEquals(first, Mono.from(sticky.select("user-1")).block().getURI().getHost());
            }
            LoadBalancer custom = resolver.resolve("custom").orElseThrow();
            for (int i = 0; i < 3; i++) {
                Assertions.assertEquals("b", Mono.from(custom.select()).block().getURI().getHost());
            }
            LoadBalancer plain = resolver.resolve("plain").orElseThrow();
            Assertions.assertNull(((AbstractRoundRobinLoadBalancer) plain).getStrategy());
            Assertions.assertNotEquals(Mono.from(plain.select()).block().getURI().getHost(), Mono.from(plain.select()).block().getURI().getHost());
        }
    }

    @Prototype
    @Named("last")
    @Requires(property = "spec.name", value = "LoadBalancerStrategyTest")
    static class LastStrategy implements LoadBalancerStrategy {
        @Override
        public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            return available.get(available.size() - 1);
        }
    }
}
