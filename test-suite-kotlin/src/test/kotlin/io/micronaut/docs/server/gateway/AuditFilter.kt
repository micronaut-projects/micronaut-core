package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.gateway.GatewayFilter
import io.micronaut.http.gateway.ProxyExchange
import io.micronaut.http.gateway.config.GatewayFilterFactory
import jakarta.inject.Named
import jakarta.inject.Singleton

// tag::clazz[]
class AuditFilter(private val team: String) : GatewayFilter {

    override fun filterRequest(exchange: ProxyExchange, request: MutableHttpRequest<*>) { // <1>
        request.headers.set("X-Audit-Team", team)
    }

    override fun filterResponse(exchange: ProxyExchange, response: MutableHttpResponse<*>) { // <2>
        response.headers.set("X-Audited-Route", exchange.routeId())
    }
}

@Factory
@Requires(property = "spec.name", value = "GatewayDocsTest")
class AuditFilterFactory {
    @Singleton
    @Named("Audit")
    fun audit(): GatewayFilterFactory =
        GatewayFilterFactory.of(listOf("team")) { args -> AuditFilter(args.get("team")) } // <3>
}
// end::clazz[]
