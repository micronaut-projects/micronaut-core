package io.micronaut.http.server.netty.path

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * An authority-form request target (RFC 9112 section 3.2.3) is valid for CONNECT only.
 */
class ConnectAuthorityFormSpec extends Specification {

    void "a CONNECT request with an authority-form target is not rejected as an invalid target"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ConnectAuthorityFormSpec'])

        when:
        String response = RawHttp.send(server.port, "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nConnection: close\r\n\r\n")

        then:
        RawHttp.statusLine(response) == 'HTTP/1.1 404 Not Found'

        cleanup:
        server?.close()
    }

    void "an authority-form target is rejected for other methods"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ConnectAuthorityFormSpec'])

        when:
        String response = RawHttp.send(server.port, "GET example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nConnection: close\r\n\r\n")

        then:
        RawHttp.statusLine(response).startsWith('HTTP/1.1 400')

        cleanup:
        server?.close()
    }
}
