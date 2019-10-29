package io.micronaut.http.client

import io.micronaut.http.client.exceptions.HttpClientException
import spock.lang.Specification

class ClientHostNameSpec extends Specification {

    void "test host name with underscores"() {
        when:
        def client = HttpClient.create(new URL("https://foo_bar"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connect Error: foo_bar')

        cleanup:
        client.close()
    }


    void "test host name with underscores and port"() {
        when:
        def client = HttpClient.create(new URL("https://foo_bar:8080"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connect Error: foo_bar')

        cleanup:
        client.close()
    }
}
