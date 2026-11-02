package io.micronaut.context.nullfactory

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger

@Factory
@Requires(property = "spec.name", value = "NullSingletonShutdownSpec")
class NullProductFactory {

    @Singleton
    @Named("first")
    @Nullable
    Product first(Resource resource) {
        return null
    }

    @Singleton
    @Named("second")
    @Nullable
    Product second(Resource resource) {
        return null
    }

    static class Product {
    }

    /**
     * What a factory method resolves to produce its bean, released with the bean's registration.
     */
    @Prototype
    @Requires(property = "spec.name", value = "NullSingletonShutdownSpec")
    static class Resource {
        static final AtomicInteger RELEASED = new AtomicInteger()

        @PreDestroy
        void release() {
            RELEASED.incrementAndGet()
        }
    }
}
