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
        e.message.contains('Connect Error:') && e.message.contains('foo_bar')

        cleanup:
        client.close()
    }

    void "test host name with underscores and port"() {
        when:
        def client = HttpClient.create(new URL("https://foo_bar:8080"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        // messages can be different for different locales in the OS, can't compare whole string
        e.message.contains('Connect Error:') && e.message.contains('foo_bar')

        cleanup:
        client.close()
    }

    void "test host name with dots and dashes and port"() {
        when:
        def client = HttpClient.create(new URL("https://slave1-6x8-build-agent-2.0.1-5h7sl:8080"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connect Error:') && e.message.contains('slave1-6x8-build-agent-2.0.1-5h7sl')

        cleanup:
        client.close()
    }

}
