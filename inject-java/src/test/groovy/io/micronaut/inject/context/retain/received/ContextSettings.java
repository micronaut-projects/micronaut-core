package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received a prototype holding the context.
 */
@ConfigurationProperties("received.context")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ContextSettings {
    private ContextHolder holder;

    public ContextHolder holder() {
        return holder;
    }

    @Inject
    void holder(ContextHolder holder) {
        this.holder = holder;
    }
}
