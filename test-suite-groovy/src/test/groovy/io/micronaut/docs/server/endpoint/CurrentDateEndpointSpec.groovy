package io.micronaut.docs.server.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Issue
import spock.lang.Specification

import java.time.Instant

class CurrentDateEndpointSpec extends Specification {

    void "test read custom date endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/date", String).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        new Date(Instant.parse(((String)response.body()).replace('"','')).toEpochMilli()) != null

        cleanup:
        server.close()
    }

    void "test read custom date endpoint with argument"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/date/current_date_is", String).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().startsWith("current_date_is: ")

        cleanup:
        server.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/883")
    void "test read with produces"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/date/current_date_is", String).blockFirst()

        then:
        response.contentType.get() == MediaType.TEXT_PLAIN_TYPE

        cleanup:
        server.close()
    }

    void "test write custom date endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())
        Date originalDate, resetDate

        when:
        def response = rxClient.exchange("/date", String).blockFirst()
        originalDate = new Date(Instant.parse(((String)response.body()).replace('"','')).toEpochMilli())

        and:
        response = rxClient.exchange(HttpRequest.POST("/date", [:]), String).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Current date reset"

        when:
        response = rxClient.exchange("/date", String).blockFirst()
        resetDate = new Date(Instant.parse(((String)response.body()).replace('"','')).toEpochMilli())

        then:
        resetDate > originalDate

        cleanup:
        server.close()
    }

    void "test disable endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['custom.date.enabled':false])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        rxClient.exchange("/date", String).blockFirst()

        then:
        HttpClientResponseException ex = thrown()
        ex.response.code() == HttpStatus.NOT_FOUND.code

        cleanup:
        server.close()
    }
}
