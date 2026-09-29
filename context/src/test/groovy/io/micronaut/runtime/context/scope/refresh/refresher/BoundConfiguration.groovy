package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires

/**
 * Bound through the constructor: a refresh replaces the instance.
 */
@ConfigurationProperties("bound")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec")
class BoundConfiguration {
    final String label

    @ConfigurationInject
    BoundConfiguration(String label) {
        this.label = label
    }
}
