package io.micronaut.inject.context.retain.annotated;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * Produces a retained pool from an annotated factory method, and a product that is not annotated.
 */
@Factory
@Requires(property = "spec.name", value = "RetainAnnotationSpec")
public class PoolProducer {

    @Singleton
    @Retain(invalidatedBy = "my.pool")
    public ProducedPool pool() {
        return new ProducedPool("main");
    }

    @Singleton
    public OtherProduct other() {
        return new OtherProduct();
    }
}
