package io.micronaut.docs.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MdcServiceSpec extends Specification {

    void "test MDC propagation"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['mdc.example.service.enabled': true])
        HttpClient client = HttpClient.create(embeddedServer.URL)

        when:
        String response = client.toBlocking().retrieve(HttpRequest.GET('/mdc/test'))

        then:
        response.startsWith('New user id: ')
        response.endsWith(' name: Denis')

        cleanup:
        client.close()
        embeddedServer.close()
    }
}
