package io.micronaut.docs.server.routes

import io.micronaut.context.ApplicationContext;

// tag::imports[]

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

// end::imports[]

// tag::startclass[]
class IssuesControllerTest extends Specification {

    @Shared
    @AutoCleanup // <2>
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer) // <1>

    @Shared
    @AutoCleanup // <2>
    HttpClient client = HttpClient.create(embeddedServer.URL) // <1>
    // end::startclass[]

    // tag::normal[]
    void "test issue"() {
        when:
        String body = client.toBlocking().retrieve("/issues/12") // <3>

        then:
        body != null
        body == "Issue # 12!" // <4>
    }

    void "test issue from id"() {
        when:
        String body = client.toBlocking().retrieve("/issues/issue/13")

        then:
        body != null
        body == "Issue # 13!" // <5>
    }

    void "/issues/{number} with an invalid Integer number responds 400"() {
        when:
        client.toBlocking().exchange("/issues/hello")

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 400 // <6>
    }

    void "/issues/{number} without number responds 404"() {
        when:
        client.toBlocking().exchange("/issues/")

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 404 // <7>
    }
    // end::normal[]

    // tag::defaultvalue[]
    void "test default issue"() {
        when:
        String body = client.toBlocking().retrieve("/issues/default")

        then:
        body != null
        body == "Issue # 0!" // <1>
    }

    void "test not default issue"() {
        when:
        String body = client.toBlocking().retrieve("/issues/default/1")

        then:
        body != null
        body == "Issue # 1!" // <2>
    }
    // end::defaultvalue[]

// tag::endclass[]
}
// end::endclass[]
