package io.micronaut.http.server

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "spec.name", value = "ControllerSuspendTest")
class ControllerSuspendTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Test
    fun test() {
        server.applicationContext.createBean(HttpClient::class.java, server.uri).use { client ->
            Assertions.assertTrue(client.toBlocking().exchange("/controller-suspend/unit", String::class.java).body.isEmpty)
        }
    }

    @Requires(property = "spec.name", value = "ControllerSuspendTest")
    @Controller("/controller-suspend")
    class MyController {
        @Get("unit")
        suspend fun unit() {
            delay(1)
        }
    }
}
