package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Holds every entry of an {@code @EachProperty} configuration.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.eachProperty")
class EndpointsUser {
    final List<EndpointConfiguration> endpoints

    EndpointsUser(List<EndpointConfiguration> endpoints) {
        this.endpoints = endpoints
    }
}
