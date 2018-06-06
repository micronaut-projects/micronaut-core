package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micronaut.context.BeanContext;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;

/**
 * Initializes a meter registry prior to its use by applying customizations, meter filters and
 * non-JVM bindings.  JVM Bindings are handled in a separate customizer to support not binding
 * them to all meter registries.
 */
@Singleton
public class MeterRegistryInitializer implements BeanCreatedEventListener<MeterRegistry> {
    private static final Logger LOG = LoggerFactory.getLogger(MeterRegistryInitializer.class);

    /**
     *
     * @param event The bean created event.
     * @return The MeterRegistry
     */
    @Override
    public MeterRegistry onCreated(BeanCreatedEvent<MeterRegistry> event) {
        LOG.trace("MeterRegistryInitializer.onCreated");

        MeterRegistry meterRegistry = event.getBean();

        BeanContext ctx = event.getSource();

        addMeterCustomizations(ctx, meterRegistry);
        addMeterFilters(ctx, meterRegistry);
        applyMeterBinders(ctx, meterRegistry);
        addToCompositeRegistry(ctx, meterRegistry);

        return meterRegistry;
    }

    /**
     *
     * @param ctx The bean context
     * @param meterRegistry The registry
     */
    protected void applyMeterBinders(BeanContext ctx, MeterRegistry meterRegistry) {
        Collection<MeterBinder> meterBinders = ctx.getBeansOfType(MeterBinder.class);
        meterBinders.forEach(b -> b.bindTo(meterRegistry));
    }

    /**
     * Applies all MeterRegistryCustomizers applicable to this MeterRegistry.
     *
     * @param ctx           The BeanContext
     * @param meterRegistry The meter registry
     */
    protected void addMeterCustomizations(BeanContext ctx, MeterRegistry meterRegistry) {
        Collection<MeterRegistryCustomizer> customizers = ctx.getBeansOfType(MeterRegistryCustomizer.class);

        customizers.stream()
          .filter(c -> c.supports(meterRegistry))
          .forEach(c -> c.customize(meterRegistry));
    }

    /**
     *
     * @param ctx The bean context
     * @param meterRegistry The meter registry
     */
    protected void addMeterFilters(BeanContext ctx, MeterRegistry meterRegistry) {
        Collection<MeterFilter> meterFilters = ctx.getBeansOfType(MeterFilter.class);
        meterFilters.forEach(meterRegistry.config()::meterFilter);
    }

    /**
     *
     * @param ctx The bean context
     * @param meterRegistry The meter registry
     */
    protected void addToCompositeRegistry(BeanContext ctx, MeterRegistry meterRegistry) {
        if (CompositeMeterRegistry.class.isAssignableFrom(meterRegistry.getClass())) {
            return;
        }

        Optional<CompositeMeterRegistry> opt = ctx.findBean(CompositeMeterRegistry.class);
        if (opt.isPresent()) {
            CompositeMeterRegistry compositeRegistry = opt.get();
            if (compositeRegistry != meterRegistry) {
                compositeRegistry.add(meterRegistry);
            }
        }
    }
}
