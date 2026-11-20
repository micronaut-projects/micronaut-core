package io.micronaut.docs.server.limit

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.limit.RequestLimiter
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RequestLimitTest {

    @Test
    fun bothRoutesGoThroughTheNamedLimiter() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
            "spec.name" to "RequestLimitTest",
            "micronaut.server.limits.reports.max-concurrent" to 2
        )).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val limiter = server.applicationContext.getBean(RequestLimiter::class.java, Qualifiers.byName("reports"))
                assertEquals("report 1", client.toBlocking().retrieve(HttpRequest.GET<Any>("/limited-reports/1")))
                assertEquals("report 2", client.toBlocking().retrieve(HttpRequest.GET<Any>("/limited-reports/functional/2")))
                assertEquals(2, limiter.acquiredCount)
            }
        }
    }
}
