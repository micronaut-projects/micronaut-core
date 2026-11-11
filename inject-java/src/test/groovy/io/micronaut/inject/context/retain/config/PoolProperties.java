package io.micronaut.inject.context.retain.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * The configuration of the pool, under the prefix its retention names: an interface, whose implementation the
 * compiler generates and which reads the environment.
 */
@ConfigurationProperties("my.pool")
@Requires(property = "spec.name", value = "RetainedConfigurationSpec")
public interface PoolProperties {

    @Bindable(defaultValue = "1")
    int getSize();
}
