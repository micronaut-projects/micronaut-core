package io.micronaut.http.server

import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MicronautTest
@Property(name = "spec.name", value = "SuspendRouteTest")
class SuspendRouteTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var client: HttpClient

    @Test
    fun testVoid() {
        val exc = assertThrows<HttpClientResponseException> {
            client.toBlocking().retrieve("${server.uri}/suspend-route")
        }
        exc.status shouldBe HttpStatus.UNAUTHORIZED
    }

    @Controller("/suspend-route")
    class MyCtrl {
        @Get
        suspend fun index() {
            withContext(Dispatchers.IO) {
                throw HttpStatusException(HttpStatus.UNAUTHORIZED, "foo")
            }
        }
    }
}
