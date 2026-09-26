package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.Channel
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateDecoder
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateEncoder
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

/**
 * The websocket compression handler negotiates {@code permessage-deflate}. It must only be part of
 * a pipeline that is actually upgraded to a websocket, not of every HTTP/1 pipeline.
 */
class WebSocketCompressionHandlerPlacementSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run([
            'spec.name': 'WebSocketCompressionHandlerPlacementSpec',
    ])
    @Shared EmbeddedServer server = ctx.getBean(EmbeddedServer).start()
    @Shared PipelineCapture capture = ctx.getBean(PipelineCapture)

    void "plain HTTP pipelines do not carry the websocket compression handler"() {
        when:
        String response = exchange("GET /compression-placement HTTP/1.1\r\n" +
                "Host: ${server.host}:${server.port}\r\n" +
                "Connection: close\r\n" +
                "\r\n")
        ChannelPipeline pipeline = capture.pipelines.last()

        then:
        response.startsWith('HTTP/1.1 200 OK')
        response.endsWith('plain')
        pipeline.get(WebSocketServerCompressionHandler) == null
        pipeline.get(NettyServerWebSocketUpgradeHandler.COMPRESSION_HANDLER) == null
        pipeline.get(PerMessageDeflateEncoder) == null
        pipeline.get(PerMessageDeflateDecoder) == null
    }

    void "an upgrade with permessage-deflate negotiates compression"() {
        when:
        Map<String, Boolean> handlers = null
        String response = exchange(upgradeRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")) {
            ChannelPipeline pipeline = capture.pipelines.last()
            // the compression handler replaces itself with the deflate codecs once the 101 has been written
            new PollingConditions(timeout: 5).eventually {
                assert pipeline.get(PerMessageDeflateEncoder) != null
            }
            handlers = snapshot(pipeline)
        }

        then:
        response.startsWith('HTTP/1.1 101')
        response.toLowerCase().contains('sec-websocket-extensions: permessage-deflate')
        handlers == [
                compression: false,
                deflateEncoder: true,
                deflateDecoder: true,
                websocketHandler: true,
                sink: false,
        ]
    }

    void "an upgrade without extensions does not install compression"() {
        when:
        Map<String, Boolean> handlers = null
        String response = exchange(upgradeRequest("")) {
            ChannelPipeline pipeline = capture.pipelines.last()
            new PollingConditions(timeout: 5).eventually {
                assert pipeline.get(WebSocketServerCompressionHandler) == null
            }
            handlers = snapshot(pipeline)
        }

        then:
        response.startsWith('HTTP/1.1 101')
        !response.toLowerCase().contains('sec-websocket-extensions')
        handlers == [
                compression: false,
                deflateEncoder: false,
                deflateDecoder: false,
                websocketHandler: true,
                sink: false,
        ]
    }

    private static Map<String, Boolean> snapshot(ChannelPipeline pipeline) {
        return [
                compression: pipeline.get(WebSocketServerCompressionHandler) != null,
                deflateEncoder: pipeline.get(PerMessageDeflateEncoder) != null,
                deflateDecoder: pipeline.get(PerMessageDeflateDecoder) != null,
                websocketHandler: pipeline.get(NettyServerWebSocketHandler.ID) != null,
                sink: pipeline.get('websocket-upgrade-request-sink') != null,
        ]
    }

    private String upgradeRequest(String extraHeaders) {
        return "GET /compression-placement/ws HTTP/1.1\r\n" +
                "Host: ${server.host}:${server.port}\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                extraHeaders +
                "\r\n"
    }

    /**
     * Send the request and read the response: up to the end of the head for an upgrade, to EOF otherwise.
     * {@code whileOpen} runs before the connection is closed again.
     */
    private String exchange(String request, Closure<?> whileOpen = {}) {
        return new Socket(server.host, server.port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.write(request.getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            StringBuilder response = new StringBuilder()
            int c
            while ((c = socket.inputStream.read()) != -1) {
                response.append((char) c)
                if (response.toString().startsWith('HTTP/1.1 101') && response.toString().endsWith('\r\n\r\n')) {
                    break
                }
            }
            whileOpen.call()
            return response.toString()
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'WebSocketCompressionHandlerPlacementSpec')
    static class PipelineCapture implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        List<ChannelPipeline> pipelines = Collections.synchronizedList(new ArrayList<>())

        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(new Customizer(null))
            return event.bean
        }

        class Customizer implements NettyServerCustomizer {
            final Channel channel

            Customizer(Channel channel) {
                this.channel = channel
            }

            @Override
            NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                return new Customizer(channel)
            }

            @Override
            void onStreamPipelineBuilt() {
                pipelines.add(channel.pipeline())
            }
        }
    }

    @Controller('/compression-placement')
    @Requires(property = 'spec.name', value = 'WebSocketCompressionHandlerPlacementSpec')
    static class PlainController {
        @Get(produces = 'text/plain')
        String get() {
            return 'plain'
        }
    }

    @ServerWebSocket('/compression-placement/ws')
    @Requires(property = 'spec.name', value = 'WebSocketCompressionHandlerPlacementSpec')
    static class EchoWebSocket {
        @OnMessage
        String onMessage(String message) {
            return message
        }
    }
}
