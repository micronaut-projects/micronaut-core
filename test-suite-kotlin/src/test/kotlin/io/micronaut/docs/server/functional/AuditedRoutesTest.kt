package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuditedRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
                "spec.name" to "AuditedRoutesTest",
                "micronaut.router.versioning.enabled" to "true",
                "micronaut.router.versioning.header.enabled" to "true"))
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
    fun theAnnotationsOfARouteBindAFilter() {
        val http = client.toBlocking()
        val payment = http.exchange(HttpRequest.POST("/payments/10", ""), String::class.java)
        assertEquals("paid 10", payment.body())
        assertEquals("true", payment.headers["X-Audited"])
        val refund = http.exchange(HttpRequest.POST("/refunds/5", ""), String::class.java)
        assertEquals("refunded 5", refund.body())
        assertEquals("true", refund.headers["X-Audited"])
        val prices = http.exchange(HttpRequest.GET<Any>("/prices"), String::class.java)
        assertEquals("prices", prices.body())
        assertNull(prices.headers["X-Audited"])
    }

    @Test
    fun theVersionAnnotationOfARouteSelectsIt() {
        val http = client.toBlocking()
        val v1 = http.exchange(HttpRequest.GET<Any>("/receipts/7").header("X-API-VERSION", "1"), String::class.java)
        assertEquals("receipt v1 7", v1.body())
        assertNull(v1.headers["X-Audited"])
        val v2 = http.exchange(HttpRequest.GET<Any>("/receipts/7").header("X-API-VERSION", "2"), String::class.java)
        assertEquals("receipt v2 7", v2.body())
        assertEquals("true", v2.headers["X-Audited"])
    }

    @Test
    fun theRoutesOfAGroupHaveItsExecutorAndAnnotations() {
        val http = client.toBlocking()
        val users = http.exchange(HttpRequest.GET<Any>("/admin/users").header("X-API-VERSION", "2"), String::class.java)
        // the blocking executor, not the event loop
        assertFalse(users.body()!!.contains("EventLoop"), users.body())
        assertEquals("true", users.headers["X-Audited"])
        val deleted = http.exchange(HttpRequest.DELETE<Any>("/admin/users/3").header("X-API-VERSION", "2"), String::class.java)
        assertEquals("deleted 3", deleted.body())
        assertEquals("true", deleted.headers["X-Audited"])
        // the routes of the group answer the version 2 only
        val v1 = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.GET<Any>("/admin/users").header("X-API-VERSION", "1"), String::class.java)
        }
        assertEquals(HttpStatus.NOT_FOUND, v1.status)
    }

    @Test
    fun aDeclaredRoute() {
        assertEquals("balance of main", client.toBlocking().retrieve(HttpRequest.GET<Any>("/balance/main")))
    }
}
