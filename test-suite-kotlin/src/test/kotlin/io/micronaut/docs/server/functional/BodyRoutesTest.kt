package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

class BodyRoutesTest {

    companion object {
        private lateinit var uploads: Path
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            uploads = Files.createTempDirectory("body-routes")
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
                "spec.name" to "BodyRoutesTest",
                "uploads.directory" to uploads.toString()))
            client = server.applicationContext.createBean(HttpClient::class.java, server.url)
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            client.stop()
            server.stop()
            uploads.toFile().deleteRecursively()
        }
    }

    @Test
    fun aDecodedBody() {
        val response = client.toBlocking().exchange(HttpRequest.POST("/async/items", Item(0, "lamp")), Item::class.java)
        assertEquals(HttpStatus.CREATED, response.status)
        assertEquals("lamp", response.body()!!.name)
    }

    @Test
    fun theElementsOfAJsonArray() {
        val items = server.applicationContext.getBean(ItemRepository::class.java)
        val response = client.toBlocking().exchange<List<Item>, Any>(HttpRequest.POST("/async/items/import",
            listOf(Item(101, "a"), Item(102, "b"), Item(103, "c"))))
        assertEquals(HttpStatus.ACCEPTED, response.status)
        assertEquals("b", items.find(102).name)
    }

    @Test
    fun boundedText() {
        val http = client.toBlocking()
        assertEquals("received 5 characters", http.retrieve(HttpRequest.POST("/async/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)))
        val tooLarge = assertThrows<HttpClientResponseException> {
            http.retrieve(HttpRequest.POST("/async/notes", "x".repeat(2048)).contentType(MediaType.TEXT_PLAIN_TYPE))
        }
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, tooLarge.status)
    }

    @Test
    fun aBodyWrittenToAFile() {
        val content = ByteArray(100_000) { i -> i.toByte() }
        val name = client.toBlocking().retrieve(HttpRequest.PUT("/async/files", content).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE))
        assertArrayEquals(content, Files.readAllBytes(uploads.resolve(name)))
    }

    @Test
    fun aRequestRejectedWithoutReadingTheBody() {
        val http = client.toBlocking()
        val unauthorized = assertThrows<HttpClientResponseException> {
            http.retrieve(HttpRequest.POST("/async/guarded", ByteArray(10)).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.status)
        assertEquals("accepted 10 bytes", http.retrieve(HttpRequest.POST("/async/guarded", ByteArray(10))
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .header("X-Token", "secret")))
    }
}
