package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.gateway.GatewayRouteBuilder
import io.micronaut.http.gateway.GatewayRoutes
import io.micronaut.web.router.builder.RequestCondition
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "GatewayDocsSpec")
// tag::clazz[]
@Singleton
class BodyRoutes implements GatewayRoutes {
    private final String upstream

    BodyRoutes(@Value('${orders.url}') String upstream) {
        this.upstream = upstream
    }

    @Override
    void routes(GatewayRouteBuilder gateway) {
        gateway.route("express-orders").path("/orders/**").order(1)
            .where(RequestCondition.bodyText({ String body -> body.contains("express") }))
            .uri(upstream + "/express")
        gateway.route("orders").path("/orders/**").order(2)
            .uri(upstream + "/standard")
    }
}
// end::clazz[]
