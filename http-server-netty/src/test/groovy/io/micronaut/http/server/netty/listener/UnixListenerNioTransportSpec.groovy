package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import io.netty.channel.socket.nio.NioDomainSocketChannel
import io.netty.channel.socket.nio.NioServerDomainSocketChannel
import jakarta.inject.Singleton
import spock.lang.IgnoreIf
import spock.lang.Requires as RequiresIf
import spock.lang.Specification

import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * UNIX listeners on the nio transport, which bind through the JDK domain socket support and need
 * no native transport.
 */
class UnixListenerNioTransportSpec extends Specification {

    @RequiresIf({ Boolean.getBoolean('micronaut.test.no-native-transport') })
    void "the native transport classes are not on the classpath"() {
        when:
        Class.forName('io.netty.channel.unix.DomainSocketAddress')

        then:
        thrown(ClassNotFoundException)

        when:
        Class.forName('io.netty.channel.epoll.Epoll')

        then:
        thrown(ClassNotFoundException)

        when:
        Class.forName('io.netty.channel.kqueue.KQueue')

        then:
        thrown(ClassNotFoundException)
    }

    @IgnoreIf({ os.windows })
    void "a UNIX listener on the nio transport serves requests over JDK domain sockets"() {
        given:
        Path dir = shortTempDir()
        Path socket = dir.resolve('server.sock')
        ApplicationContext ctx = ApplicationContext.run(properties(socket))
        EmbeddedServer server = ctx.getBean(EmbeddedServer)
        server.start()

        when:
        List<String> response = request(socket, '/unix-nio')

        then:
        response[0].contains(' 200 ')
        response.last() == 'nio'

        and: 'the listener and its connections are the nio channels, not native ones'
        def channels = ctx.getBean(ChannelRecorder)
        channels.listeners.any { it instanceof NioServerDomainSocketChannel }
        channels.connections.any { it instanceof NioDomainSocketChannel }

        cleanup:
        ctx?.close()
        Files.deleteIfExists(socket)
        Files.deleteIfExists(dir)
    }

    @IgnoreIf({ os.windows })
    void "a UNIX listener on the nio transport can bind the same path again after a restart"() {
        given:
        Path dir = shortTempDir()
        Path socket = dir.resolve('server.sock')

        when:
        ApplicationContext first = ApplicationContext.run(properties(socket))
        first.getBean(EmbeddedServer).start()

        then:
        Files.exists(socket)
        request(socket, '/unix-nio').last() == 'nio'

        when:
        first.close()

        then: 'the socket file goes away with the listener'
        !Files.exists(socket)

        when:
        ApplicationContext second = ApplicationContext.run(properties(socket))
        second.getBean(EmbeddedServer).start()

        then:
        request(socket, '/unix-nio').last() == 'nio'

        cleanup:
        first?.close()
        second?.close()
        Files.deleteIfExists(socket)
        Files.deleteIfExists(dir)
    }

    @IgnoreIf({ os.windows })
    void "a socket file left behind by a server that did not close is removed before bind"() {
        given: 'a socket file nothing listens on, as after kill -9'
        Path dir = shortTempDir()
        Path socket = dir.resolve('server.sock')
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).withCloseable {
            it.bind(UnixDomainSocketAddress.of(socket))
        }
        assert Files.exists(socket)

        when:
        ApplicationContext ctx = ApplicationContext.run(properties(socket))
        ctx.getBean(EmbeddedServer).start()

        then:
        request(socket, '/unix-nio').last() == 'nio'

        cleanup:
        ctx?.close()
        Files.deleteIfExists(socket)
        Files.deleteIfExists(dir)
    }

    @IgnoreIf({ os.windows })
    void "a socket another server still listens on is not taken over"() {
        given:
        Path dir = shortTempDir()
        Path socket = dir.resolve('server.sock')
        ServerSocketChannel live = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        live.bind(UnixDomainSocketAddress.of(socket))
        ApplicationContext ctx = ApplicationContext.run(properties(socket))

        when:
        ctx.getBean(EmbeddedServer).start()

        then:
        thrown(ServerStartupException)

        and: 'the other server keeps its socket'
        Files.exists(socket)
        SocketChannel.open(UnixDomainSocketAddress.of(socket)).close() == null

        cleanup:
        ctx?.close()
        live?.close()
        Files.deleteIfExists(socket)
        Files.deleteIfExists(dir)
    }

    @IgnoreIf({ os.windows })
    void "a regular file at the socket path is not removed"() {
        given:
        Path dir = shortTempDir()
        Path socket = dir.resolve('server.sock')
        Files.writeString(socket, 'not a socket')
        ApplicationContext ctx = ApplicationContext.run(properties(socket))

        when:
        ctx.getBean(EmbeddedServer).start()

        then:
        thrown(ServerStartupException)
        Files.readString(socket) == 'not a socket'

        cleanup:
        ctx?.close()
        Files.deleteIfExists(socket)
        Files.deleteIfExists(dir)
    }

    void "an abstract UNIX socket on the nio transport fails with a clear message"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                                    : 'UnixListenerNioTransportSpec',
                'micronaut.netty.event-loops.default.transport': 'nio',
                'micronaut.netty.event-loops.parent.transport' : 'nio',
                'micronaut.server.netty.listeners.abs.family'  : 'UNIX',
                'micronaut.server.netty.listeners.abs.path'    : '\0micronaut-nio-abstract',
        ])

        when:
        ctx.getBean(EmbeddedServer).start()

        then:
        def e = thrown(ServerStartupException)
        def cause = causes(e).find { it instanceof ConfigurationException }
        cause != null
        cause.message.contains("Listener 'abs'")
        cause.message.contains('abstract domain socket')

        cleanup:
        ctx?.close()
    }

    private static Map<String, Object> properties(Path socket) {
        return [
                'spec.name'                                    : 'UnixListenerNioTransportSpec',
                'micronaut.netty.event-loops.default.transport': 'nio',
                'micronaut.netty.event-loops.parent.transport' : 'nio',
                'micronaut.server.netty.listeners.unix.family' : 'UNIX',
                'micronaut.server.netty.listeners.unix.path'   : socket.toString(),
        ]
    }

    /**
     * Domain socket paths are limited to about 100 bytes, and the default temp dir on macOS is
     * already long, so prefer /tmp.
     */
    private static Path shortTempDir() {
        Path tmp = Path.of('/tmp')
        return Files.isDirectory(tmp) && Files.isWritable(tmp) ? Files.createTempDirectory(tmp, 'mn') : Files.createTempDirectory('mn')
    }

    private static List<Throwable> causes(Throwable t) {
        List<Throwable> all = []
        while (t != null && !all.contains(t)) {
            all << t
            t = t.cause
        }
        return all
    }

    private static List<String> request(Path socketPath, String path) {
        try (SocketChannel socket = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            def out = Channels.newOutputStream(socket)
            out.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            out.flush()
            // a blocking channel read has no timeout of its own: closing the channel ends it
            def read = CompletableFuture.supplyAsync { Channels.newInputStream(socket).readAllBytes() }
            return new String(read.get(10, TimeUnit.SECONDS), StandardCharsets.ISO_8859_1).readLines()
        }
    }

    @Requires(property = 'spec.name', value = 'UnixListenerNioTransportSpec')
    @Controller('/unix-nio')
    static class Hello {
        @Get
        String get() { 'nio' }
    }

    @Requires(property = 'spec.name', value = 'UnixListenerNioTransportSpec')
    @Singleton
    static class ChannelRecorder implements BeanCreatedEventListener<NettyServerCustomizer.Registry>, NettyServerCustomizer {
        final List<Channel> listeners = new CopyOnWriteArrayList<>()
        final List<Channel> connections = new CopyOnWriteArrayList<>()

        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(this)
            return event.bean
        }

        @Override
        NettyServerCustomizer specializeForChannel(Channel channel, NettyServerCustomizer.ChannelRole role) {
            if (role == NettyServerCustomizer.ChannelRole.LISTENER) {
                listeners << channel
            } else if (role == NettyServerCustomizer.ChannelRole.CONNECTION) {
                connections << channel
            }
            return this
        }
    }
}
