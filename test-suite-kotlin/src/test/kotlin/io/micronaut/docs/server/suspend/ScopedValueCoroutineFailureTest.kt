package io.micronaut.docs.server.suspend

import io.micronaut.context.annotation.Property
import io.micronaut.core.propagation.PropagatedContextConfiguration
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@MicronautTest
@Property(name = "micronaut.propagation", value = "scoped-value")
class ScopedValueCoroutineFailureTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `suspend endpoint fails when propagation mode is scoped value`() {
        val exception = assertFailsWith<HttpClientResponseException> {
            client.toBlocking().retrieve(HttpRequest.GET<Any>("/coroutine/failure"))
        }
        assertEquals(500, exception.status.code)
        val details = buildString {
            exception.response.getBody(String::class.java).ifPresent { append(it) }
            exception.cause?.message?.let { append(it) }
            append(exception.message)
        }
        assertTrue(details.contains("Scope propagation requires thread-local support"))
    }

    @Controller("/coroutine/failure")
    internal class CoroutineFailureController {

        @Get
        suspend fun index(): String {
            return withContext(Dispatchers.Default) {
                "never gets here"
            }
        }
    }
}
