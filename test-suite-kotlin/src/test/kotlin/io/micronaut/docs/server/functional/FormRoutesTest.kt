package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FormRoutesTest {

    companion object {
        private lateinit var uploads: Path
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            uploads = Files.createTempDirectory("form-routes")
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
                "spec.name" to "FormRoutesTest",
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
    fun aUrlEncodedForm() {
        val answer = client.toBlocking().retrieve(HttpRequest.POST("/forms/signup", mapOf("name" to "Ada", "age" to "36"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
        assertEquals("Welcome Ada, 36", answer)
        val withDefault = client.toBlocking().retrieve(HttpRequest.POST("/forms/signup", mapOf("name" to "Bob"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
        assertEquals("Welcome Bob, 18", withDefault)
    }

    @Test
    fun aCollectedMultipartForm() {
        val body = MultipartBody.builder()
            .addPart("name", "Ada")
            .addPart("avatar", "ada.png", MediaType.IMAGE_PNG_TYPE, ByteArray(2048))
            .build()
        val answer = client.toBlocking().retrieve(HttpRequest.POST("/forms/profile", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
        assertEquals("Ada sent ada.png, 2048 bytes", answer)
    }

    @Test
    fun aStreamedFilePart() {
        val content = "line\n".repeat(10_000)
        val body = MultipartBody.builder()
            .addPart("file", "lines.txt", MediaType.TEXT_PLAIN_TYPE, content.toByteArray())
            .build()
        val response = client.toBlocking().exchange(HttpRequest.POST("/forms/upload", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String::class.java)
        assertEquals(HttpStatus.CREATED, response.status)
        assertEquals(content, Files.readString(uploads.resolve(response.body()!!)))
    }
}
