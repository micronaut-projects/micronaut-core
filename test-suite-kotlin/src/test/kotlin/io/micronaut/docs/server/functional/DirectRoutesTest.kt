package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DirectRoutesTest {

    @Test
    fun theServerAnswersBeforeTheRequestIsCreated() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "DirectRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                assertEquals("UP", http.retrieve(HttpRequest.GET<Any>("/probe/live")))
                assertEquals("db UP", http.retrieve(HttpRequest.GET<Any>("/probe/db")))
                // a declined request continues to the ordinary route
                assertEquals("cached logo.png", http.retrieve(HttpRequest.GET<Any>("/assets/logo.png")))
                assertEquals("rendered readme.txt", http.retrieve(HttpRequest.GET<Any>("/assets/readme.txt")))
                assertEquals("User-agent: *\nDisallow: /private/\n", http.retrieve(HttpRequest.GET<Any>("/robots.txt")))
                // a conditional GET: the function reads the request
                val version = http.exchange(HttpRequest.GET<Any>("/version"), String::class.java)
                assertEquals("1.0.0", version.body())
                assertEquals("\"1.0.0\"", version.header(HttpHeaders.ETAG))
                val notModified = http.exchange(HttpRequest.GET<Any>("/version").header(HttpHeaders.IF_NONE_MATCH, "\"1.0.0\""), String::class.java)
                assertEquals(HttpStatus.NOT_MODIFIED, notModified.status)
                val forbidden = assertThrows(HttpClientResponseException::class.java) {
                    http.retrieve(HttpRequest.POST("/orders", "{}").header("User-Agent", "BadBot/1.0"))
                }
                assertEquals(HttpStatus.FORBIDDEN, forbidden.status)
                // no direct route matches: the request continues to the ordinary routes, here none
                val notFound = assertThrows(HttpClientResponseException::class.java) {
                    http.retrieve(HttpRequest.GET<Any>("/probe/queue"))
                }
                assertEquals(HttpStatus.NOT_FOUND, notFound.status)
            }
        }
    }
}
