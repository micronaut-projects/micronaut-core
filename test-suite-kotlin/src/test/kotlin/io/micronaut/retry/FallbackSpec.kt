package io.micronaut.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FallbackSpec {

    lateinit var server: EmbeddedServer
    lateinit var fallbackClient: FallbackClient

    @BeforeEach
    fun setUp() {
        server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "FallbackClientSpec"))
        val context = server.applicationContext
        fallbackClient = context.getBean(FallbackClient::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `server ok with string output`() {
        runBlocking {
            val response = fallbackClient.stringOutput(false, false)
            assertEquals("server ok", response)
        }
    }

    @Test
    fun `server ok with HttpResponse output`() {
        runBlocking {
            val response = fallbackClient.httpResponseOutput(false, false)
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("server ok", response.body())
        }
    }

    @Test
    fun `server ok with null`() {
        runBlocking {
            val response = fallbackClient.nullOutput(false, false)
            assertNull(response)
        }
    }

    @Test
    fun `server fail with string output`() {
        runBlocking {
            val response = fallbackClient.stringOutput(true, false)
            assertEquals("fallback ok", response)
        }
    }

    @Test
    fun `server fail with HttpResponse output`() {
        runBlocking {
            val response = fallbackClient.httpResponseOutput(true, false)
            assertEquals(HttpStatus.OK, response.status)
            assertEquals("fallback ok", response.body())
        }
    }

    @Test
    fun `server fail with null`() {
        runBlocking {
            val response = fallbackClient.nullOutput(true, false)
            assertNull(response)
        }
    }

    @Test
    fun `faillback fail with string output`() {
        runBlocking {
            val exception = assertThrows<RuntimeException> { fallbackClient.stringOutput(true, true) }
            assertEquals("fallback fail", exception.message)
        }
    }

    @Test
    fun `faillback fail with HttpResponse output`() {
        runBlocking {
            val exception = assertThrows<RuntimeException> { fallbackClient.httpResponseOutput(true, true) }
            assertEquals("fallback fail", exception.message)
        }
    }

    @Test
    fun `faillback fail with null`() {
        runBlocking {
            val exception = assertThrows<RuntimeException> { fallbackClient.nullOutput(true, true) }
            assertEquals("fallback fail", exception.message)
        }
    }


}


@Requires(property = "spec.name", value = "FallbackClientSpec")
@Controller("/fallback")
class FallbackClientController {

    @Post("stringOutput")
    fun stringOutput(serverFail: Boolean, fallbackFail: Boolean): HttpResponse<String> {
        return httpResponseOutput(serverFail, fallbackFail)
    }

    @Post("httpResponseOutput")
    fun httpResponseOutput(serverFail: Boolean, fallbackFail: Boolean): HttpResponse<String> {
        return if (serverFail) {
            HttpResponse.serverError("server fail")
        } else {
            HttpResponse.ok("server ok")
        }
    }

    @Post("nullOutput")
    fun nullOutput(serverFail: Boolean, fallbackFail: Boolean): HttpResponse<String> {
        return if (serverFail) {
            HttpResponse.serverError()
        } else {
            HttpResponse.ok()
        }
    }
}

@Client("/fallback")
@Recoverable(api = FallbackClientFallback::class)
interface FallbackClient {

    @Post("stringOutput")
    suspend fun stringOutput(serverFail: Boolean, fallbackFail: Boolean): String

    @Post("httpResponseOutput")
    suspend fun httpResponseOutput(serverFail: Boolean, fallbackFail: Boolean): HttpResponse<String>

    @Post("nullOutput")
    suspend fun nullOutput(serverFail: Boolean, fallbackFail: Boolean): String?
}

@Fallback
open class FallbackClientFallback : FallbackClient {
    override suspend fun stringOutput(serverFail: Boolean, fallbackFail: Boolean): String {
        return if (fallbackFail) {
            throw RuntimeException("fallback fail")
        } else {
            "fallback ok"
        }
    }

    override suspend fun httpResponseOutput(serverFail: Boolean, fallbackFail: Boolean): HttpResponse<String> {
        return if (fallbackFail) {
            throw RuntimeException("fallback fail")
        } else {
            HttpResponse.ok("fallback ok")
        }
    }

    override suspend fun nullOutput(serverFail: Boolean, fallbackFail: Boolean): String? {
        return if (fallbackFail) {
            throw RuntimeException("fallback fail")
        } else {
            null
        }
    }
}
