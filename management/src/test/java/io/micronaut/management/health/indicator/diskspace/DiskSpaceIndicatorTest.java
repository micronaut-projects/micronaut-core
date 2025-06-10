package io.micronaut.management.health.indicator.diskspace;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.DefaultHealthAggregator;
import io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskSpaceIndicatorTest {

    @Test
    void diskSpaceHealthIndicatorViaConfiguration() {
        Consumer<ApplicationContext> healthBeansConsumer = context -> {
            assertTrue(context.containsBean(HealthEndpoint.class));
            assertTrue(context.containsBean(DefaultHealthAggregator.class));
        };
        Map<String, Object> configuration = Map.of("endpoints.health.disk-space.enabled", StringUtils.FALSE);
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            healthBeansConsumer.accept(context);
            assertFalse(context.containsBean(DiskSpaceIndicator.class));
        }
        // enabled by default
        try (ApplicationContext context = ApplicationContext.run()) {
            healthBeansConsumer.accept(context);
            assertTrue(context.containsBean(DiskSpaceIndicator.class));
        }
    }
}
