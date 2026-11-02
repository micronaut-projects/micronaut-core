package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.DefaultRouteBuilder
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * A route exposed on an explicit port is matched by the port of the listener, which a UNIX
 * listener does not have: it is not served there, and does not fail.
 */
class UnixListenerExplicitPortRouteSpec extends Specification {

    void "a route exposed on the TCP port is not served by a UNIX listener"() {
        given:
        def dir = Files.createTempDirectory('unix-port-route')
        Path socket = dir.resolve('port-route.sock')
        int tcpPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                : 'UnixListenerExplicitPortRouteSpec',
                'port-only.port'                           : tcpPort,
                'micronaut.server.netty.listeners.tcp.port': tcpPort,
                'micronaut.server.netty.listeners.unix.family': 'UNIX',
                'micronaut.server.netty.listeners.unix.path': socket.toString(),
        ])

        expect: 'the TCP listener serves the route'
        RestrictedListenerStaticResourceSpec.statusLine(tcpPort, '/port-only').contains(' 200 ')

        and: 'the UNIX listener does not'
        UnixListenerDefaultRoutesSpec.statusLine(socket, '/port-only').contains(' 404 ')

        cleanup:
        server?.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'UnixListenerExplicitPortRouteSpec')
    static class PortOnly {
        @Executable
        String get() {
            'port-only'
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'UnixListenerExplicitPortRouteSpec')
    static class PortRoutes extends DefaultRouteBuilder {
        PortRoutes(ExecutionHandleLocator locator, @Value('${port-only.port}') int port) {
            super(locator)
            GET('/port-only', PortOnly, 'get').exposedPort(port)
        }
    }
}
