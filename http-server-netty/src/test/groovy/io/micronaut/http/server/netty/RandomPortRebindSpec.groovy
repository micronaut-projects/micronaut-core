package io.micronaut.http.server.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.NetUtil
import org.junit.jupiter.api.Assumptions
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.channels.ServerSocketChannel
import java.util.function.IntPredicate

/**
 * The parts of the shadowed random port handling that do not depend on the operating system.
 * {@link RandomPortLoopbackShadowSpec} covers the whole server on macOS.
 */
class RandomPortRebindSpec extends Specification {

    @AutoCleanup('shutdownGracefully')
    EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())

    List<Channel> channels = []

    def cleanup() {
        channels*.close()*.syncUninterruptibly()
    }

    void "a random port is bound again while it is taken"() {
        given:
        List<Integer> checkedPorts = []
        IntPredicate portTaken = { int port ->
            checkedPorts << port
            checkedPorts.size() <= 2
        }

        when:
        ChannelFuture future = NettyHttpServer.bindRandomWildcardPort(bootstrap(), portTaken)
        channels << future.channel()

        then: 'the two taken ports were released, and the third bind is kept'
        checkedPorts.size() == 3
        future.channel().open
        ((InetSocketAddress) future.channel().localAddress()).port == checkedPorts[2]
        checkedPorts[0..1].every { canBind(it) }
    }

    void "the port is kept after the maximum number of attempts"() {
        given:
        int checks = 0
        IntPredicate portTaken = { int port ->
            checks++
            true
        }

        when:
        ChannelFuture future = NettyHttpServer.bindRandomWildcardPort(bootstrap(), portTaken)
        channels << future.channel()

        then: 'the last attempt is not checked'
        checks == 9
        future.channel().open
    }

    void "a port is not taken when nothing listens on it at a loopback address"() {
        given:
        ServerSocketChannel socket = ServerSocketChannel.open()
        socket.bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
        int port = ((InetSocketAddress) socket.localAddress).port
        socket.close()

        expect:
        !NettyHttpServer.isLoopbackPortTaken(port)
    }

    void "a port is taken when another socket listens on it at #address"() {
        given:
        ServerSocketChannel socket = ServerSocketChannel.open()
        try {
            socket.bind(new InetSocketAddress(address, 0))
        } catch (IOException | UnsupportedOperationException e) {
            socket.close()
            Assumptions.abort("$address is not available: $e")
        }
        int port = ((InetSocketAddress) socket.localAddress).port

        expect:
        NettyHttpServer.isLoopbackPortTaken(port)

        cleanup:
        socket.close()

        where:
        address << [NetUtil.LOCALHOST4, NetUtil.LOCALHOST6]
    }

    private ServerBootstrap bootstrap() {
        new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new ChannelInboundHandlerAdapter())
    }

    private static boolean canBind(int port) {
        ServerSocketChannel socket = ServerSocketChannel.open()
        try {
            socket.bind(new InetSocketAddress(port))
            return true
        } catch (IOException ignored) {
            return false
        } finally {
            socket.close()
        }
    }
}
