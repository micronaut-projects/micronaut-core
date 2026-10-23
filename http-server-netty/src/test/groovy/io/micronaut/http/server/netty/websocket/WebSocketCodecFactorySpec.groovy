package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.netty.compression.NettyZlibCodecFactory
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibDecoder
import io.netty.handler.codec.compression.ZlibEncoder
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ConcurrentLinkedQueue

class WebSocketCodecFactorySpec extends Specification {
    def 'provider receives endpoint limits for each #secure connection with compression #compression'() {
        given:
        Map options = [
            'spec.name': 'WebSocketCodecFactorySpec',
            'micronaut.http.client.ws.compression.enabled': compression,
        ]
        if (secure) {
            options += [
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port': 0,
                'micronaut.server.ssl.build-self-signed': true,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
            ]
        }
        def server = ApplicationContext.run(EmbeddedServer, options)
        def client = server.applicationContext.createBean(WebSocketClient, server.URI)
        def factory = server.applicationContext.getBean(CountingFactory)
        String message = 'websocket codec provider '.repeat(128)

        expect:
        2.times {
            def endpoint = Flux.from(client.connect(EchoClient, '/codec-ws')).blockFirst()
            try {
                endpoint.session.sendSync(message)
                new PollingConditions(timeout: 10).eventually {
                    assert endpoint.messages.poll() == message
                }
            } finally {
                endpoint.close()
            }
        }
        factory.serverLimits == [12345, 12345]
        factory.clientLimits == (compression ? [23456, 23456] : [])
        factory.serverHandlers.size() == 2
        !factory.serverHandlers[0].is(factory.serverHandlers[1])

        cleanup:
        client?.close()
        server?.close()

        where:
        [secure, compression] << [[false, true], [false, true]].combinations()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'WebSocketCodecFactorySpec')
    static class CountingFactory implements NettyZlibCodecFactory {
        final List<Integer> serverLimits = Collections.synchronizedList(new ArrayList<>())
        final List<Integer> clientLimits = Collections.synchronizedList(new ArrayList<>())
        final List<WebSocketServerExtensionHandler> serverHandlers = Collections.synchronizedList(new ArrayList<>())

        @Override
        ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int level, int windowBits, int memLevel) {
            ZlibCodecFactory.newZlibEncoder(wrapper, level, windowBits, memLevel)
        }

        @Override
        ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
            ZlibCodecFactory.newZlibDecoder(wrapper, maxAllocation)
        }

        @Override
        WebSocketServerExtensionHandler newWebSocketServerCompressionHandler(int maxMessageBytes) {
            serverLimits.add(maxMessageBytes)
            def handler = new WebSocketServerCompressionHandler()
            serverHandlers.add(handler)
            handler
        }

        @Override
        WebSocketClientExtensionHandler newWebSocketClientCompressionHandler(int maxMessageBytes) {
            clientLimits.add(maxMessageBytes)
            new WebSocketClientCompressionHandler(0)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketCodecFactorySpec')
    @ServerWebSocket('/codec-ws')
    static class EchoServer {
        @OnMessage(maxPayloadLength = 12345)
        void onMessage(String message, WebSocketSession session) {
            session.sendSync(message)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketCodecFactorySpec')
    @ClientWebSocket
    static abstract class EchoClient implements AutoCloseable {
        final Queue<String> messages = new ConcurrentLinkedQueue<>()
        WebSocketSession session

        @OnOpen
        void onOpen(WebSocketSession session) { this.session = session }

        @OnMessage(maxPayloadLength = 23456)
        void onMessage(String message) { messages.add(message) }
    }
}
