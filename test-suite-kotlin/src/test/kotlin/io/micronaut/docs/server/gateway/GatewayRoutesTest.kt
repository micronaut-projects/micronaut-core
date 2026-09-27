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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GatewayRoutesTest {

    @Test
    fun theGatewayRoutesRelayToTheUpstream() {
        val port = SocketUtils.findAvailableTcpPort()
        // the application is its own upstream: /orders/{id} below
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
            "spec.name" to "GatewayRoutesTest",
            "micronaut.server.port" to port,
            "orders.url" to "http://localhost:$port")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                assertEquals("order 7 mn", client.toBlocking().retrieve(HttpRequest.GET<Any>("/api/orders/7").header("X-Tenant", "t")))
                assertThrows(HttpClientResponseException::class.java) { client.toBlocking().retrieve(HttpRequest.GET<Any>("/api/orders/7")) }
                assertEquals("order 8 -", client.toBlocking().retrieve(HttpRequest.GET<Any>("/internal/orders/8")))
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "GatewayRoutesTest")
    class Upstream : HttpRoutes {
        override fun routes(routes: HttpRouteBuilder) {
            routes.GET("/orders/{id}") { request, pathVariables ->
                HttpResponse.ok("order " + pathVariables.getString("id") + " " + request.headers.get("X-Gateway", String::class.java, "-"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }
    }
}
