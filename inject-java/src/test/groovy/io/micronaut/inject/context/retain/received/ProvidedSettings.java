package io.micronaut.inject.context.retain.received;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received a provider, which resolves through the context that made it.
 */
@ConfigurationProperties("received.provided")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ProvidedSettings {
    private BeanProvider<SettingsListener> listener;

    public BeanProvider<SettingsListener> listener() {
        return listener;
    }

    @Inject
    void listener(BeanProvider<SettingsListener> listener) {
        this.listener = listener;
    }
}
