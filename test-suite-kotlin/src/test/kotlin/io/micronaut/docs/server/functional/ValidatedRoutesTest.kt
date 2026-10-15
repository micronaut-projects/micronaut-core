package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValidatedRoutesTest {

    @Test
    fun theBeanMethodValidatesTheBody() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "ValidatedRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val created = client.toBlocking().exchange<Map<String, String>, Any>(HttpRequest.POST("/products", mapOf("name" to "lamp")))
                assertEquals(HttpStatus.CREATED, created.status)
                val invalid = assertThrows<HttpClientResponseException> {
                    client.toBlocking().exchange<Map<String, String>, Any>(HttpRequest.POST("/products", mapOf("name" to "")))
                }
                assertEquals(HttpStatus.BAD_REQUEST, invalid.status)
            }
        }
    }
}
