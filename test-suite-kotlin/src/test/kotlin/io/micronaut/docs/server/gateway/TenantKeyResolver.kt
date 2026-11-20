package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.gateway.KeyResolver
import jakarta.inject.Named
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
@Named("tenant")
class TenantKeyResolver : KeyResolver {

    // synchronous: read the request, no IO
    override fun resolve(request: HttpRequest<*>): String? = request.headers.get("X-Tenant")
}
// end::clazz[]
