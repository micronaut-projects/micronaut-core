package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.SuspendClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SuspendClientSpec {

    @Test
    fun testSuspendClientBody() {
        val server = createServer()
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).call("test")
        }
        Assertions.assertEquals(response, "{\"newState\":\"test\"}")
        server.close()
    }

    @Test
    fun testNotFound() {
        val server = createServer()
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).notFound()
        }

        Assertions.assertEquals(response.status, HttpStatus.NOT_FOUND)
        server.close()
    }

    @Test
    fun testNotFoundWithoutHttpResponseWrapper() {
        val server = createServer()
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).notFoundWithoutHttpResponseWrapper()
        }

        Assertions.assertNull(response)
        server.close()
    }

    @Test
    fun testFlowAsPublisherInFilterStep() {
        val server = createServer()
        val ctx = server.applicationContext
        val response = runBlocking {
            ctx.getBean(SuspendClient::class.java).call(SuspendClientFilter.filterCheck)
        }
        Assertions.assertEquals(response, "{\"newState\":\"${SuspendClientFilter.filterCheck}\"}")
        server.close()
    }

    fun createServer() : EmbeddedServer {
        return ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "SuspendClientSpec"))
    }

}
