package io.micronaut.docs.server.routes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IssuesControllerSpec extends Specification{
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void "/issues/show/{number} with an invalid Integer number responds 400"() {
        when:
        client.toBlocking().exchange("/issues/hello")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 400
    }

    void "/issues/show/{number} without number responds 404"() {
        when:
        client.toBlocking().exchange("/issues/")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 404
    }
}
