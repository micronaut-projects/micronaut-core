package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.http.netty.channel.NettyChannelType
import io.micronaut.http.netty.channel.NioEventLoopGroupFactory
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.NetUtil
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.nio.channels.ServerSocketChannel
import java.util.concurrent.atomic.AtomicInteger

/**
 * On macOS, a dual-stack wildcard socket that binds port 0 can get a port that another socket
 * already listens on at a loopback address. Requests to {@code localhost} then go to that other
 * listener and fail with "Connection closed before response was received" or "Connection reset".
 * The kernel only does this occasionally, so this spec makes the first random port bind of the
 * server land on a port that is shadowed like that. Linux refuses such a bind, which is also why
 * the race does not happen there.
 */
@IgnoreIf({ !os.macOs })
class RandomPortLoopbackShadowSpec extends Specification {

    static final AtomicInteger NEXT_RANDOM_PORT = new AtomicInteger()

    void "a random port that is shadowed by a loopback listener is not used"() {
        given: 'another socket that listens on a loopback port'
        ServerSocketChannel shadow = ServerSocketChannel.open()
        shadow.bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
        int shadowedPort = ((InetSocketAddress) shadow.localAddress).port

        and: 'the first random port bind of the server lands on that port'
        NEXT_RANDOM_PORT.set(shadowedPort)

        when:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                  : 'RandomPortLoopbackShadowSpec',
                'micronaut.netty.event-loops.default.transport': 'nio',
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        then: 'the shadowed port was assigned, and the server bound again'
        NEXT_RANDOM_PORT.get() == 0
        server.port != shadowedPort

        and: 'requests to localhost reach the server'
        client.toBlocking().retrieve('/random-port-shadow') == 'ok'

        cleanup:
        NEXT_RANDOM_PORT.set(0)
        client?.close()
        server?.close()
        shadow.close()
    }

    @Requires(property = 'spec.name', value = 'RandomPortLoopbackShadowSpec')
    @Controller('/random-port-shadow')
    static class ShadowController {
        @Get
        String index() {
            'ok'
        }
    }

    @Requires(property = 'spec.name', value = 'RandomPortLoopbackShadowSpec')
    @Replaces(NioEventLoopGroupFactory)
    @Named(NioEventLoopGroupFactory.NAME)
    @Singleton
    static class ShadowingNioEventLoopGroupFactory extends NioEventLoopGroupFactory {
        @Override
        Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
            if (type == NettyChannelType.SERVER_SOCKET) {
                return new ShadowedServerSocketChannel()
            }
            return super.channelInstance(type, configuration)
        }
    }

    /**
     * Binds a port 0 request to {@link #NEXT_RANDOM_PORT} once, the way the kernel assigns a
     * shadowed port.
     */
    static class ShadowedServerSocketChannel extends NioServerSocketChannel {
        @Override
        protected void doBind(SocketAddress localAddress) throws Exception {
            InetSocketAddress address = (InetSocketAddress) localAddress
            int port = address.port == 0 ? NEXT_RANDOM_PORT.getAndSet(0) : 0
            super.doBind(port == 0 ? localAddress : new InetSocketAddress(address.address, port))
        }
    }
}
