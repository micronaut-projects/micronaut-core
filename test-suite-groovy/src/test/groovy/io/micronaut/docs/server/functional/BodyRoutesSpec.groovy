package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class BodyRoutesSpec extends Specification {

    @Shared Path uploads = Files.createTempDirectory("body-routes")
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        "spec.name"        : "BodyRoutesSpec",
        "uploads.directory": uploads.toString()])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void cleanupSpec() {
        uploads.toFile().deleteDir()
    }

    void "a decoded body"() {
        when:
        HttpResponse<Item> response = client.toBlocking().exchange(HttpRequest.POST("/async/items", new Item(0, "lamp")), Item)

        then:
        response.status == HttpStatus.CREATED
        response.body().name() == "lamp"
    }

    void "the elements of a JSON array"() {
        given:
        ItemRepository items = server.applicationContext.getBean(ItemRepository)

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST("/async/items/import",
            [new Item(101, "a"), new Item(102, "b"), new Item(103, "c")]))

        then:
        response.status == HttpStatus.ACCEPTED
        items.find(102).name() == "b"
    }

    void "bounded text"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.POST("/async/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)) == "received 5 characters"

        when:
        http.retrieve(HttpRequest.POST("/async/notes", "x" * 2048).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        HttpClientResponseException tooLarge = thrown()
        tooLarge.status == HttpStatus.REQUEST_ENTITY_TOO_LARGE
    }

    void "a body written to a file"() {
        given:
        byte[] content = new byte[100_000]
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i
        }

        when:
        String name = client.toBlocking().retrieve(HttpRequest.PUT("/async/files", content).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE))

        then:
        Files.readAllBytes(uploads.resolve(name)) == content
    }

    void "a request rejected without reading the body"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        http.retrieve(HttpRequest.POST("/async/guarded", new byte[10]).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE))

        then:
        HttpClientResponseException unauthorized = thrown()
        unauthorized.status == HttpStatus.UNAUTHORIZED

        and:
        http.retrieve(HttpRequest.POST("/async/guarded", new byte[10])
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .header("X-Token", "secret")) == "accepted 10 bytes"
    }
}
