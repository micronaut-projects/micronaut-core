package io.micronaut.management.health.indicator.discovery;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.CompositeDiscoveryClient;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.DefaultHealthAggregator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.diskspace.DiskSpaceIndicator;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryClientHealthIndicatorTest {

    @Test
    void disableDiscoveryClientHealthIndicatorViaConfiguration() {
        Consumer<ApplicationContext> healthBeansConsumer = context -> {
            assertTrue(context.containsBean(HealthEndpoint.class));
            assertTrue(context.containsBean(DefaultHealthAggregator.class));
        };
        Map<String, Object> configuration = Map.of("endpoints.health.discovery-client-health.enabled", StringUtils.FALSE);
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            healthBeansConsumer.accept(context);
            assertFalse(context.containsBean(DiscoveryClientHealthIndicator.class));
        }
        configuration = Map.of("endpoints.health.discovery-client-health.enabled", StringUtils.TRUE);
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            healthBeansConsumer.accept(context);
            assertTrue(context.containsBean(DiscoveryClientHealthIndicator.class));
        }
        // enabled by default
        try (ApplicationContext context = ApplicationContext.run()) {
            healthBeansConsumer.accept(context);
            assertTrue(context.containsBean(DiscoveryClientHealthIndicator.class));
        }
    }

    @Test
    void healthCheckFallsBackWhenCompositeDiscoveryClientDecoratorFails() {
        DiscoveryClient delegate = new DiscoveryClient() {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                return Flux.just(Collections.singletonList(ServiceInstance.of("service", URI.create("http://localhost"))));
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                return Flux.just(Collections.singletonList("service"));
            }

            @Override
            public String getDescription() {
                return "delegate";
            }

            @Override
            public void close() {
                // nothing to release: the delegate of this test holds no resource
            }
        };
        DiscoveryClient decorated = new CompositeDiscoveryClient(new DiscoveryClient[]{delegate}) {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                throw new ConfigurationException("No cache configured for name: discovery-client");
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                throw new ConfigurationException("No cache configured for name: discovery-client");
            }
        };

        HealthResult result = Mono.from(new DiscoveryClientHealthIndicator(decorated).getResult()).block();

        assertEquals(HealthStatus.UP, result.getStatus());
    }

    @Test
    void healthCheckReturnsHealthyWhenCompositeHasNoChildClients() {
        DiscoveryClient decorated = new CompositeDiscoveryClient(new DiscoveryClient[0]) {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                throw new ConfigurationException("No cache configured for name: discovery-client");
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                throw new ConfigurationException("No cache configured for name: discovery-client");
            }
        };

        HealthResult result = Mono.from(new DiscoveryClientHealthIndicator(decorated).getResult()).block();

        assertEquals(HealthStatus.UP, result.getStatus());
    }
}
