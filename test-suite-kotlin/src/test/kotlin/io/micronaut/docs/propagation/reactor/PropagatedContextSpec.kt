package io.micronaut.docs.propagation.reactor

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropagatedContextSpec {

    @Test
    fun testMonoRequest() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "PropagatedContextSpec")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val uri = UriBuilder.of("/hello").queryParam("name", "Dean").build()
                val hello = client.toBlocking().retrieve(HttpRequest.GET<Any>(uri), String::class.java)

                assertEquals("Hello, Dean", hello)
            }
        }
    }
}
