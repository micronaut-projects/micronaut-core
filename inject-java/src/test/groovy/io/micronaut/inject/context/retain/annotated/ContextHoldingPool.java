package io.micronaut.inject.context.retain.annotated;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * Annotated, but holding the context: not safe to retain.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "RetainAnnotationSpec")
public class ContextHoldingPool {
    public final ApplicationContext context;

    public ContextHoldingPool(ApplicationContext context) {
        this.context = context;
    }
}
