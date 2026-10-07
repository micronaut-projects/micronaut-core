package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HelloRoutesTest {

    @Test
    fun theHandlerAnswers() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "HelloRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                assertEquals("Hello World", client.toBlocking().retrieve(HttpRequest.GET<Any>("/hello/World")))
            }
        }
    }
}
