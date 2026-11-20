package io.micronaut.docs.server.requestlog

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestMetricsTest {

    @Test
    fun theListenerSeesTheRouteTemplate() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
            "spec.name" to "RequestMetricsTest",
            "micronaut.server.netty.request-log-enabled" to true
        )).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                assertEquals("book 1", client.toBlocking().retrieve(HttpRequest.GET<Any>("/books/1")))
                val metrics = server.applicationContext.getBean(RequestMetrics::class.java)
                val deadline = System.currentTimeMillis() + 10_000
                while (metrics.nanosByRoute.isEmpty() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10)
                }
                assertTrue(metrics.nanosByRoute.containsKey("/books/{id} 200"), metrics.nanosByRoute.toString())
            }
        }
    }

    @Requires(property = "spec.name", value = "RequestMetricsTest")
    @Controller("/books")
    class BookController {
        @Get("/{id}")
        fun book(id: String) = "book $id"
    }
}
