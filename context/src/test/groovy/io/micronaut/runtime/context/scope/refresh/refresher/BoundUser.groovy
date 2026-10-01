package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Holds the constructor-bound configuration: without a dependency graph, found by the type it injects.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec")
class BoundUser {
    final BoundConfiguration configuration

    BoundUser(BoundConfiguration configuration) {
        this.configuration = configuration
    }
}
