package io.micronaut.inject.context.retain.received;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * A prototype holding the context.
 */
@Prototype
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ContextHolder {
    public final BeanContext context;

    ContextHolder(BeanContext context) {
        this.context = context;
    }
}
