package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;

/**
 * Bean used to configure meter registries.  This bean is required to compose any sub type
 * of registries that are created, like statsd, graphite, etc.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Singleton
public class MeterRegistryCreationListener implements BeanCreatedEventListener<MeterRegistry> {

    @Override
    public MeterRegistry onCreated(BeanCreatedEvent<MeterRegistry> event) {
        MeterRegistry meterRegistry = event.getBean();
        BeanContext ctx = event.getSource();
        if (!(meterRegistry instanceof CompositeMeterRegistry)) {
            ctx.streamOfType(MeterRegistryConfigurer.class).forEach(meterRegistryConfigurer -> {
                if (meterRegistryConfigurer.supports(meterRegistry)) {
                    meterRegistryConfigurer.configure(meterRegistry);
                }
            });

            ctx.findBean(CompositeMeterRegistry.class)
                    .ifPresent(compositeMeterRegistry -> compositeMeterRegistry.add(meterRegistry));
        }

        return meterRegistry;
    }
}
