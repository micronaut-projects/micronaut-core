package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received other configuration, which is examined in turn.
 */
@ConfigurationProperties("received.nested")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class NestedSettings {
    private PlainSettings plain;

    public PlainSettings plain() {
        return plain;
    }

    @Inject
    void plain(PlainSettings plain) {
        this.plain = plain;
    }
}
