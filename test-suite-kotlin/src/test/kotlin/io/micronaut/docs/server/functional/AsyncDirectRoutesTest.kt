package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AsyncDirectRoutesTest {

    @Test
    fun aDirectRouteRunsOnAnExecutorOrCompletesLater() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "AsyncDirectRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                assertEquals("Q1: 1200 orders", http.retrieve(HttpRequest.GET<Any>("/reports/2026-q1")))
                assertEquals("42.00", http.retrieve(HttpRequest.GET<Any>("/quotes/MNT")))
                // declined: no ordinary route answers them
                for (path in listOf("/reports/2025-q4", "/quotes/XYZ")) {
                    val notFound = assertThrows(HttpClientResponseException::class.java) {
                        http.retrieve(HttpRequest.GET<Any>(path))
                    }
                    assertEquals(HttpStatus.NOT_FOUND, notFound.status)
                }
            }
        }
    }
}
