package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreparedRoutesTest {

    @Test
    fun aFunctionAnswersWithAPreparedResponse() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "PreparedRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                val current = http.exchange(HttpRequest.GET<Any>("/release"), String::class.java)
                assertEquals(HttpStatus.OK, current.status)
                assertEquals("release 2026.10", current.body())
                assertEquals("\"2026.10\"", current.headers.get(HttpHeaders.ETAG))

                val notModified = http.exchange(HttpRequest.GET<Any>("/release").header(HttpHeaders.IF_NONE_MATCH, "\"2026.10\""), String::class.java)
                assertEquals(HttpStatus.NOT_MODIFIED, notModified.status)
                assertEquals("\"2026.10\"", notModified.headers.get(HttpHeaders.ETAG))
            }
        }
    }
}
