package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ErrorRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "ErrorRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a global error route"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/orders/5"))

        then:
        HttpClientResponseException error = thrown()
        error.status == HttpStatus.NOT_FOUND
        body(error) == [error: "No order 5"]
    }

    void "a global status route"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/nothing/here"))

        then:
        HttpClientResponseException error = thrown()
        error.status == HttpStatus.NOT_FOUND
        body(error) == [error: "Nothing at /nothing/here"]
    }

    void "the error routes of a group are local to its routes"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.POST("/checkout/2", "")) == "ordered 2"

        when:
        http.retrieve(HttpRequest.POST("/checkout/0", ""))

        then:
        HttpClientResponseException invalid = thrown()
        invalid.status == HttpStatus.BAD_REQUEST
        body(invalid) == [invalid: "quantity must be positive"]

        when:
        http.retrieve(HttpRequest.POST("/checkout/11", ""))

        then:
        HttpClientResponseException conflict = thrown()
        conflict.status == HttpStatus.CONFLICT
        body(conflict) == [conflict: "not enough stock"]

        when: 'the error route of the group does not answer for a route outside of it'
        http.retrieve(HttpRequest.GET("/pricing/0"))

        then:
        HttpClientResponseException outside = thrown()
        outside.status == HttpStatus.INTERNAL_SERVER_ERROR
    }

    private static Map<String, String> body(HttpClientResponseException error) {
        return error.response.getBody(Argument.mapOf(String, String)).orElseThrow()
    }
}
