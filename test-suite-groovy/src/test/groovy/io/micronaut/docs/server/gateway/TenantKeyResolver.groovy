package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.gateway.KeyResolver
import jakarta.inject.Named
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "GatewayDocsSpec")
// tag::clazz[]
@Singleton
@Named("tenant")
class TenantKeyResolver implements KeyResolver {

    @Override
    String resolve(HttpRequest<?> request) {
        // synchronous: read the request, no IO
        request.headers.get("X-Tenant")
    }
}
// end::clazz[]
