package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
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

class LocatorRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "LocatorRoutesTest"))
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
    fun theRoutesOfTheLocatedTargetAnswer() {
        val http = client.toBlocking()
        for (prefix in listOf("/shops", "/remote-shops")) {
            assertEquals("north", http.retrieve(HttpRequest.GET<Any>("$prefix/north")))
            assertEquals("coffee", http.retrieve(HttpRequest.GET<Any>("$prefix/north/items/1")))
            assertEquals("juice", http.retrieve(HttpRequest.GET<Any>("$prefix/south/items/0")))
            val unknownShop = assertThrows<HttpClientResponseException> {
                http.retrieve(HttpRequest.GET<Any>("$prefix/west/items/0"))
            }
            assertEquals(HttpStatus.NOT_FOUND, unknownShop.status)
            val notAllowed = assertThrows<HttpClientResponseException> {
                http.retrieve(HttpRequest.DELETE<Any>("$prefix/north/items/0"))
            }
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.status)
        }
        // the routes the function chose for the located shop
        assertEquals("coffee", http.retrieve(HttpRequest.GET<Any>("/archived-shops/north/items/1")))
        assertEquals("juice", http.retrieve(HttpRequest.GET<Any>("/archived-shops/south/item")))
        val notRouted = assertThrows<HttpClientResponseException> {
            http.retrieve(HttpRequest.GET<Any>("/archived-shops/south/items/0"))
        }
        assertEquals(HttpStatus.NOT_FOUND, notRouted.status)
    }
}
