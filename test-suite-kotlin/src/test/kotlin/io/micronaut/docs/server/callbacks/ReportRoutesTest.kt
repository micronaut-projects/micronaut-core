package io.micronaut.docs.server.callbacks

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.Socket
import java.util.concurrent.TimeUnit

class ReportRoutesTest {

    @Test
    fun theRouteSeesTheOutcomeAndTheAbandonment() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "ReportRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val reports = server.applicationContext.getBean(Reports::class.java)
                reports.generate("1").complete("report 1")
                assertEquals("report 1", client.toBlocking().retrieve(HttpRequest.GET<Any>("/reports/1")))
                assertFalse(reports.outcome("1").get(10, TimeUnit.SECONDS).isPresent)

                val report = reports.generate("2")
                Socket(server.host, server.port).use { socket ->
                    socket.getOutputStream().write("GET /reports/2 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    socket.getOutputStream().flush()
                    val deadline = System.currentTimeMillis() + 10_000
                    while (report.numberOfDependents == 0 && System.currentTimeMillis() < deadline) {
                        Thread.sleep(20)
                    }
                }
                assertTrue(reports.outcome("2").get(10, TimeUnit.SECONDS).isPresent)
                assertTrue(report.isCancelled)
            }
        }
    }
}
