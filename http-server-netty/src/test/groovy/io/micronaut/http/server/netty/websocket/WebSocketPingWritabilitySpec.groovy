package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.netty.websocket.WebSocketSessionRepository
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.Channel
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A ping is not answered while the connection is not writable, so a peer that does not read
 * cannot make the server queue pongs without bound.
 */
class WebSocketPingWritabilitySpec extends Specification {

    void 'a ping is not answered while the channel is not writable'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'WebSocketPingWritabilitySpec', 'micronaut.server.port': -1])
        EmbeddedServer server = ctx.getBean(EmbeddedServer)
        server.start()
        PingServer bean = ctx.getBean(PingServer)

        Socket socket = new Socket('127.0.0.1', server.port)
        socket.soTimeout = 10_000
        OutputStream out = socket.outputStream
        DataInputStream input = new DataInputStream(socket.inputStream)
        out.write(("GET /ping-writability HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
        out.flush()
        StringBuilder head = new StringBuilder()
        while (!head.toString().endsWith('\r\n\r\n')) {
            head.append((char) input.read())
        }
        assert head.toString().startsWith('HTTP/1.1 101')

        new PollingConditions(timeout: 10).eventually {
            assert ((WebSocketSessionRepository) server).channelGroup.size() == 1
        }
        Channel channel = ((WebSocketSessionRepository) server).channelGroup.find()

        when: 'a ping arrives while the channel is not writable'
        setWritable(channel, false)
        out.write(frame(0x9, 'a'))
        out.write(frame(0x1, 'x'))
        out.flush()

        then: 'it is read'
        bean.messages.poll(10, TimeUnit.SECONDS) == 'x'

        when: 'a ping arrives once the channel is writable again'
        setWritable(channel, true)
        out.write(frame(0x9, 'b'))
        out.flush()

        then: 'only that ping is answered'
        input.readUnsignedByte() == 0x8A
        int length = input.readUnsignedByte()
        byte[] payload = new byte[length]
        input.readFully(payload)
        new String(payload, StandardCharsets.US_ASCII) == 'b'

        cleanup:
        socket?.close()
        ctx?.close()
    }

    private static void setWritable(Channel channel, boolean writable) {
        channel.eventLoop().submit {
            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, writable)
        }.get(10, TimeUnit.SECONDS)
    }

    private static byte[] frame(int opcode, String text) {
        // a final, masked client frame with an all-zero mask
        byte[] payload = text.getBytes(StandardCharsets.US_ASCII)
        ByteArrayOutputStream frame = new ByteArrayOutputStream()
        frame.write(0x80 | opcode)
        frame.write(0x80 | payload.length)
        frame.write(new byte[4])
        frame.write(payload)
        return frame.toByteArray()
    }

    @ServerWebSocket('/ping-writability')
    @Requires(property = 'spec.name', value = 'WebSocketPingWritabilitySpec')
    static class PingServer {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>()

        @OnMessage
        void message(String m) {
            messages.add(m)
        }
    }
}
