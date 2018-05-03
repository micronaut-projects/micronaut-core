package io.micronaut.docs.produces

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ProducesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'producesspec'
    ], "test")

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "@Produces allow you to change the Media Type of the response"() {

        when:
        HttpResponse rsp = rxClient.toBlocking().exchange(HttpRequest.GET('/test'))

        then:
        rsp.status() == HttpStatus.OK
        rsp.getContentType().isPresent() && rsp.getContentType().get() == MediaType.APPLICATION_JSON_TYPE

        when:
        rsp = rxClient.toBlocking().exchange(HttpRequest.GET('/test/html'))

        then:
        rsp.status() == HttpStatus.OK
        rsp.getContentType().isPresent() && rsp.getContentType().get() == MediaType.TEXT_HTML_TYPE
    }
}
