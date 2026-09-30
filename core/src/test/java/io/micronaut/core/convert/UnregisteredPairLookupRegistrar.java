package io.micronaut.core.convert;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only registrar that looks up a pair with no registered converter while it registers.
 * The lookup goes through the type hierarchy cache of {@link DefaultMutableConversionService},
 * so it fails if that class is not fully initialized when {@link ConversionService#SHARED} is built.
 * It is registered only in the isolated class loaders of {@code ConversionServiceInitializationSpec}.
 */
public class UnregisteredPairLookupRegistrar implements TypeConverterRegistrar {

    /**
     * The number of conversion services this registrar was applied to in its class loader.
     */
    public static final AtomicInteger REGISTRATIONS = new AtomicInteger();

    @Override
    public void register(MutableConversionService conversionService) {
        if (conversionService.canConvert(Thread.class, Runnable.class)) {
            throw new IllegalStateException("Thread -> Runnable is not expected to be convertible");
        }
        REGISTRATIONS.incrementAndGet();
    }
}
