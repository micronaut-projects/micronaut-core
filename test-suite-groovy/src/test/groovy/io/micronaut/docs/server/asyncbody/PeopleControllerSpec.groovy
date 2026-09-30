package io.micronaut.docs.server.asyncbody

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PeopleControllerSpec extends Specification {

    static final int BUFFER_LIMIT = 256 * 1024

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'PeopleControllerSpec',
        'micronaut.server.max-request-buffer-size': BUFFER_LIMIT
    ])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "decodes the body"() {
        when:
        HttpResponse<Person> response = client.toBlocking().exchange(
            HttpRequest.POST("/people", '{"name":"Fred","age":45}').contentType(MediaType.APPLICATION_JSON_TYPE),
            Person)

        then:
        response.status == HttpStatus.CREATED
        response.body() == new Person(name: "Fred", age: 45)
    }

    void "a malformed body is a bad request"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.POST("/people", '{"name":').contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    void "a body larger than the buffer limit is too large"() {
        given:
        String json = '{"name":"' + "x" * (4 * BUFFER_LIMIT) + '","age":45}'

        when:
        client.toBlocking().exchange(
            HttpRequest.POST("/people", json).contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.REQUEST_ENTITY_TOO_LARGE
    }

    void "imports the elements of a JSON array"() {
        given:
        String array = (0..<2000).collect { i -> '{"name":"' + "x" * 1000 + i + '","age":' + i + '}' }.join(",")
        array = "[" + array + "]"

        expect: "the limit applies to each element, not to the whole body"
        array.length() > 4 * BUFFER_LIMIT
        client.toBlocking().retrieve(
            HttpRequest.POST("/people/import", array).contentType(MediaType.APPLICATION_JSON_TYPE)) == "Imported 2000"
    }

    void "imports the elements of a JSON stream"() {
        given:
        String stream = '{"name":"Fred","age":45}\n{"name":"Wilma","age":40}\n'

        expect:
        client.toBlocking().retrieve(
            HttpRequest.POST("/people/import", stream).contentType(MediaType.APPLICATION_JSON_STREAM_TYPE)) == "Imported 2"
    }

    void "a filter reads a copy and the controller reads the body"() {
        expect:
        client.toBlocking().retrieve(
            HttpRequest.POST("/messages", "Hello Fred").contentType(MediaType.TEXT_PLAIN_TYPE)) == "Received Hello Fred"

        when:
        client.toBlocking().exchange(
            HttpRequest.POST("/messages", "Buy spam").contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    void "reads the body as text"() {
        expect:
        client.toBlocking().retrieve(
            HttpRequest.POST("/people/notes", "Call Fred").contentType(MediaType.TEXT_PLAIN_TYPE)) == "Received 9 characters"
    }
}
