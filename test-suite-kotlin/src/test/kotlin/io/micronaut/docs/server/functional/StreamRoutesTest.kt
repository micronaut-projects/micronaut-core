package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreamRoutesTest {

    @Test
    fun theRoutesStreamTheirResponses() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "StreamRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                assertEquals("id: 3\ndata: 3\n\nid: 2\ndata: 2\n\nid: 1\ndata: 1\n\n", http.retrieve(HttpRequest.GET<Any>("/countdown/3")))
                assertEquals("data: tick 1\n\ndata: tick 2\n\ndata: tick 3\n\n", http.retrieve(HttpRequest.GET<Any>("/ticks")))
                assertEquals("data: a\n\ndata: b\n\n", http.retrieve(HttpRequest.POST("/words", "a b").contentType(MediaType.TEXT_PLAIN_TYPE)))
                assertEquals("[1,2,3]", http.retrieve(HttpRequest.GET<Any>("/numbers")))
                val notified = http.exchange(message("notify"), String::class.java)
                assertEquals(HttpStatus.ACCEPTED, notified.status)
                assertEquals("s-1", notified.headers.get("Session-Id"))
                assertEquals("{\"result\":\"pong\"}", http.retrieve(message("ping")))
                assertEquals("data: received hello\n\n", http.retrieve(message("hello")))
                // a client that accepts only JSON
                assertEquals("{\"result\":\"pong\"}", http.retrieve(HttpRequest.POST("/messages", "ping")
                    .contentType(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.APPLICATION_JSON_TYPE)))
            }
        }
    }

    private fun message(message: String): MutableHttpRequest<String> =
        HttpRequest.POST("/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE)
}
