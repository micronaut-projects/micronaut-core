package io.micronaut.docs.server.suspend

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@MicronautTest
@Property(name = "micronaut.propagation", value = "scoped-value")
class ScopedValueCoroutineFailureTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `suspend endpoint works when propagation mode is scoped value`() {
        val result = client.toBlocking().retrieve(HttpRequest.GET<Any>("/coroutine/failure"))
        assertEquals("success", result)
    }

    @Controller("/coroutine/failure")
    internal class CoroutineFailureController {

        @Get
        suspend fun index(): String {
            return withContext(Dispatchers.Default) {
                "success"
            }
        }
    }
}
