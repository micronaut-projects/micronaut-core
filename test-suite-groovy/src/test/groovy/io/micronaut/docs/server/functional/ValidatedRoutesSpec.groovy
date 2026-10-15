package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ValidatedRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "ValidatedRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the bean method validates the body"() {
        expect:
        client.toBlocking().exchange(HttpRequest.POST("/products", [name: "lamp"])).status == HttpStatus.CREATED

        when:
        client.toBlocking().exchange(HttpRequest.POST("/products", [name: ""]))

        then:
        HttpClientResponseException invalid = thrown()
        invalid.status == HttpStatus.BAD_REQUEST
    }
}
