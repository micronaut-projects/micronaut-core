package io.micronaut.docs.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdcServiceSpec {

    @Test
    fun testMdcPropagation() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("mdc.example.service.enabled" to true)).use { server ->
            HttpClient.create(server.url).use { client ->
                val response = client.toBlocking().retrieve(HttpRequest.GET<Any>("/mdc/test"))

                assertTrue(response.startsWith("New user id: "))
                assertTrue(response.endsWith(" name: Denis"))
            }
        }
    }
}

@Controller("/mdc")
@Requires(property = "mdc.example.service.enabled")
internal class MdcController(private val mdcService: MdcService) {

    @Get(value = "/test", produces = [MediaType.TEXT_PLAIN])
    fun test(): String = mdcService.createUser("Denis")
}
