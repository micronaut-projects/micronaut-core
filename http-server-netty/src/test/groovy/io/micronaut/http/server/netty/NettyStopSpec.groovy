package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class NettyStopSpec extends Specification {

    /**
     * @return a port that is free right now
     */
    private static int freePort() {
        def socket = new ServerSocket(0)
        try {
            return socket.localPort
        } finally {
            socket.close()
        }
    }

    /**
     * @return {@code true} if a plain server socket can be bound to the given port right now
     */
    private static boolean canBind(int port) {
        new ServerSocket(port).close()
        return true
    }

    def 'can shutdown netty and application context'() {
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext

        when:
        server.stop()

        then:
        !ctx.running
    }

    def 'can shutdown netty and keep application context running'() {
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext

        when:
        server.stopServerOnly()

        then:
        ctx.running

        cleanup:
        ctx.stop()
    }

    def 'the port is free once stop returns'() {
        given:
        int port = freePort()
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'            : 'NettyStopSpec',
                'micronaut.server.port': port
        ])

        when:
        server.stop()

        then:
        canBind(port)
    }

    def 'the port is free once stop returns with a shared acceptor event loop group'() {
        given:
        int port = freePort()
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'NettyStopSpec',
                'micronaut.server.port'                        : port,
                'micronaut.netty.event-loops.parent.num-threads': 1
        ])
        def registry = server.applicationContext.getBean(EventLoopGroupRegistry)
        // the acceptor group comes from the registry, so it is not owned (and not shut down) by the server
        assert registry.getEventLoopGroup('parent').get().is(server.parentGroup)

        when:
        server.stop()

        then:
        canBind(port)
    }

    def 'the port is free once stopServerOnly returns with a shared acceptor event loop group'() {
        given:
        int port = freePort()
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'NettyStopSpec',
                'micronaut.server.port'                        : port,
                'micronaut.netty.event-loops.parent.num-threads': 1
        ])
        def ctx = server.applicationContext

        when:
        server.stopServerOnly()

        then:
        ctx.running
        canBind(port)

        cleanup:
        ctx.stop()
    }
}
