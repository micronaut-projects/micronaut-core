package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.epoll.Epoll
import io.netty.channel.kqueue.KQueue
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * A UNIX listener keeps its own default route exposure when a TCP listener does not expose them.
 */
class UnixListenerDefaultRoutesSpec extends Specification {

    @IgnoreIf({ !Epoll.isAvailable() && !KQueue.isAvailable() })
    void "a UNIX listener serves the default routes it exposes, and not those it does not, next to a restricted TCP listener"() {
        given:
        def dir = Files.createTempDirectory('unix-listener')
        Files.writeString(dir.resolve('file.txt'), 'STATIC-CONTENT-OK')
        Path open = dir.resolve('open.sock')
        Path closed = dir.resolve('closed.sock')
        int tcpPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'UnixListenerDefaultRoutesSpec',
                // UNIX domain sockets need a native transport
                'micronaut.netty.event-loops.default.prefer-native-transport': true,
                'micronaut.netty.event-loops.parent.prefer-native-transport': true,
                'micronaut.server.netty.listeners.tcp.port': tcpPort,
                'micronaut.server.netty.listeners.tcp.expose-default-routes': false,
                'micronaut.server.netty.listeners.open.family': 'UNIX',
                'micronaut.server.netty.listeners.open.path': open.toString(),
                'micronaut.server.netty.listeners.closed.family': 'UNIX',
                'micronaut.server.netty.listeners.closed.path': closed.toString(),
                'micronaut.server.netty.listeners.closed.expose-default-routes': false,
                'micronaut.router.static-resources.unix.paths': 'file:' + dir.toAbsolutePath(),
                'micronaut.router.static-resources.unix.mapping': '/assets/**',
        ])

        expect: 'the TCP listener does not expose the default routes'
        RestrictedListenerStaticResourceSpec.statusLine(tcpPort, '/unix-pub').contains(' 404 ')
        RestrictedListenerStaticResourceSpec.statusLine(tcpPort, '/assets/file.txt').contains(' 404 ')

        and: 'the unrestricted UNIX listener does'
        statusLine(open, '/unix-pub').contains(' 200 ')
        statusLine(open, '/assets/file.txt').contains(' 200 ')

        and: 'the restricted UNIX listener does not'
        statusLine(closed, '/unix-pub').contains(' 404 ')
        statusLine(closed, '/assets/file.txt').contains(' 404 ')

        cleanup:
        server?.close()
    }

    static String statusLine(Path socketPath, String path) {
        try (SocketChannel socket = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            def out = Channels.newOutputStream(socket)
            out.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            out.flush()
            return new String(Channels.newInputStream(socket).bytes, StandardCharsets.ISO_8859_1).readLines()[0]
        }
    }

    @Requires(property = 'spec.name', value = 'UnixListenerDefaultRoutesSpec')
    @Controller('/unix-pub')
    static class Pub {
        @Get
        String get() { 'pub' }
    }
}
