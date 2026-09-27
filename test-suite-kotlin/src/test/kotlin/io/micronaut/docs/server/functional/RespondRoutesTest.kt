package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RespondRoutesTest {

    @Test
    fun theRoutesAnswerWithoutAHandler() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "RespondRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                val ping = http.exchange(HttpRequest.GET<Any>("/ping"), String::class.java)
                assertEquals("pong", ping.body())
                assertEquals("max-age=60", ping.headers.get("Cache-Control"))
                // the client follows the redirect
                assertEquals("pong", http.retrieve(HttpRequest.GET<Any>("/old-ping")))
                assertEquals("visit 1", http.retrieve(HttpRequest.GET<Any>("/visits")))
                assertEquals("visit 2", http.retrieve(HttpRequest.GET<Any>("/visits")))
                assertEquals("Hello World", http.retrieve(HttpRequest.GET<Any>("/greetings/World")))
                val gone = assertThrows(HttpClientResponseException::class.java) {
                    http.retrieve(HttpRequest.POST("/legacy/webhook", "{}"))
                }
                assertEquals(HttpStatus.GONE, gone.status)
            }
        }
    }
}
