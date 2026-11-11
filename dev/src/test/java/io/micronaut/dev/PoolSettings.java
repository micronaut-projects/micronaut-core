package io.micronaut.dev;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * A module's pool configuration, an interface whose generated implementation reads the environment.
 */
@ConfigurationProperties("my.settings")
@Requires(property = "spec.name", value = "RetainedConfigurationTest")
public interface PoolSettings {

    @Bindable(defaultValue = "0")
    int getSize();
}
