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
 * A route with an explicit port is served by the listener of that port only, and a listener
 * without a port (a UNIX domain socket) answers it with a 404 rather than failing.
 */
class ExplicitPortRouteListenerSpec extends Specification {

    void "an explicit port route is matched by the listener port and not served on a UNIX listener"() {
        given:
        def dir = Files.createTempDirectory('explicit-port')
        Path unix = dir.resolve('unix.sock')
        int routePort = SocketUtils.findAvailableTcpPort()
        int otherPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                               : 'ExplicitPortRouteListenerSpec',
                'spec.route-port'                         : routePort,
                'micronaut.server.netty.listeners.a.port' : routePort,
                'micronaut.server.netty.listeners.b.port' : otherPort,
                'micronaut.server.netty.listeners.u.family': 'UNIX',
                'micronaut.server.netty.listeners.u.path' : unix.toString(),
        ])

        expect:
        RestrictedListenerStaticResourceSpec.statusLine(routePort, '/explicit-port').contains(' 200 ')
        RestrictedListenerStaticResourceSpec.statusLine(otherPort, '/explicit-port').contains(' 404 ')
        UnixListenerDefaultRoutesSpec.statusLine(unix, '/explicit-port').contains(' 404 ')

        cleanup:
        server?.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ExplicitPortRouteListenerSpec')
    static class Target {
        @Executable
        String get() { 'port' }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ExplicitPortRouteListenerSpec')
    static class Routes extends DefaultRouteBuilder {
        Routes(ExecutionHandleLocator locator, @Value('${spec.route-port}') int port) {
            super(locator)
            GET('/explicit-port', Target, 'get').exposedPort(port)
        }
    }
}
