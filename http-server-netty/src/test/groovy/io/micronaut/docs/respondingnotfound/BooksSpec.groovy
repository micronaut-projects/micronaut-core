package io.micronaut.docs.respondingnotfound

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BooksSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'respondingnotfound'
    ], "test")

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "returning null returns 404"() {
        when:
        rxClient.toBlocking().exchange(HttpRequest.GET('/books/stock/XXXXX'))

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND
    }


    def "returning Maybe.empty returns 404"() {
        when:
        rxClient.toBlocking().exchange(HttpRequest.GET('/books/maybestock/XXXXX'))

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND
    }

    def "returning Single.never returns 404"() {
        when:
        rxClient.toBlocking().exchange(HttpRequest.GET('/books/singlestock/XXXXX'))

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND
    }
}
