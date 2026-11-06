package io.micronaut.inject.context.retain.received;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * A prototype holding a provider, which resolves through the context that made it.
 */
@Prototype
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class Lookup {
    public final BeanProvider<SettingsListener> listener;

    Lookup(BeanProvider<SettingsListener> listener) {
        this.listener = listener;
    }
}
