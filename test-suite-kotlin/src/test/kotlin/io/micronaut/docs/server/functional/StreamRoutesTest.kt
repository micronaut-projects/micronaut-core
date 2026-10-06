package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
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
            }
        }
    }
}
