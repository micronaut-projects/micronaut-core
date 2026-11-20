package io.micronaut.docs.server.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.gateway.GatewayFilters;
import io.micronaut.http.gateway.GatewayRoute;
import io.micronaut.http.gateway.GatewayRouteLocator;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
public class TenantRouteLocator implements GatewayRouteLocator {
    private final String upstream;

    public TenantRouteLocator(@Value("${orders.url}") String upstream) {
        this.upstream = upstream;
    }

    @Override
    public CompletionStage<List<GatewayRoute>> getRoutes() {
        List<GatewayRoute> routes = new ArrayList<>();
        for (String tenant : List.of("acme", "globex")) { // <1>
            routes.add(GatewayRoute.builder("tenant-" + tenant)
                .path("/tenants/" + tenant + "/**")
                .filters(GatewayFilters.stripPrefix(2), GatewayFilters.addRequestHeader("X-Tenant", tenant))
                .uri(upstream) // <2>
                .build());
        }
        return CompletableFuture.completedFuture(routes);
    }
}
// end::clazz[]
