package io.micronaut.docs.server.asyncbody

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeopleControllerTest {

    private val bufferLimit = 256 * 1024

    private val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf(
        "spec.name" to "PeopleControllerTest",
        "micronaut.server.max-request-buffer-size" to bufferLimit
    ))
    private val client = server.applicationContext.createBean(HttpClient::class.java, server.url)

    @AfterAll
    fun stopServer() {
        client.stop()
        server.stop()
    }

    @Test
    fun decodesTheBody() {
        val response = client.toBlocking().exchange(
            HttpRequest.POST("/people", """{"name":"Fred","age":45}""").contentType(MediaType.APPLICATION_JSON_TYPE),
            Person::class.java)
        assertEquals(HttpStatus.CREATED, response.status)
        assertEquals(Person("Fred", 45), response.body())
    }

    @Test
    fun aMalformedBodyIsABadRequest() {
        val e = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange<String, Any>(
                HttpRequest.POST("/people", """{"name":""").contentType(MediaType.APPLICATION_JSON_TYPE))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
    }

    @Test
    fun aBodyLargerThanTheBufferLimitIsTooLarge() {
        val json = """{"name":"${"x".repeat(4 * bufferLimit)}","age":45}"""
        val e = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange<String, Any>(
                HttpRequest.POST("/people", json).contentType(MediaType.APPLICATION_JSON_TYPE))
        }
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, e.status)
    }

    @Test
    fun importsTheElementsOfAJsonArray() {
        val array = (0 until 2000).joinToString(",", "[", "]") { i ->
            """{"name":"${"x".repeat(1000)}$i","age":$i}"""
        }
        // the limit applies to each element, not to the whole body
        assertTrue(array.length > 4 * bufferLimit)
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/people/import", array).contentType(MediaType.APPLICATION_JSON_TYPE))
        assertEquals("Imported 2000", response)
    }

    @Test
    fun importsTheElementsOfAJsonStream() {
        val stream = "{\"name\":\"Fred\",\"age\":45}\n{\"name\":\"Wilma\",\"age\":40}\n"
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/people/import", stream).contentType(MediaType.APPLICATION_JSON_STREAM_TYPE))
        assertEquals("Imported 2", response)
    }

    @Test
    fun aFilterReadsACopyAndTheControllerReadsTheBody() {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/messages", "Hello Fred").contentType(MediaType.TEXT_PLAIN_TYPE))
        assertEquals("Received Hello Fred", response)
        val e = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange<String, Any>(
                HttpRequest.POST("/messages", "Buy spam").contentType(MediaType.TEXT_PLAIN_TYPE))
        }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
    }

    @Test
    fun readsTheBodyAsText() {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/people/notes", "Call Fred").contentType(MediaType.TEXT_PLAIN_TYPE))
        assertEquals("Received 9 characters", response)
    }
}
