package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED;

/**
 * Factory for a Graphite Meter Registry.
 */
@Factory
public class GraphiteMeterRegistryFactory {
    public static final String GRAPHITE_CONFIG = "metrics.export.graphite";
    public static final String GRAPHITE_ENABLED = GRAPHITE_CONFIG + ".enabled";

    private static final Logger LOG = LoggerFactory.getLogger(GraphiteMeterRegistryFactory.class);

    private final GraphiteConfig graphiteConfig;

    /**
     * Build the GraphiteConfig from the GraphiteConfigurationProperties / GraphiteConfig.
     *
     * @param graphiteConfigurationProperties Graphite properties specified in application config
     */
    GraphiteMeterRegistryFactory(final GraphiteConfigurationProperties graphiteConfigurationProperties) {
        this.graphiteConfig = new GraphiteConfigAdapter(graphiteConfigurationProperties);
    }

    /**
     * Create a GraphiteMeterRegistry bean if metrics are not disabled, the composite registry is enabled, and the graphite-meter-registry
     * is not disabled.
     *
     * @param ctx The app context
     * @return A GraphiteMeterRegistry
     */
    @Bean
    @Primary
    @Context
    @Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = GRAPHITE_ENABLED, value = "true", defaultValue = "true")
    @Requires(classes = GraphiteMeterRegistry.class)
    @SuppressWarnings("unused")
    GraphiteMeterRegistry graphiteMeterRegistry(ApplicationContext ctx) {
        LOG.debug("Adding GraphiteMeterRegistry bean");
        return new GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT);
    }
}
