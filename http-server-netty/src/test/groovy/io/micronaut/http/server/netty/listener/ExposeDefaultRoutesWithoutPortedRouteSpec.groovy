package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ExposeDefaultRoutesWithoutPortedRouteSpec extends Specification {

    void "expose-default-routes=false applies when no route declares a port"() {
        given:
        int main = SocketUtils.findAvailableTcpPort()
        int restricted = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ExposeDefaultRoutesWithoutPortedRouteSpec',
                'micronaut.server.netty.listeners.main.port': main,
                'micronaut.server.netty.listeners.restricted.port': restricted,
                'micronaut.server.netty.listeners.restricted.expose-default-routes': false,
        ])

        expect:
        RestrictedListenerStaticResourceSpec.statusLine(main, '/unported-pub').contains(' 200 ')
        RestrictedListenerStaticResourceSpec.statusLine(restricted, '/unported-pub').contains(' 404 ')

        cleanup:
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'ExposeDefaultRoutesWithoutPortedRouteSpec')
    @Controller('/unported-pub')
    static class Pub {
        @Get
        String get() { 'pub' }
    }
}
