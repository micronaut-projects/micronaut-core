package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayRouteBuilder
import io.micronaut.http.gateway.GatewayRoutes
import io.micronaut.web.router.builder.RequestCondition
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
class BodyRoutes(@Value("\${orders.url}") private val upstream: String) : GatewayRoutes {

    override fun routes(gateway: GatewayRouteBuilder) {
        gateway.route("express-orders").path("/orders/**").order(1)
            .where(RequestCondition.bodyText { body -> body.contains("express") })
            .uri("$upstream/express")
        gateway.route("orders").path("/orders/**").order(2)
            .uri("$upstream/standard")
    }
}
// end::clazz[]
