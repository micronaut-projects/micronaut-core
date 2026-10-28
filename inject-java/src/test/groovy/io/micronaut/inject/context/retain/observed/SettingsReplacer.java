package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

/**
 * Replaces the configuration of the client factory with a subclass, as a listener of an application may.
 */
@Singleton
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
@Requires(property = "observed.replace-settings", value = "true")
public class SettingsReplacer implements BeanCreatedEventListener<ClientSettings> {

    @Override
    public ClientSettings onCreated(BeanCreatedEvent<ClientSettings> event) {
        ReplacedSettings replaced = new ReplacedSettings();
        replaced.setTimeout(event.getBean().getTimeout());
        return replaced;
    }

    /**
     * The subclass the listener returns.
     */
    public static final class ReplacedSettings extends ClientSettings {
    }
}
