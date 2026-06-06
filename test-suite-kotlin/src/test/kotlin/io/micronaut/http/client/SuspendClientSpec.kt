package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SuspendClientSpec {

    @Test
    fun testSuspendClientBody() {
        val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "SuspendClientSpec"))
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).call("test")
        }

        Assertions.assertEquals(response, "{\"newState\":\"test\"}")
    }

    @Test
    fun testNotFound() {
        val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "SuspendClientSpec"))
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).notFound()
        }

        Assertions.assertEquals(response.status, HttpStatus.NOT_FOUND)
    }

    @Test
    fun testNotFoundWithoutHttpResponseWrapper() {
        val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "SuspendClientSpec"))
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).notFoundWithoutHttpResponseWrapper()
        }

        Assertions.assertNull(response)
    }

    @Test
    fun testSuspendClientReturnsTypealiasToList() {
        // Regression test for https://github.com/micronaut-projects/micronaut-core/issues/12686
        // KSP was emitting Argument.of(List, "T") with literal "T" instead of resolving
        // the concrete type from the typealias, causing ClassCastException at runtime.
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "SuspendClientSpec")).use { server ->
            val ctx = server.applicationContext
            val bars = runBlocking {
                ctx.getBean(SuspendClient::class.java).getBars()
            }

            Assertions.assertEquals(1, bars.size)
            Assertions.assertEquals("hello", bars[0].name)
        }

}
