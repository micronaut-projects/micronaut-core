package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class ConditionRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient
        private var managementPort = 0

        @JvmStatic
        @BeforeAll
        fun start() {
            managementPort = SocketUtils.findAvailableTcpPort()
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
                "spec.name" to "ConditionRoutesTest",
                "management.port" to managementPort))
            client = server.applicationContext.createBean(HttpClient::class.java, server.url)
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun aConditionSelectsTheRoute() {
        val http = client.toBlocking()
        assertEquals("search", http.retrieve(HttpRequest.GET<Any>("/search")))
        assertEquals("beta search", http.retrieve(HttpRequest.GET<Any>("/search").header("X-Beta", "on")))
        assertEquals("beta search", http.retrieve(HttpRequest.GET<Any>("/search?beta=true")))
    }

    @Test
    fun aConstraintOnThePathVariables() {
        val http = client.toBlocking()
        assertEquals("stock of north", http.retrieve(HttpRequest.GET<Any>("/shops/north/stock")))
        val notFound = assertThrows<HttpClientResponseException> { http.retrieve(HttpRequest.GET<Any>("/shops/west/stock")) }
        assertEquals(HttpStatus.NOT_FOUND, notFound.status)
        assertEquals("item 5", http.retrieve(HttpRequest.GET<Any>("/items/5")))
        assertEquals("item named lamp", http.retrieve(HttpRequest.GET<Any>("/items/lamp")))
        assertEquals("item named -1", http.retrieve(HttpRequest.GET<Any>("/items/-1")))
    }

    @Test
    fun declarativeConditionsAndMatchers() {
        val http = client.toBlocking()
        assertEquals("download app.zip", http.retrieve(HttpRequest.GET<Any>("/downloads/app.zip").header("X-Channel", "Canary")))
        assertEquals("download app.zip", http.retrieve(HttpRequest.GET<Any>("/downloads/app.zip").cookie(Cookie.of("channel", "beta"))))
        // the address of the peer, not a forwarded one
        assertEquals("download app.zip", http.retrieve(HttpRequest.GET<Any>("/downloads/app.zip").header("X-Channel", "beta").header("X-Forwarded-For", "203.0.113.9")))
        val noChannel = assertThrows<HttpClientResponseException> { http.retrieve(HttpRequest.GET<Any>("/downloads/app.zip")) }
        assertEquals(HttpStatus.NOT_FOUND, noChannel.status)
        val notAZip = assertThrows<HttpClientResponseException> {
            http.retrieve(HttpRequest.GET<Any>("/downloads/app.txt").header("X-Channel", "beta"))
        }
        assertEquals(HttpStatus.NOT_FOUND, notAZip.status)
    }

    @Test
    fun aFilterReadsTheAttributesOfTheRoute() {
        val http = client.toBlocking()
        assertEquals("daily report", http.retrieve(HttpRequest.GET<Any>("/reports/daily").header("X-Role", "auditor")))
        val forbidden = assertThrows<HttpClientResponseException> {
            http.retrieve(HttpRequest.GET<Any>("/reports/salaries").header("X-Role", "auditor"))
        }
        assertEquals(HttpStatus.FORBIDDEN, forbidden.status)
        assertEquals("salaries", http.retrieve(HttpRequest.GET<Any>("/reports/salaries").header("X-Role", "admin")))
    }

    @Test
    fun aRouteOnAnotherPort() {
        val notFound = assertThrows<HttpClientResponseException> {
            client.toBlocking().retrieve(HttpRequest.GET<Any>("/management/health"))
        }
        assertEquals(HttpStatus.NOT_FOUND, notFound.status)
        HttpClient.create(URI.create("http://" + server.host + ":" + managementPort).toURL()).use { management ->
            assertEquals("UP", management.toBlocking().retrieve(HttpRequest.GET<Any>("/management/health")))
        }
    }
}
