package io.micronaut.management.health.indicator.service;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.aggregator.DefaultHealthAggregator;
import io.micronaut.management.health.indicator.jdbc.JdbcIndicator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceReadyIndicatorTest {

    @Test
    void serviceReadyHealthIndicatorViaConfiguration() {
        Consumer<ApplicationContext> healthBeansConsumer = context -> {
            assertTrue(context.containsBean(HealthEndpoint.class));
            assertTrue(context.containsBean(DefaultHealthAggregator.class));
        };
        Map<String, Object> configuration = Map.of("endpoints.health.service-ready-indicator-enabled", StringUtils.FALSE);
        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            healthBeansConsumer.accept(context);
            assertFalse(context.containsBean(ServiceReadyHealthIndicator.class));
        }
        // enabled by default
        try (ApplicationContext context = ApplicationContext.run()) {
            healthBeansConsumer.accept(context);
            assertTrue(context.containsBean(ServiceReadyHealthIndicator.class));
        }
    }
}
