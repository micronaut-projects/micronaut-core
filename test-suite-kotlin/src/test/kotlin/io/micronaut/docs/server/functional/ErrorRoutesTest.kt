package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ErrorRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "ErrorRoutesTest"))
            client = server.applicationContext.createBean(HttpClient::class.java, server.url)
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            client.stop()
            server.stop()
        }

        private fun body(error: HttpClientResponseException): Map<String, String> =
            error.response.getBody(Argument.mapOf(String::class.java, String::class.java)).orElseThrow()
    }

    @Test
    fun aGlobalErrorRoute() {
        val error = assertThrows<HttpClientResponseException> { client.toBlocking().retrieve(HttpRequest.GET<Any>("/orders/5")) }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals(mapOf("error" to "No order 5"), body(error))
    }

    @Test
    fun aGlobalStatusRoute() {
        val error = assertThrows<HttpClientResponseException> { client.toBlocking().retrieve(HttpRequest.GET<Any>("/nothing/here")) }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals(mapOf("error" to "Nothing at /nothing/here"), body(error))
    }

    @Test
    fun theErrorRoutesOfAGroupAreLocalToItsRoutes() {
        val http = client.toBlocking()
        assertEquals("ordered 2", http.retrieve(HttpRequest.POST("/checkout/2", "")))
        val invalid = assertThrows<HttpClientResponseException> { http.retrieve(HttpRequest.POST("/checkout/0", "")) }
        assertEquals(HttpStatus.BAD_REQUEST, invalid.status)
        assertEquals(mapOf("invalid" to "quantity must be positive"), body(invalid))
        val conflict = assertThrows<HttpClientResponseException> { http.retrieve(HttpRequest.POST("/checkout/11", "")) }
        assertEquals(HttpStatus.CONFLICT, conflict.status)
        assertEquals(mapOf("conflict" to "not enough stock"), body(conflict))
        // the error route of the group does not answer for a route outside of it
        val outside = assertThrows<HttpClientResponseException> { http.retrieve(HttpRequest.GET<Any>("/pricing/0")) }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, outside.status)
    }
}
