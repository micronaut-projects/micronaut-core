package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject

/**
 * One entry per service, which receives the configuration nested under it through its constructor.
 */
@EachProperty("services")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec.nested")
class ServiceConfiguration {
    final String name
    final Tls tls
    String url

    @Inject
    ServiceConfiguration(@Parameter String name, Tls tls) {
        this.name = name
        this.tls = tls
    }

    /**
     * Bound under the entry's own prefix, {@code services.<name>.tls}.
     */
    @ConfigurationProperties("tls")
    @Requires(property = "spec.name", value = "ConfigurationRefresherSpec.nested")
    static class Tls {
        boolean trustAll
    }
}
