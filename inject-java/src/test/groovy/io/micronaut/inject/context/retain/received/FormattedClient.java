package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A retained bean made from configuration, which holds the prototype bound to nothing that the configuration received.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class FormattedClient {
    public final Formatter formatter;

    FormattedClient(FormattedSettings settings) {
        this.formatter = settings.formatter();
    }
}
