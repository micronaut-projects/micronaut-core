package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
                // an error before the first event is answered by the error routes
                assertEquals("data: order 1 shipped\n\n", http.retrieve(HttpRequest.GET<Any>("/orders/1/updates")))
                val notFound = assertThrows(HttpClientResponseException::class.java) { http.retrieve(HttpRequest.GET<Any>("/orders/2/updates")) }
                assertEquals(HttpStatus.NOT_FOUND, notFound.status)
                // after it, as an event
                assertEquals("data: started\n\ndata: done\n\n", http.retrieve(HttpRequest.GET<Any>("/jobs/1")))
                assertEquals("data: started\n\nevent: error\ndata: the job failed\n\n", http.retrieve(HttpRequest.GET<Any>("/jobs/2")))
                // a reconnecting client resumes after the last event it received
                assertEquals("id: 1\nretry: 5000\ndata: item 1\n\nid: 2\ndata: item 2\n\nid: 3\ndata: item 3\n\n", http.retrieve(HttpRequest.GET<Any>("/feed")))
                assertEquals("id: 2\nretry: 5000\ndata: item 2\n\nid: 3\ndata: item 3\n\n", http.retrieve(HttpRequest.GET<Any>("/feed").header("Last-Event-ID", "1")))
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
