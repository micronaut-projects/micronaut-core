package io.micronaut.docs.server.request_scope

import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "TestControllerTest")
@MicronautTest
internal class TestControllerTest(
    @Client("/") val client: HttpClient
) {

    @Test
    fun `should support request scope`() {
        // Issue: if RequestScopeClass has non-nullable field, this does not work
        val response = client.toBlocking().exchange("/testEndpoint", DemoObject::class.java)
        assertEquals(200, response.status.code)
    }

    @Test
    fun `should clear request scope in subsequent request`() {
        val responseWithoutInput = client.toBlocking().exchange("/testEndpoint", DemoObject::class.java)
        assertEquals(200, responseWithoutInput.status.code)
        assertEquals("defaultText", responseWithoutInput.body()!!.text)

        val responseWithInputAsResult =
            client.toBlocking().exchange("/testEndpoint?text=inputText", DemoObject::class.java)
        assertEquals(200, responseWithInputAsResult.status.code)
        assertEquals("inputText", responseWithInputAsResult.body()!!.text)

        val responseWithoutInputAfterCallWithInput =
            client.toBlocking().exchange("/testEndpoint", DemoObject::class.java)
        assertEquals(200, responseWithoutInputAfterCallWithInput.status.code)
        
        // this should be again defaultText, but the request context is not cleared and thus this asserts fails
        assertEquals("defaultText", responseWithoutInputAfterCallWithInput.body()!!.text)
    }
}
