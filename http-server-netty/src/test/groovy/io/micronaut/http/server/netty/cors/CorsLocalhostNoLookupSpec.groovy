package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The localhost guard runs on the event loop for every request with an Origin, so a bracketed
 * Host that is not an IPv6 literal must not be resolved as a name.
 */
class CorsLocalhostNoLookupSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CorsLocalhostNoLookupSpec'])

    void "a bracketed non-literal #header is not looked up"(String header, String name) {
        when:
        request(header)

        then:
        !RecordingInetAddressResolverProvider.LOOKUPS.contains(name)

        where:
        header                                  | name
        'Host: [zz:1]:8080'                     | 'zz:1'
        'X-Forwarded-Host: [g::1]'              | 'g::1'
        'Host: [lookup-me.example:1]'           | 'lookup-me.example:1'
    }

    private void request(String header) {
        StringBuilder request = new StringBuilder("GET /cors-no-lookup HTTP/1.1\r\n")
        if (!header.startsWith('Host:')) {
            request.append("Host: localhost\r\n")
        }
        request.append(header).append("\r\n")
        request.append("Origin: http://evil.example\r\n")
        request.append("Connection: close\r\n\r\n")
        try (Socket socket = new Socket("127.0.0.1", server.port)) {
            socket.soTimeout = 10_000
            socket.outputStream.write(request.toString().getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            socket.inputStream.readAllBytes()
        }
    }
}
