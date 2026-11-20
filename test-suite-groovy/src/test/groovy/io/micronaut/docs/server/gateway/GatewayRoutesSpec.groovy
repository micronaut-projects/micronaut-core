package io.micronaut.docs.server.gateway

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GatewayRoutesSpec extends Specification {

    // the application is its own upstream: /orders/{id} below
    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        "spec.name": "GatewayRoutesSpec",
        "micronaut.server.port": port,
        "orders.url": "http://localhost:" + port])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the gateway routes relay to the upstream"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/api/orders/7").header("X-Tenant", "t")) == "order 7 mn"
        client.toBlocking().retrieve(HttpRequest.GET("/internal/orders/8")) == "order 8 -"

        when: 'the condition of the route does not match'
        client.toBlocking().retrieve(HttpRequest.GET("/api/orders/7"))

        then:
        thrown(HttpClientResponseException)
    }

    @Singleton
    @Requires(property = "spec.name", value = "GatewayRoutesSpec")
    static class Upstream implements HttpRoutes {
        @Override
        void routes(HttpRouteBuilder routes) {
            routes.GET("/orders/{id}", { request, pathVariables ->
                HttpResponse.ok("order " + pathVariables.getString("id") + " " + request.headers.get("X-Gateway", String, "-"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
            })
        }
    }
}
