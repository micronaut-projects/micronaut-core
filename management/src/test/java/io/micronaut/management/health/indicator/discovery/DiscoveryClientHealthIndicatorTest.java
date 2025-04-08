package io.micronaut.management.health.indicator.discovery;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.DefaultHealthAggregator;
import io.micronaut.management.health.indicator.diskspace.DiskSpaceIndicator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

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
}
