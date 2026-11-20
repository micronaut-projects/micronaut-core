package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires

@EachProperty("listeners")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.server")
class ListenerConfiguration {
    final String name
    String url

    ListenerConfiguration(@Parameter String name) {
        this.name = name
    }
}
