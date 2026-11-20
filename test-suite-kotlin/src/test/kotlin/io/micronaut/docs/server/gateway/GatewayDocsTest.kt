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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatewayDocsTest {

    @Test
    fun theSamplesOfTheGatewayGuide() {
        val port = SocketUtils.findAvailableTcpPort()
        val r = "micronaut.gateway.routes."
        // the application is its own upstream: the routes of Upstream below
        val properties = mapOf<String, Any>(
            "spec.name" to "GatewayDocsTest",
            "micronaut.server.port" to port,
            "orders.url" to "http://localhost:$port",
            r + "audited.uri" to "\${orders.url}",
            r + "audited.predicates" to listOf("Path=/audited/**"),
            r + "audited.filters" to listOf("StripPrefix=1", "Audit=billing"),
            r + "limited.uri" to "\${orders.url}",
            r + "limited.predicates" to listOf("Path=/limited/**"),
            r + "limited.filters" to listOf("StripPrefix=1", "RequestRateLimiter=1, 1, 1, tenant"),
            r + "guarded.uri" to "http://127.0.0.1:1",
            r + "guarded.predicates" to listOf("Path=/guarded/**"),
            r + "guarded.filters" to listOf("CircuitBreaker=orders, ordersFallback"))
        ApplicationContext.run(EmbeddedServer::class.java, properties).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                assertEquals("express", http.retrieve(HttpRequest.POST("/orders/1", "{\"express\":true}").contentType(MediaType.APPLICATION_JSON_TYPE)))
                assertEquals("standard", http.retrieve(HttpRequest.POST("/orders/1", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)))
                assertEquals("tenant=acme team=-", http.retrieve(HttpRequest.GET<Any>("/tenants/acme/echo")))
                val audited = http.exchange(HttpRequest.GET<Any>("/audited/echo"), String::class.java)
                assertEquals("tenant=- team=billing", audited.body())
                assertEquals("audited", audited.headers.get("X-Audited-Route"))
                assertEquals(200, http.exchange(HttpRequest.GET<Any>("/limited/echo").header("X-Tenant", "a"), String::class.java).code())
                val tooMany = assertThrows(HttpClientResponseException::class.java) {
                    http.exchange(HttpRequest.GET<Any>("/limited/echo").header("X-Tenant", "a"), String::class.java)
                }
                assertEquals(429, tooMany.code())
                assertTrue(http.retrieve(HttpRequest.GET<Any>("/guarded/x")).startsWith("fallback: "))
                val metrics = server.applicationContext.getBean(RouteMetrics::class.java)
                assertEquals(1, metrics.count("audited", GatewayObserver.Outcome.RESPONSE, 200))
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "GatewayDocsTest")
    class Upstream : HttpRoutes {
        override fun routes(routes: HttpRouteBuilder) {
            routes.POST("/express/{+path}") { _, _ -> HttpResponse.ok("express").contentType(MediaType.TEXT_PLAIN_TYPE) }
            routes.POST("/standard/{+path}") { _, _ -> HttpResponse.ok("standard").contentType(MediaType.TEXT_PLAIN_TYPE) }
            routes.GET("/echo") { request, _ ->
                HttpResponse.ok("tenant=" + request.headers.get("X-Tenant", String::class.java, "-")
                    + " team=" + request.headers.get("X-Audit-Team", String::class.java, "-")).contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }
    }
}
