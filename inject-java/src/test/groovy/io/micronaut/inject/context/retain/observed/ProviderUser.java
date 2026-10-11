package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A bean that holds a provider of configuration, which resolves through the context that made it.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class ProviderUser {
    public final BeanProvider<ClientSettings> settings;

    ProviderUser(BeanProvider<ClientSettings> settings) {
        this.settings = settings;
    }
}
