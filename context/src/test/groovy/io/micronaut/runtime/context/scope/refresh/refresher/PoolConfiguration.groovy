package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires

@ConfigurationProperties("pool")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec")
class PoolConfiguration {
    String url = "default"
    int size = 1
}
