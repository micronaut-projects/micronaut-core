package io.micronaut.docs.server.gateway

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayFilters
import io.micronaut.http.gateway.GatewayRetry
import io.micronaut.http.gateway.GatewayRouteBuilder
import io.micronaut.http.gateway.GatewayRoutes
import io.micronaut.web.router.builder.RequestCondition
import jakarta.inject.Singleton
import java.time.Duration
// end::imports[]

@Requires(property = "spec.name", value = "GatewayRoutesTest")
// tag::clazz[]
@Singleton
class ApiGateway(@Value("\${orders.url}") private val orders: String) : GatewayRoutes { // <1>

    override fun routes(gateway: GatewayRouteBuilder) {
        gateway.route("orders")
            .path("/api/orders/**") // <2>
            .where(RequestCondition.header("X-Tenant")) // <3>
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Gateway", "mn")) // <4>
            .retry(GatewayRetry.of(2)) // <5>
            .responseTimeout(Duration.ofSeconds(5))
            .uri(orders)
    }
}
// end::clazz[]
