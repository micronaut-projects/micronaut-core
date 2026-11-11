package io.micronaut.inject.context.retain.annotated;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * An annotated factory: the factory is retained, not what it produces.
 */
@Factory
@Retain
@Requires(property = "spec.name", value = "RetainAnnotationSpec")
public class RetainedFactory {

    @Singleton
    public FactoryProduct product() {
        return new FactoryProduct();
    }
}
