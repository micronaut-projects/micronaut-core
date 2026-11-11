package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A retained bean made from configuration, which holds the prototype the configuration received.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ChainedClient {
    public final LookupHolder holder;

    ChainedClient(ChainedSettings settings) {
        this.holder = settings.holder();
    }
}
