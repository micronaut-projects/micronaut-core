package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Meter registry customizations defined by implementers.
 * Customizers are called on meter registry bean creation.
 */
public interface MeterRegistryCustomizer {

    /**
     * Apply customizations to the specified {@code registry} of the specified type T.
     *
     * @see <a href="http://micrometer.io/docs/registry/graphite">http://micrometer.io/docs/registry/graphite</a>
     * for an example of how to use a meter registry customizer.
     *
     * @param registry the meter registry to customize
     */
    void customize(MeterRegistry registry);

    /**
     * Determines if the specified registry should be customized by this customizer.
     * @param registry The registry to check
     * @return true if this customer applies to the registry; false otherwise
     */
    boolean supports(MeterRegistry registry);
}
