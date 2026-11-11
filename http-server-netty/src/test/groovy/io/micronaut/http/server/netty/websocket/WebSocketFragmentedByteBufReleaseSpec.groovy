package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A fragmented message to a ByteBuf @OnMessage handler with a codec-less media type
 * is never released.
 */
class WebSocketFragmentedByteBufReleaseSpec extends Specification {

    void "fragmented binary messages to a ByteBuf handler are released"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketFragmentedByteBufReleaseSpec'])
        HttpClient httpClient = HttpClient.newHttpClient()
        BufServer bean = server.applicationContext.getBean(BufServer)
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/fragmented-bytebuf"), new WebSocket.Listener() {})
                .get(10, TimeUnit.SECONDS)

        when:
        10.times {
            ws.sendBinary(ByteBuffer.wrap(new byte[1000]), false).get(10, TimeUnit.SECONDS)
            ws.sendBinary(ByteBuffer.wrap(new byte[1000]), true).get(10, TimeUnit.SECONDS)
        }

        then:
        new PollingConditions(timeout: 10).eventually {
            assert bean.received.size() == 10
        }
        sleep(500)
        bean.received.collect { it.refCnt() } == [0] * 10

        cleanup:
        bean?.received?.each { if (it.refCnt() > 0) it.release(it.refCnt()) }
        ws?.abort()
        httpClient?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'WebSocketFragmentedByteBufReleaseSpec')
    @ServerWebSocket("/fragmented-bytebuf")
    static class BufServer {
        final List<ByteBuf> received = new CopyOnWriteArrayList<>()

        @OnMessage
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        void onMessage(ByteBuf body) {
            received.add(body)
        }
    }
}
