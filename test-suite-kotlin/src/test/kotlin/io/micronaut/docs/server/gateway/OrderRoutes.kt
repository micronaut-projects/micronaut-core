package io.micronaut.docs.server.gateway

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayFilters
import io.micronaut.http.gateway.GatewayRoute
import io.micronaut.http.gateway.ProxyHandlerFactory
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "GatewayRoutesTest")
// tag::clazz[]
@Singleton
class OrderRoutes(private val proxies: ProxyHandlerFactory,
                  @Value("\${orders.url}") private val orders: String) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        val handler = proxies.create(GatewayRoute.builder("internal-orders") // <1>
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
