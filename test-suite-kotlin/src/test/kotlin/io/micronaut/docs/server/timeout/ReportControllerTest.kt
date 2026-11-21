package io.micronaut.docs.server.timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReportControllerTest {

    @Test
    fun theRoutesAnswerWithinTheirTimeout() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "ReportControllerTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                assertEquals("summary", client.toBlocking().retrieve(HttpRequest.GET<Any>("/reports/summary")))
                assertEquals("export", client.toBlocking().retrieve(HttpRequest.GET<Any>("/reports/export")))
                assertEquals("archive", client.toBlocking().retrieve(HttpRequest.GET<Any>("/reports/archive")))
                val e = assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().retrieve(HttpRequest.GET<Any>("/reports/rebuild"))
                }
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.status)
                val stopped = assertThrows(HttpClientResponseException::class.java) {
                    client.toBlocking().retrieve(HttpRequest.GET<Any>("/index/rebuild"))
                }
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, stopped.status)
            }
        }
    }
}
