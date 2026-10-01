package io.micronaut.docs.taskexecutors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TaskExecutorsBlockingTest {

    @Test
    fun testMethodAnnotatedWithTaskExecutorsBlocking() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "TaskExecutorsBlockingTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { httpClient ->
                val request = HttpRequest.GET<Any>(UriBuilder.of("/hello").path("world").build())
                    .accept(MediaType.TEXT_PLAIN)
                val response = httpClient.toBlocking().exchange(request, String::class.java)

                assertEquals(HttpStatus.OK, response.status())
                assertEquals("Hello World", response.body())
            }
        }
    }
}
