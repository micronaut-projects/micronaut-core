package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.ApplicationConfiguration
import spock.lang.Specification

class PartialBindFailureSpec extends Specification {

    private static final String LOOPBACK = '127.0.0.1'

    def 'a listener that was bound is closed when a later listener fails to bind'() {
        given: 'a server that is not the default one, so it shares the event loops of the context and does not shut them down'
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'PartialBindFailureSpec'])
        int freePort = SocketUtils.findAvailableTcpPort()
        // taken, so the second listener cannot bind
        def taken = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        def config = new NettyHttpServerConfiguration(ctx.getBean(ApplicationConfiguration))
        config.listeners = [
                NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp(LOOPBACK, freePort, false, null, null),
                NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp(LOOPBACK, taken.localPort, false, null, null),
        ]
        NettyEmbeddedServer server = ctx.getBean(NettyEmbeddedServerFactory).build(config)

        when:
        server.start()
        then:
        thrown(Exception)
        !server.running

        when: 'the port of the listener that did bind is free again once the start has failed'
        def again = new ServerSocket()
        again.reuseAddress = false
        again.bind(new InetSocketAddress(InetAddress.getByName(LOOPBACK), freePort))
        then:
        again.bound

        cleanup:
        again?.close()
        taken.close()
        ctx.close()
    }
}
