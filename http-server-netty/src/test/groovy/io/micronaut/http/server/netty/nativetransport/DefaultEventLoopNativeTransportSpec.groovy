package io.micronaut.http.server.netty.nativetransport

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.IoEventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollIoHandler
import io.netty.channel.nio.NioIoHandler
import spock.lang.Requires
import spock.lang.Specification

class DefaultEventLoopNativeTransportSpec extends Specification {

    static boolean ioUringAvailable() {
        try {
            return (boolean) Class.forName('io.netty.channel.uring.IoUring').getMethod('isAvailable').invoke(null)
        } catch (Throwable ignored) {
            return false
        }
    }

    private static String request(EmbeddedServer server) {
        HttpClient client = HttpClient.create(server.URL)
        try {
            return client.toBlocking().retrieve(HttpRequest.GET('/native-transport'))
        } finally {
            client.close()
        }
    }

    @Requires({ Epoll.isAvailable() })
    void 'a native transport selected only for the default event loop is used by the acceptor too'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec'                                         : 'TransportSpec',
                'micronaut.netty.event-loops.default.transport': 'epoll'
        ])

        expect:
        request(server) == 'works'
        ((IoEventLoopGroup) ((NettyHttpServer) server).parentGroup).isIoType(EpollIoHandler)

        cleanup:
        server?.close()
    }

    @Requires({ Epoll.isAvailable() })
    void 'the legacy prefer-native-transport flag on the default event loop also applies to the acceptor'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec'                                                       : 'TransportSpec',
                'micronaut.netty.event-loops.default.prefer-native-transport': true
        ])

        expect:
        request(server) == 'works'
        !((IoEventLoopGroup) ((NettyHttpServer) server).parentGroup).isIoType(NioIoHandler)

        cleanup:
        server?.close()
    }

    @Requires({ DefaultEventLoopNativeTransportSpec.ioUringAvailable() })
    void 'io_uring selected only for the default event loop works'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec'                                         : 'TransportSpec',
                'micronaut.netty.event-loops.default.transport': 'io_uring'
        ])

        expect:
        request(server) == 'works'

        cleanup:
        server?.close()
    }

    @Requires({ Epoll.isAvailable() })
    void 'an explicitly incompatible acceptor transport fails with a clear message'() {
        when:
        ApplicationContext.run(EmbeddedServer, [
                'spec'                                         : 'TransportSpec',
                'micronaut.netty.event-loops.default.transport': 'epoll',
                'micronaut.server.netty.parent.transport'      : 'nio'
        ])

        then:
        Throwable e = thrown()
        Throwable cause = e
        while (cause != null && !(cause instanceof IllegalStateException && cause.message?.contains('micronaut.server.netty.parent.transport'))) {
            cause = cause.cause
        }
        cause != null
        cause.message.contains('micronaut.netty.event-loops.default.transport')
    }
}
