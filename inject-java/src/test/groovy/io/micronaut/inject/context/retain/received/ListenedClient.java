package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A retained bean made from configuration, which holds the listener the configuration received.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ListenedClient {
    public final int timeout;
    public final SettingsListener listener;

    ListenedClient(ListenedSettings settings) {
        this.timeout = settings.getTimeout();
        this.listener = settings.listener();
    }
}
