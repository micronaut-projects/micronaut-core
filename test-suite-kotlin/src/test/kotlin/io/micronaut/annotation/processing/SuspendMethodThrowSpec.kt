package io.micronaut.annotation.processing

import io.kotest.common.runBlocking
import io.micronaut.http.HttpStatus.OK
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.reactive.awaitSingle
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@MicronautTest
class SuspendMethodThrowSpec {

    @Inject
    @field:Client("/demo-error")
    lateinit var client: HttpClient

    @Test
    fun testAsyncMethodReturnTypeUnit() = runBlocking {
        val id = randomUUID().toString()
        val response = client.exchange("/async/unit/throw?id=$id").awaitSingle()

        assertNotNull(response)
        assertEquals(OK, response.status)
        assertEquals(id, response.getBody(String::class.java).orElse(null))
    }
}
