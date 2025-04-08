package io.micronaut.management.health.indicator.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.DefaultHealthAggregator;
import io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicator;
import io.micronaut.management.health.indicator.diskspace.DiskSpaceIndicator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceHttpClientHealthIndicatorTest {

    @Test
    void serviceHttpClientHealthIndicatorViaConfiguration() {
        Consumer<ApplicationContext> healthBeansConsumer = context -> {
            assertTrue(context.containsBean(HealthEndpoint.class));
            assertTrue(context.containsBean(DefaultHealthAggregator.class));
        };
        Map<String, Object> configuration = Map.of("endpoints.health.service-http-client.enabled", StringUtils.FALSE);
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            healthBeansConsumer.accept(context);
            assertFalse(context.containsBean(ServiceHttpClientHealthIndicator.class));
        }
        // service http client health indicator is disabled by default
        try (ApplicationContext context = ApplicationContext.run()) {
            healthBeansConsumer.accept(context);
            assertFalse(context.containsBean(ServiceHttpClientHealthIndicator.class));
        }
    }
}
