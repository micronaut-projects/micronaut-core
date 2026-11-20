package io.micronaut.docs.server.gateway

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.gateway.GatewayObserver
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GatewayDocsSpec extends Specification {

    // the application is its own upstream: the routes of Upstream below
    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        "spec.name": "GatewayDocsSpec",
        "micronaut.server.port": port,
        "orders.url": "http://localhost:" + port,
        "micronaut.gateway.routes.audited.uri": '${orders.url}',
        "micronaut.gateway.routes.audited.predicates": ["Path=/audited/**"],
        "micronaut.gateway.routes.audited.filters": ["StripPrefix=1", "Audit=billing"],
        "micronaut.gateway.routes.limited.uri": '${orders.url}',
        "micronaut.gateway.routes.limited.predicates": ["Path=/limited/**"],
        "micronaut.gateway.routes.limited.filters": ["StripPrefix=1", "RequestRateLimiter=1, 1, 1, tenant"],
        "micronaut.gateway.routes.guarded.uri": "http://127.0.0.1:1",
        "micronaut.gateway.routes.guarded.predicates": ["Path=/guarded/**"],
        "micronaut.gateway.routes.guarded.filters": ["CircuitBreaker=orders, ordersFallback"]])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the samples of the gateway guide"() {
        given:
        def http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.POST("/orders/1", '{"express":true}').contentType(MediaType.APPLICATION_JSON_TYPE)) == "express"
        http.retrieve(HttpRequest.POST("/orders/1", '{}').contentType(MediaType.APPLICATION_JSON_TYPE)) == "standard"
        http.retrieve(HttpRequest.GET("/tenants/acme/echo")) == "tenant=acme team=-"

        when:
        HttpResponse<String> audited = http.exchange(HttpRequest.GET("/audited/echo"), String)

        then:
        audited.body() == "tenant=- team=billing"
        audited.headers.get("X-Audited-Route") == "audited"
        http.exchange(HttpRequest.GET("/limited/echo").header("X-Tenant", "a"), String).code() == 200

        when:
        http.exchange(HttpRequest.GET("/limited/echo").header("X-Tenant", "a"), String)

        then:
        HttpClientResponseException tooMany = thrown()
        tooMany.code() == 429
        http.retrieve(HttpRequest.GET("/guarded/x")).startsWith("fallback: ")
        server.applicationContext.getBean(RouteMetrics).count("audited", GatewayObserver.Outcome.RESPONSE, 200) == 1
    }

    @Singleton
    @Requires(property = "spec.name", value = "GatewayDocsSpec")
    static class Upstream implements HttpRoutes {
        @Override
        void routes(HttpRouteBuilder routes) {
            routes.POST("/express/{+path}", { request, pathVariables -> HttpResponse.ok("express").contentType(MediaType.TEXT_PLAIN_TYPE) })
            routes.POST("/standard/{+path}", { request, pathVariables -> HttpResponse.ok("standard").contentType(MediaType.TEXT_PLAIN_TYPE) })
            routes.GET("/echo", { request, pathVariables ->
                HttpResponse.ok("tenant=" + request.headers.get("X-Tenant", String, "-")
                    + " team=" + request.headers.get("X-Audit-Team", String, "-")).contentType(MediaType.TEXT_PLAIN_TYPE)
            })
        }
    }
}
