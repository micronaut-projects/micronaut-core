package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class FormLocatorRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "FormLocatorRoutesTest"))
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
    fun theLocatorAndTheLocatedRouteReadTheForm() {
        val http = client.toBlocking()
        assertEquals("ann added tea", http.retrieve(HttpRequest.POST("/carts/items", "customer=ann&item=tea")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)))
        // not a form: located with the empty form
        assertEquals("guest", http.retrieve(HttpRequest.GET<Any>("/carts")))
    }
}
