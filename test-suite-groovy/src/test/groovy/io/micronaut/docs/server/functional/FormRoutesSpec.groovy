package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class FormRoutesSpec extends Specification {

    @Shared Path uploads = Files.createTempDirectory("form-routes")
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        "spec.name"        : "FormRoutesSpec",
        "uploads.directory": uploads.toString()])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void cleanupSpec() {
        uploads.toFile().deleteDir()
    }

    void "a URL encoded form"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST("/forms/signup", [name: "Ada", age: "36"])
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) == "Welcome Ada, 36"
        client.toBlocking().retrieve(HttpRequest.POST("/forms/signup", [name: "Bob"])
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) == "Welcome Bob, 18"
    }

    void "a collected multipart form"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Ada")
            .addPart("avatar", "ada.png", MediaType.IMAGE_PNG_TYPE, new byte[2048])
            .build()

        expect:
        client.toBlocking().retrieve(HttpRequest.POST("/forms/profile", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)) == "Ada sent ada.png, 2048 bytes"
    }

    void "a streamed file part"() {
        given:
        String content = "line\n" * 10_000
        MultipartBody body = MultipartBody.builder()
            .addPart("file", "lines.txt", MediaType.TEXT_PLAIN_TYPE, content.getBytes(StandardCharsets.UTF_8))
            .build()

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.POST("/forms/upload", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)

        then:
        response.status == HttpStatus.CREATED
        Files.readString(uploads.resolve(response.body())) == content
    }
}
