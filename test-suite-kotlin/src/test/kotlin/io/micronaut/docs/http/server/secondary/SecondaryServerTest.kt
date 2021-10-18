package io.micronaut.docs.http.server.secondary

import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest

class SecondaryServerTest {
    // tag::inject[]
    @Inject
    @field:Client(path = "/", id = SecondaryNettyServer.SERVER_ID)
    lateinit var httpClient : HttpClient // <1>

    @Inject
    @field:Named(SecondaryNettyServer.SERVER_ID)
    lateinit var embeddedServer : EmbeddedServer // <2>
    // end::inject[]

    @Test
    fun testCallSecondaryServer() {
        val result = httpClient.toBlocking().retrieve("/test/secondary/server")
        Assertions.assertTrue(result.endsWith(embeddedServer.port.toString()))
    }
}

@Controller("/test/secondary/server")
class TestController {
    @Get
    fun hello(request: HttpRequest<*>): String {
        return "Hello from: " + request.serverAddress.port
    }
}