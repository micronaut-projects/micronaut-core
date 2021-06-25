package io.micronaut.docs.writable

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class WritableSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient, embeddedServer.getURL())


    void "test render template"() {
        expect:
        client.toBlocking().retrieve('/template/welcome') == 'Dear Fred Flintstone. Nice to meet you.'
    }

    void "test the correct headers are applied"() {
        when:
        HttpResponse response = client.toBlocking().exchange('/template/welcome', String)

        then:
        response.getHeaders().contains("Date")
        response.getHeaders().contains("Content-Length")
    }

}
