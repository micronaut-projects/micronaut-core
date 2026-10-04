package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Holds another entry of the {@code @EachProperty} configuration than the one removed.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.eachProperty")
class OtherEndpointUser {
    final EndpointConfiguration endpoint

    OtherEndpointUser(@Named("one") EndpointConfiguration endpoint) {
        this.endpoint = endpoint
    }
}
