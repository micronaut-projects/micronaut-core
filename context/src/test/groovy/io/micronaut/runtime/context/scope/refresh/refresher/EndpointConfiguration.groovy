package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires

/**
 * One entry per endpoint: an entry whose keys all go is removed.
 */
@EachProperty("endpoints")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.eachProperty")
class EndpointConfiguration {
    final String name
    String url

    EndpointConfiguration(@Parameter String name) {
        this.name = name
    }
}
