package io.micronaut.docs.server.formdata

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileControllerTest {

    private val maxFileSize = 512 * 1024

    private val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf(
        "spec.name" to "ProfileControllerTest",
        "micronaut.server.multipart.max-file-size" to maxFileSize
    ))
    private val client = server.applicationContext.createBean(HttpClient::class.java, server.url)

    @AfterAll
    fun stopServer() {
        client.stop()
        server.stop()
    }

    @Test
    fun readsTheWholeForm() {
        val body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("age", "30")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .build()
        assertEquals("Fred (30) sent avatar.png of 7 bytes", post("/profile/form", body))

        val withoutAge = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .build()
        assertEquals("Fred (18) sent avatar.png of 7 bytes", post("/profile/form", withoutAge))
    }

    @Test
    fun aMissingFileIsABadRequest() {
        val body = MultipartBody.builder()
            .addPart("name", "Fred")
            .build()
        assertEquals(HttpStatus.BAD_REQUEST, status("/profile/form", body))
    }

    @Test
    fun bindsTheFilesAndFieldsOfTheForm() {
        val body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".toByteArray())
            .addPart("documents", "letter.pdf", MediaType.APPLICATION_PDF_TYPE, "letter".toByteArray())
            .build()
        assertEquals("Fred sent avatar.png without a cover and 2 documents", post("/profile/arguments", body))

        val withCover = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .addPart("cover", "cover.png", MediaType.IMAGE_PNG_TYPE, "cover".toByteArray())
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".toByteArray())
            .build()
        assertEquals("Fred sent avatar.png with cover.png and 1 documents", post("/profile/arguments", withCover))
    }

    @Test
    fun aFileLargerThanTheMaximumFileSizeIsTooLarge() {
        val body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, ByteArray(2 * maxFileSize))
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".toByteArray())
            .build()
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, status("/profile/arguments", body))
    }

    @Test
    fun streamsAPart() {
        val body = MultipartBody.builder()
            .addPart("title", "Holiday")
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, ByteArray(256 * 1024))
            .build()
        assertEquals("Holiday stored holiday.mp4", post("/profile/video", body))
    }

    @Test
    fun aFieldSentAfterAStreamedPartIsABadRequest() {
        val body = MultipartBody.builder()
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, ByteArray(256 * 1024))
            .addPart("title", "Holiday")
            .build()
        assertEquals(HttpStatus.BAD_REQUEST, status("/profile/video", body))
    }

    @Test
    fun readsThePartsOneByOne() {
        val body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .addPart("age", "30")
            .build()
        assertEquals("name=Fred, avatar stored, age=30, ", post("/profile/parts", body))
    }

    @Test
    fun aFilterReadsTheFormBeforeTheController() {
        val accepted = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("terms", "true")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .build()
        assertEquals("Welcome Fred, your avatar has 7 bytes", post("/signup", accepted))

        val refused = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".toByteArray())
            .build()
        assertEquals(HttpStatus.BAD_REQUEST, status("/signup", refused))
    }

    private fun post(uri: String, body: MultipartBody): String =
        client.toBlocking().retrieve(HttpRequest.POST(uri, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

    private fun status(uri: String, body: MultipartBody): HttpStatus =
        assertThrows(HttpClientResponseException::class.java) { post(uri, body) }.status
}
