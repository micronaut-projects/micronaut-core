package io.micronaut.docs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;

public class SimpleMeterRegistryConfigurer implements MeterRegistryConfigurer {

    @Override
    public void configure(MeterRegistry meterRegistry) {
        meterRegistry.config().commonTags("key", "value");
    }

    @Override
    public boolean supports(MeterRegistry meterRegistry) {
        return meterRegistry instanceof SimpleMeterRegistry;
    }
}
