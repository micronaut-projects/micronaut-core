package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayFilters
import io.micronaut.http.gateway.GatewayRoute
import io.micronaut.http.gateway.GatewayRouteLocator
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@Requires(property = "spec.name", value = "GatewayDocsSpec")
// tag::clazz[]
@Singleton
class TenantRouteLocator implements GatewayRouteLocator {
    private final String upstream

    TenantRouteLocator(@Value('${orders.url}') String upstream) {
        this.upstream = upstream
    }

    @Override
    CompletionStage<List<GatewayRoute>> getRoutes() {
        List<GatewayRoute> routes = ["acme", "globex"].collect { String tenant -> // <1>
            GatewayRoute.builder("tenant-" + tenant)
                .path("/tenants/" + tenant + "/**")
                .filters(GatewayFilters.stripPrefix(2), GatewayFilters.addRequestHeader("X-Tenant", tenant))
                .uri(upstream) // <2>
                .build()
        }
        return CompletableFuture.completedFuture(routes)
    }
}
// end::clazz[]
