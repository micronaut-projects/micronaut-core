package org.particleframework.docs.server.routes

import org.particleframework.context.ApplicationContext
import org.particleframework.http.client.HttpClient
import org.particleframework.http.client.exceptions.HttpClientResponseException
import org.particleframework.runtime.server.EmbeddedServer
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
        client.toBlocking().exchange("/issues/show/hello")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 400
    }

    void "/issues/show/{number} without number responds 404"() {
        when:
        client.toBlocking().exchange("/issues/show/")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 404
    }
}
