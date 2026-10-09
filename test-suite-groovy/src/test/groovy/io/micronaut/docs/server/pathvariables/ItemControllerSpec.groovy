package io.micronaut.docs.server.pathvariables

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ItemControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ItemControllerSpec'])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "reads the variables of the route"() {
        expect:
        get("/items/5") == "Item 5, page 1"
        get("/items/5/3") == "Item 5, page 3"
    }

    void "reads the values of a list variable"() {
        expect:
        get("/tags/red,green") == "Tags [red, green]"
        get("/sum/1,2,3") == "Sum 6"
    }

    void "a value that does not convert is a bad request"() {
        when:
        get("/items/abc")

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    void "a filter reads the variables of the route"() {
        when:
        get("/items/7")

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.GONE
    }

    private String get(String uri) {
        client.toBlocking().retrieve(HttpRequest.GET(uri))
    }
}
