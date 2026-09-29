package io.micronaut.http.client.loadbalance;

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@code backup} metadata of an instance only counts once a strategy is configured.
 */
class BackupInstanceTest {

    private static ServiceInstance instance(String name, boolean backup) {
        ServiceInstance.Builder builder = ServiceInstance.builder("svc", URI.create("http://" + name + ":8080"));
        if (backup) {
            builder.metadata(Map.of("backup", "true"));
        }
        return builder.build();
    }

    private static ServiceInstanceList list(List<ServiceInstance> instances) {
        return new ServiceInstanceList() {
            @Override
            public String getID() {
                return "svc";
            }

            @Override
            public List<ServiceInstance> getInstances() {
                return instances;
            }
        };
    }

    @Test
    void withoutAStrategyABackupInstanceIsSelectedInTurn() {
        ServiceInstanceListRoundRobinLoadBalancer loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(
            list(List.of(instance("a", false), instance("b", false), instance("backup", true))));
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            picks.add(Mono.from(loadBalancer.select(null)).block().getURI().getHost());
        }
        Assertions.assertEquals(List.of("a", "b", "backup", "a", "b", "backup"), picks);
    }

    @Test
    void withAStrategyABackupInstanceIsTheLastResort() {
        ServiceInstanceListRoundRobinLoadBalancer loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(
            list(List.of(instance("a", false), instance("b", false), instance("backup", true))));
        loadBalancer.setStrategy(LoadBalancerStrategy.of(LoadBalancerStrategy.ROUND_ROBIN));
        for (int i = 0; i < 6; i++) {
            Assertions.assertNotEquals("backup", Mono.from(loadBalancer.select(null)).block().getURI().getHost());
        }
    }
}
