package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.gateway.GatewayFilter
import io.micronaut.http.gateway.ProxyExchange
import io.micronaut.http.gateway.config.GatewayArguments
import io.micronaut.http.gateway.config.GatewayFilterFactory
import jakarta.inject.Named
import jakarta.inject.Singleton

// tag::clazz[]
class AuditFilter implements GatewayFilter {
    private final String team

    AuditFilter(String team) {
        this.team = team
    }

    @Override
    void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) { // <1>
        request.headers.set("X-Audit-Team", team)
    }

    @Override
    void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) { // <2>
        response.headers.set("X-Audited-Route", exchange.routeId())
    }
}

@Factory
@Requires(property = "spec.name", value = "GatewayDocsSpec")
class AuditFilterFactory {
    @Singleton
    @Named("Audit")
    GatewayFilterFactory audit() {
        GatewayFilterFactory.of(["team"], { GatewayArguments args -> new AuditFilter(args.get("team")) }) // <3>
    }
}
// end::clazz[]
