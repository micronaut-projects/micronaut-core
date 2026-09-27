package io.micronaut.docs.server.gateway

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayFilters
import io.micronaut.http.gateway.GatewayRoute
import io.micronaut.http.gateway.ProxyHandler
import io.micronaut.http.gateway.ProxyHandlerFactory
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "GatewayRoutesSpec")
// tag::clazz[]
@Singleton
class OrderRoutes implements HttpRoutes {
    private final ProxyHandlerFactory proxies
    private final String orders

    OrderRoutes(ProxyHandlerFactory proxies, @Value('${orders.url}') String orders) {
        this.proxies = proxies
        this.orders = orders
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        ProxyHandler handler = proxies.create(GatewayRoute.builder("internal-orders") // <1>
            .uri(orders)
            .filters(GatewayFilters.stripPrefix(1))
            .build())
        routes.path("/internal") { group -> // <2>
            group.any("").consumesAll().keepErrorResponses().handleAsync(handler)
            group.any("/{+path}").consumesAll().keepErrorResponses().handleAsync(handler)
        }
    }
}
// end::clazz[]
