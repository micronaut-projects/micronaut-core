package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires

/**
 * A configuration bound through its constructor that the server receives.
 */
@ConfigurationProperties("binding")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.server")
class ServerBinding {
    final String label

    @ConfigurationInject
    ServerBinding(String label) {
        this.label = label
    }
}
