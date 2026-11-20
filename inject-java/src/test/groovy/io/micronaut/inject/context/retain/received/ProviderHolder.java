package io.micronaut.inject.context.retain.received;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A singleton holding a provider, which resolves through the context that made it.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ProviderHolder {
    public final BeanProvider<SettingsListener> listener;

    ProviderHolder(BeanProvider<SettingsListener> listener) {
        this.listener = listener;
    }
}
