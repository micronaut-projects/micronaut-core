package io.micronaut.http.server.netty.configuration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonBlocking
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ListenerEventLoopSpec extends Specification {
    def 'listeners borrow named worker and parent groups and survive restart'(String secondGroup) {
        given:
        def context = ApplicationContext.run([
            'spec.name': 'ListenerEventLoopSpec',
            'micronaut.netty.event-loops.one.num-threads': 1,
            'micronaut.netty.event-loops.two.num-threads': 1,
            'micronaut.netty.event-loops.accept-one.num-threads': 1,
            'micronaut.netty.event-loops.accept-two.num-threads': 1,
            'micronaut.server.netty.listeners.a.port': -1,
            'micronaut.server.netty.listeners.a.event-loop-group': 'one',
            'micronaut.server.netty.listeners.a.parent-event-loop-group': 'accept-one',
            'micronaut.server.netty.listeners.b.port': -1,
            'micronaut.server.netty.listeners.b.event-loop-group': secondGroup,
            'micronaut.server.netty.listeners.b.parent-event-loop-group': 'accept-two',
        ])
        NettyEmbeddedServer server = context.getBean(NettyEmbeddedServer)
        def registry = context.getBean(EventLoopGroupRegistry)
        def first = registry.getEventLoopGroup('one').orElseThrow()
        def second = registry.getEventLoopGroup('two').orElseThrow()

        expect:
        2.times {
            server.start()
            def threads = server.boundPorts.collect { port ->
                HttpClient.create(new URL("http://localhost:$port")).withCloseable { it.toBlocking().retrieve('/listener-thread') }
            }
            assert threads.any { it.startsWith('one-') }
            assert threads.any { it.startsWith(secondGroup + '-') }
            server.stopServerOnly()
            assert !first.isShuttingDown()
            assert !second.isShuttingDown()
        }

        cleanup:
        context.close()

        where:
        secondGroup << ['one', 'two']
    }

    def 'unknown listener group reports listener and group'() {
        when:
        ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.netty.listeners.custom.port': -1,
            'micronaut.server.netty.listeners.custom.event-loop-group': 'missing',
        ])

        then:
        def e = thrown(ServerStartupException)
        e.message.contains('custom')
        e.cause.message.contains('missing')
    }

    def 'bind failure closes listeners that already bound'() {
        given:
        int firstPort = io.micronaut.core.io.socket.SocketUtils.findAvailableTcpPort()
        def occupied = new ServerSocket(0)

        when:
        ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.netty.listeners.a.port': firstPort,
            'micronaut.server.netty.listeners.b.port': occupied.localPort,
        ])

        then:
        thrown(ServerStartupException)
        new ServerSocket(firstPort).withCloseable { it.bound }

        cleanup:
        occupied.close()
    }

    def 'TCP and QUIC listeners use their own named workers'() {
        given:
        int tcpPort = io.micronaut.core.io.socket.SocketUtils.findAvailableTcpPort()
        int quicPort = io.micronaut.core.io.socket.SocketUtils.findAvailableTcpPort()
        def server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ListenerEventLoopSpec',
            'micronaut.netty.event-loops.tcp.num-threads': 1,
            'micronaut.netty.event-loops.udp.num-threads': 1,
            'micronaut.server.ssl.enabled': true,
            'micronaut.server.ssl.build-self-signed': true,
            'micronaut.server.netty.listeners.tcp.port': tcpPort,
            'micronaut.server.netty.listeners.tcp.event-loop-group': 'tcp',
            'micronaut.server.netty.listeners.quic.port': quicPort,
            'micronaut.server.netty.listeners.quic.family': 'QUIC',
            'micronaut.server.netty.listeners.quic.ssl': true,
            'micronaut.server.netty.listeners.quic.event-loop-group': 'udp',
            'micronaut.http.client.alpn-modes': 'h3',
            'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def tcp = HttpClient.create(new URL("http://localhost:$tcpPort"))
        def quic = server.applicationContext.createBean(HttpClient, new URL("https://localhost:$quicPort"))

        expect:
        tcp.toBlocking().retrieve('/listener-thread').startsWith('tcp-')
        quic.toBlocking().retrieve('/listener-thread').startsWith('udp-')

        cleanup:
        tcp?.close()
        quic?.close()
        server?.close()
    }

    @Controller('/listener-thread')
    @Requires(property = 'spec.name', value = 'ListenerEventLoopSpec')
    static class ThreadController {
        @Get
        @NonBlocking
        String thread() { Thread.currentThread().name }
    }
}
