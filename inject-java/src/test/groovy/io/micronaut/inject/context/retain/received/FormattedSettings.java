package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received a prototype bound to nothing.
 */
@ConfigurationProperties("received.formatted")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class FormattedSettings {
    private Formatter formatter;

    public Formatter formatter() {
        return formatter;
    }

    @Inject
    void formatter(Formatter formatter) {
        this.formatter = formatter;
    }
}
