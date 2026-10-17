package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class BenchmarkRoutesTest {

    @Test
    fun thePlaintextAndJsonTests() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "BenchmarkRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                val plaintext = http.exchange(HttpRequest.GET<Any>("/plaintext"), String::class.java)
                assertEquals("Hello, World!", plaintext.body())
                assertEquals("text/plain", plaintext.headers.get("Content-Type"))
                assertEquals("13", plaintext.headers.get("Content-Length"))
                assertEquals("Micronaut", plaintext.headers.get("Server"))
                ZonedDateTime.parse(plaintext.headers.get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME)
                assertNull(plaintext.headers.get("Content-Encoding"))

                val json = http.exchange(HttpRequest.GET<Any>("/json"), String::class.java)
                assertEquals("{\"message\":\"Hello, World!\"}", json.body())
                assertEquals("application/json", json.headers.get("Content-Type"))
                assertEquals("27", json.headers.get("Content-Length"))
                assertEquals("Micronaut", json.headers.get("Server"))
                ZonedDateTime.parse(json.headers.get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME)
            }
        }
    }
}
