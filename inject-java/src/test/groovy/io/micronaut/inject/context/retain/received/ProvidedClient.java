package io.micronaut.inject.context.retain.received;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A retained bean made from configuration, which holds the provider the configuration received.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ProvidedClient {
    public final BeanProvider<SettingsListener> listener;

    ProvidedClient(ProvidedSettings settings) {
        this.listener = settings.listener();
    }
}
