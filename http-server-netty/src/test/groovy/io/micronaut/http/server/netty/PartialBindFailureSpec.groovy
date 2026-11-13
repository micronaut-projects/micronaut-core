package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class PartialBindFailureSpec extends Specification {

    def 'a listener that was bound is closed when a later listener fails to bind'() {
        given:
        int freePort = SocketUtils.findAvailableTcpPort()
        // taken, so the listener for it cannot bind
        def taken = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())

        when:
        ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                : 'PartialBindFailureSpec',
                'micronaut.server.netty.listeners.a.host'  : '127.0.0.1',
                'micronaut.server.netty.listeners.a.port'  : freePort,
                'micronaut.server.netty.listeners.b.host'  : '127.0.0.1',
                'micronaut.server.netty.listeners.b.port'  : taken.localPort,
        ])
        then:
        thrown(Exception)

        when: 'the port of the listener that did bind is free again when the start has failed'
        def again = new ServerSocket()
        again.reuseAddress = false
        again.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), freePort))
        then:
        again.bound

        cleanup:
        again?.close()
        taken.close()
    }
}
