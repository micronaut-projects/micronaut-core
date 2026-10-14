package io.micronaut.inject.context.retain.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration under a prefix that no retention names.
 */
@ConfigurationProperties("other.pool")
@Requires(property = "spec.name", value = "RetainedConfigurationSpec")
public interface OtherProperties {

    @Bindable(defaultValue = "1")
    int getSize();
}
