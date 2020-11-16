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
}
