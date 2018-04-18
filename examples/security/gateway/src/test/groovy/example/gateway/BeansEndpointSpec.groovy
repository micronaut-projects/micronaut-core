package example.gateway

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BeansEndpointSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'attempt to access /beans without authenticating and server responds UNAUTHORIZED'() {
        when: 'attempt to access /beans without authenticating'
        client.toBlocking().exchange("/beans")

        then: 'server responds UNAUTHORIZED'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }
}
