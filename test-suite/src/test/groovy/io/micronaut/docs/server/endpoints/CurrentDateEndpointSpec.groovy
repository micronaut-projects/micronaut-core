package io.micronaut.docs.server.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification


class CurrentDateEndpointSpec extends Specification {

    void "test read custom date endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/date", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        new Date(response.body().toLong()) != null

        cleanup:
        server.close()
    }

    void "test read custom date endpoint with argument"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/date/current_date_is", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().startsWith("current_date_is: ")

        cleanup:
        server.close()
    }

    void "test write custom date endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [:])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())
        Date originalDate, resetDate

        when:
        def response = rxClient.exchange("/date", String).blockingFirst()
        originalDate = new Date(response.body().toLong())

        and:
        response = rxClient.exchange(HttpRequest.POST("/date", [:]), String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Current date reset"

        when:
        response = rxClient.exchange("/date", String).blockingFirst()
        resetDate = new Date(response.body().toLong())

        then:
        resetDate > originalDate

        cleanup:
        server.close()
    }

    void "test disable endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['custom.date.enabled':false])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        rxClient.exchange("/date", String).blockingFirst()

        then:
        HttpClientResponseException ex = thrown()
        ex.response.code() == HttpStatus.NOT_FOUND.code

        cleanup:
        server.close()
    }
}
