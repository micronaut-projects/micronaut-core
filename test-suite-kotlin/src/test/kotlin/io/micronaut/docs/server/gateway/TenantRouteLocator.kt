package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayFilters
import io.micronaut.http.gateway.GatewayRoute
import io.micronaut.http.gateway.GatewayRouteLocator
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
class TenantRouteLocator(@Value("\${orders.url}") private val upstream: String) : GatewayRouteLocator {

    override fun getRoutes(): CompletionStage<List<GatewayRoute>> {
        val routes = listOf("acme", "globex").map { tenant -> // <1>
            GatewayRoute.builder("tenant-$tenant")
                .path("/tenants/$tenant/**")
                .filters(GatewayFilters.stripPrefix(2), GatewayFilters.addRequestHeader("X-Tenant", tenant))
                .uri(upstream) // <2>
                .build()
        }
        return CompletableFuture.completedFuture(routes)
    }
}
// end::clazz[]
