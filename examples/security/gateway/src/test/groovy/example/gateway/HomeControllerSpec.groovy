package example.gateway

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HomeControllerSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void '/ allows anonymous access'() {
        when:
        HttpResponse rsp = client.toBlocking().exchange("/")

        then:
        rsp.status.code == 200
    }

    void 'urls not mapped in InterceptUrlMap return UNAUTHORIZED if they attempted to be accessed'() {
        when:
        client.toBlocking().exchange("/notInInterceptUrlMap")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }
}
