package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * The drive-by-localhost guard must recognise every loopback spelling of the Host header and must not
 * be switched off by forwarding headers that any client can send.
 */
class CorsLocalhostHostFormsSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                   : 'CorsLocalhostHostFormsSpec',
            'micronaut.server.cors.enabled': true,
    ])

    void "a cross-origin simple request is denied for loopback Host #host"(String host, Map<String, String> extraHeaders) {
        given:
        AtomicInteger counter = server.applicationContext.getBean(StateController).counter
        int before = counter.get()

        when:
        String status = post(host, 'http://evil.example', extraHeaders)

        then:
        status.contains(' 403 ')
        counter.get() == before

        where:
        host                                  | extraHeaders
        "localhost:PORT"                      | [:]
        "127.0.0.1:PORT"                      | [:]
        "[::1]:PORT"                          | [:]
        "[0:0:0:0:0:0:0:1]:PORT"              | [:]
        "[::ffff:127.0.0.1]:PORT"             | [:]
        "evil.localhost:PORT"                 | [:]
        "LOCALHOST:PORT"                      | [:]
        "localhost.:PORT"                     | [:]
        "0.0.0.0:PORT"                        | [:]
        "localhost:PORT"                      | ['X-Forwarded-Host': 'public.example.com']
        "localhost:PORT"                      | ['Forwarded': 'host=public.example.com;proto=https']
        "[::1]:PORT"                          | ['X-Forwarded-Host': 'public.example.com']
    }

    void "a request from a loopback origin is still allowed for Host #host"(String host, String origin) {
        when:
        String status = post(host, origin, [:])

        then:
        status.contains(' 200 ')

        where:
        host                     | origin
        "localhost:PORT"         | 'http://localhost:3000'
        "[::1]:PORT"             | 'http://localhost:3000'
        "[::1]:PORT"             | 'http://[::1]:3000'
        "localhost:PORT"         | 'http://[::1]:3000'
    }

    void "a cross-origin request to a non-local Host is not denied"() {
        expect:
        post("public.example.com", 'http://evil.example', [:]).contains(' 200 ')
    }

    private String post(String host, String origin, Map<String, String> extraHeaders) {
        String body = 'x'
        StringBuilder request = new StringBuilder()
        request.append("POST /cors-host-forms/state HTTP/1.1\r\n")
        request.append("Host: ").append(host.replace('PORT', String.valueOf(server.port))).append("\r\n")
        request.append("Origin: ").append(origin).append("\r\n")
        extraHeaders.each { k, v -> request.append(k).append(": ").append(v).append("\r\n") }
        request.append("Content-Type: text/plain\r\n")
        request.append("Content-Length: ").append(body.length()).append("\r\n")
        request.append("Connection: close\r\n\r\n")
        request.append(body)
        Socket socket = new Socket("127.0.0.1", server.port)
        try {
            socket.soTimeout = 10_000
            socket.outputStream.write(request.toString().getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII))
            return reader.readLine()
        } finally {
            socket.close()
        }
    }

    @Requires(property = 'spec.name', value = 'CorsLocalhostHostFormsSpec')
    @Controller('/cors-host-forms')
    static class StateController {
        final AtomicInteger counter = new AtomicInteger()

        @Post('/state')
        @Consumes(MediaType.TEXT_PLAIN)
        String state(@Body String body) {
            counter.incrementAndGet()
            return 'ok'
        }
    }
}
