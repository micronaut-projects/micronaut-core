package io.micronaut.docs.server.timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReportRoutesTest {

    @Test
    fun theGroupTimeoutApplies() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "ReportRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                assertEquals("summary", client.toBlocking().retrieve(HttpRequest.GET<Any>("/fn-reports/summary")))
            }
        }
    }
}
