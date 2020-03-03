package io.micronaut.http.client.docs.basics

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpRequestFactory
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/1919")
class WebdavControllerSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL())

    def "Simple get response without parameters should run fine"() {
        when:
        HttpRequest<String> request = HttpRequest.GET("/webdav")
        String response = client.toBlocking().retrieve(request)

        then:
        response == "GET easy"
    }

    def "Simple propfind response without parameters should run fine"() {
        when:
        HttpRequest<String> request = HttpRequestFactory.INSTANCE.create(HttpMethod.CUSTOM, "/webdav", "PROPFIND")
        String response = client.toBlocking().retrieve(request)

        then:
        response == "PROPFIND easy"
    }

    def "Simple proppatch response without parameters should run fine"() {
        when:
        HttpRequest<String> request = HttpRequestFactory.INSTANCE.create(HttpMethod.CUSTOM, "/webdav", "PROPPATCH")
        String response = client.toBlocking().retrieve(request)

        then:
        response == "PROPPATCH easy"
    }

    def "Simple propfind response with uri parameter should run fine"() {
        when:
        HttpRequest<String> request = HttpRequestFactory.INSTANCE.create(HttpMethod.CUSTOM, "/webdav/John", "PROPFIND")
        String response = client.toBlocking().retrieve(request)

        then:
        response == "PROPFIND John"
    }

    def "REPORT with uri parameter and parametrized response should run fine"() {
        when:
        HttpRequest<String> request = HttpRequestFactory.INSTANCE.create(HttpMethod.CUSTOM, "/webdav/John", "REPORT")
        Message response = client.toBlocking().retrieve(request, Message.class)

        then:
        response.text == "REPORT John"
    }

    def "LOCK with parametrized body and parametrized response should run fine"() {
        when:
        HttpRequest<Message> request = HttpRequestFactory.INSTANCE.create(HttpMethod.CUSTOM, "/webdav", "LOCK")
        Message response = client.toBlocking().retrieve(request.body(new Message("John")), Message.class)

        then:
        response.text == "LOCK John"
    }
}