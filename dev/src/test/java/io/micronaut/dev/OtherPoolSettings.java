package io.micronaut.dev;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration under a prefix the pools' retention does not name.
 */
@ConfigurationProperties("other.settings")
@Requires(property = "spec.name", value = "RetainedConfigurationTest")
public interface OtherPoolSettings {

    @Bindable(defaultValue = "0")
    int getSize();
}
