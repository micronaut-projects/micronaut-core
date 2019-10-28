package io.micronaut.docs.server.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MessageEndpointSpec extends Specification {

    void "test read message endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['spec.name': MessageEndpointSpec.simpleName,
                 'endpoints.message.enabled': true])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/message", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "default message"

        cleanup:
        server.close()
    }

    void "test write message endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['spec.name': MessageEndpointSpec.simpleName,
                 'endpoints.message.enabled': true])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.POST("/message", [newMessage: "A new message"])
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED), String)
                .blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Message updated"
        response.contentType.get() == MediaType.TEXT_PLAIN_TYPE

        when:
        response = rxClient.exchange("/message", String).blockingFirst()

        then:
        response.body() == "A new message"

        cleanup:
        server.close()
    }

    void "test delete message endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['spec.name': MessageEndpointSpec.simpleName,
                 'endpoints.message.enabled': true])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.DELETE("/message"), String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Message deleted"

        when:
        rxClient.exchange("/message", String).blockingFirst()

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 404

        cleanup:
        server.close()
    }
}
