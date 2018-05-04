package io.micronaut.configuration.metrics.micrometer.graphite;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.COMPOSITE_METER_REGISTRY_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

@Factory
public class GraphiteMeterRegistryFactory {
    public static final String GRAPHITE_CONFIG = MeterRegistryFactory.CFG_ROOT + "export.graphite.";
    public static final String GRAPHITE_ENABLED = GRAPHITE_CONFIG + "enabled";

    private final GraphiteConfig graphiteConfig;

    GraphiteMeterRegistryFactory(final GraphiteConfiguration graphiteConfiguration) {
        this.graphiteConfig = new GraphiteConfig() {
            @Override
            public String get(String key) {
                String value;
                switch(key) {
                    case "graphite.enabled":
                        value = String.valueOf(graphiteConfiguration.enabled);
                        break;
                    case "graphite.host":
                        value = String.valueOf(graphiteConfiguration.host);
                        break;
                    case "graphite.step":
                        value = String.valueOf(graphiteConfiguration.step);
                        break;
                    default:
                        value = null;
                }
                return value;
            }
        };
    }

    /**
     * Create a GraphiteMeterRegistry bean if metrics are not disabled, the composite registry is enabled, and the graphite-meter-registry
     * is not disabled.
     *
     * @return A GraphiteMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = METRICS_ENABLED, value="true", defaultValue = "true")
    @Requires(property = COMPOSITE_METER_REGISTRY_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = GRAPHITE_ENABLED, value="true", defaultValue = "true")
    GraphiteMeterRegistry graphiteMeterRegistry(ApplicationContext ctx) {
        return new GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT);
    }
}
