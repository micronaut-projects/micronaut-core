package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Holds one named entry of an {@code @EachProperty} configuration.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.eachProperty")
class EndpointUser {
    final EndpointConfiguration endpoint

    EndpointUser(@Named("two") EndpointConfiguration endpoint) {
        this.endpoint = endpoint
    }
}
