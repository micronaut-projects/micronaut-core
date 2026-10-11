package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A retained bean made from configuration, which holds the singleton the configuration received.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class HeldClient {
    public final ProviderHolder holder;

    HeldClient(HeldSettings settings) {
        this.holder = settings.holder();
    }
}
