package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class RestrictedListenerStaticResourceSpec extends Specification {

    void "static resources are not served on a listener that does not expose default routes"() {
        given:
        def dir = Files.createTempDirectory('restricted-static')
        Files.write(dir.resolve('file.txt'), 'STATIC-CONTENT-OK'.bytes)
        int ported = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'RestrictedListenerStaticResourceSpec',
                'restricted.port': ported,
                'micronaut.router.static-resources.restricted.paths': 'file:' + dir.toAbsolutePath(),
                'micronaut.router.static-resources.restricted.mapping': '/assets/**',
        ])

        expect: 'the default controller route is gated on the ported listener'
        statusLine(ported, '/restricted-pub').contains(' 404 ')
        statusLine(ported, '/restricted-mgmt').contains(' 200 ')
        statusLine(server.port, '/assets/file.txt').contains(' 200 ')

        and: 'so are static resources'
        statusLine(ported, '/assets/file.txt').contains(' 404 ')

        cleanup:
        server?.close()
    }

    static String statusLine(int port, String path) {
        try (Socket socket = new Socket('127.0.0.1', port)) {
            socket.soTimeout = 5000
            socket.outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            return new String(socket.inputStream.bytes, StandardCharsets.ISO_8859_1).readLines()[0]
        }
    }

    @Requires(property = 'spec.name', value = 'RestrictedListenerStaticResourceSpec')
    @Controller('/restricted-pub')
    static class Pub {
        @Get
        String get() { 'pub' }
    }

    @Requires(property = 'spec.name', value = 'RestrictedListenerStaticResourceSpec')
    @Controller(value = '/restricted-mgmt', port = '${restricted.port}')
    static class Mgmt {
        @Get
        String get() { 'mgmt' }
    }
}
