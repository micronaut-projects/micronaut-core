package io.micronaut.http.server.netty.path

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

class PathVariableDecodingSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'PathVariableDecodingSpec'])

    @Unroll
    void "a single-segment variable never receives a decoded separator: #path"(String path) {
        when:
        String response = raw(path)

        then: 'either refused, or the bound value still satisfies the template'
        !response.contains('var=[../../etc/passwd]')
        !response.contains('var=[a\\b]')

        where:
        path << ['/pv/seg/..%2F..%2Fetc%2Fpasswd', '/pv/re/..%2F..%2Fetc%2Fpasswd', '/pv/re/a%5Cb']
    }

    void "a literal plus in a path variable is not decoded to a space"() {
        expect:
        raw('/pv/re2/a+b').contains('var=[a+b]')
    }

    private String raw(String path) {
        try (Socket socket = new Socket('127.0.0.1', server.port)) {
            socket.soTimeout = 5000
            socket.outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            return new String(socket.inputStream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }

    @Requires(property = 'spec.name', value = 'PathVariableDecodingSpec')
    @Controller('/pv')
    static class Ctrl {
        @Get('/seg/{name}')
        String seg(@PathVariable String name) { "var=[$name]" }

        @Get('/re/{name:[^/\\\\]+}')
        String re(@PathVariable String name) { "var=[$name]" }

        @Get('/re2/{name:.+}')
        String re2(@PathVariable String name) { "var=[$name]" }
    }
}
