package io.micronaut.docs.consumes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConsumesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'consumesspec'
    ], "test")

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "@Consumes allow you to control which media type is accepted"() {
        given:
        def book = new Book(title: "The Stand", pages: 1000)

        when:
        HttpRequest request = HttpRequest.POST("/test", book)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        thrown(HttpClientResponseException)

        when:
        request = HttpRequest.POST("/test", book)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()

        when:
        request = HttpRequest.POST("/test/multipleConsumes", book)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()

        when:
        request = HttpRequest.POST("/test/multipleConsumes", book)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON_TYPE)
        rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()
    }

    static class Book {
        String title
        Integer pages
    }
}
