/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

/**
 * A {@code permessage-deflate} frame inflates on the server before the {@code maxPayloadLength} of the
 * route is checked. The compression handler must bound that inflation, so a small compressed frame can
 * not inflate to an arbitrarily large buffer.
 */
class WebSocketCompressionBombSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run([
            'spec.name': 'WebSocketCompressionBombSpec',
    ])
    @Shared EmbeddedServer server = ctx.getBean(EmbeddedServer).start()

    void "a compressed frame that inflates past the route limit is rejected during inflation"() {
        given:
        // 64 KiB of one byte compresses to a few dozen bytes and inflates past the 1 KiB route limit
        byte[] frame = textFrame(deflate(('a' * 65536).getBytes(StandardCharsets.US_ASCII)))

        when:
        int closeCode = upgradeThenSend(frame)

        then:
        // the inflation is capped by the compression handler and fails with a DecompressionException,
        // which closes the session as an internal error (1011). Without the cap the frame inflates
        // fully and is only then rejected by the maxPayloadLength check as message-too-big (1009)
        closeCode == 1011
    }

    /**
     * Deflate {@code data} as a {@code permessage-deflate} message payload: a raw deflate block flushed
     * with {@code SYNC_FLUSH}, with the trailing {@code 00 00 FF FF} removed (the server appends it back).
     */
    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(data)
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        byte[] buffer = new byte[8192]
        int n
        while ((n = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)) > 0) {
            out.write(buffer, 0, n)
            if (n < buffer.length) {
                break
            }
        }
        deflater.end()
        byte[] deflated = out.toByteArray()
        // strip the trailing empty stored block 00 00 FF FF that SYNC_FLUSH leaves
        return Arrays.copyOf(deflated, deflated.length - 4)
    }

    /** A masked, compressed (RSV1) final text frame, as a client sends. */
    private static byte[] textFrame(byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        out.write(0x80 | 0x40 | 0x01) // FIN, RSV1 (compressed), opcode TEXT
        byte[] mask = [0x12, 0x34, 0x56, 0x78] as byte[]
        if (payload.length < 126) {
            out.write(0x80 | payload.length) // MASK bit, 7-bit length
        } else {
            out.write(0x80 | 126)
            out.write((payload.length >> 8) & 0xff)
            out.write(payload.length & 0xff)
        }
        out.write(mask)
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ mask[i % 4])
        }
        return out.toByteArray()
    }

    /**
     * Open a websocket, send {@code frame}, and return the status code of the close frame the server
     * answers with.
     */
    private int upgradeThenSend(byte[] frame) {
        new Socket(server.host, server.port).withCloseable { Socket socket ->
            socket.soTimeout = 10_000
            socket.outputStream.write(("GET /compression-bomb/ws HTTP/1.1\r\n" +
                    "Host: ${server.host}:${server.port}\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "Sec-WebSocket-Extensions: permessage-deflate\r\n" +
                    "\r\n").getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()

            InputStream input = socket.inputStream
            readUntilEndOfHead(input)
            socket.outputStream.write(frame)
            socket.outputStream.flush()
            return readCloseCode(input)
        }
    }

    private static void readUntilEndOfHead(InputStream input) {
        StringBuilder head = new StringBuilder()
        int c
        while ((c = input.read()) != -1) {
            head.append((char) c)
            if (head.toString().endsWith('\r\n\r\n')) {
                return
            }
        }
        throw new IllegalStateException("upgrade response head not received")
    }

    /** Read websocket frames until a close frame (opcode 0x8) and return its 2-byte status code. */
    private static int readCloseCode(InputStream input) {
        while (true) {
            int b0 = input.read()
            if (b0 == -1) {
                throw new IllegalStateException("connection closed without a close frame")
            }
            int opcode = b0 & 0x0f
            int b1 = input.read()
            int len = b1 & 0x7f
            if (len == 126) {
                len = (input.read() << 8) | input.read()
            } else if (len == 127) {
                len = 0
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | input.read()
                }
            }
            byte[] payload = new byte[len]
            int read = 0
            while (read < len) {
                int r = input.read(payload, read, len - read)
                if (r == -1) {
                    throw new IllegalStateException("truncated frame")
                }
                read += r
            }
            if (opcode == 0x8) {
                return len >= 2 ? ((payload[0] & 0xff) << 8) | (payload[1] & 0xff) : -1
            }
        }
    }

    @ServerWebSocket('/compression-bomb/ws')
    @Requires(property = 'spec.name', value = 'WebSocketCompressionBombSpec')
    static class LimitedWebSocket {
        @OnMessage(maxPayloadLength = 1024)
        String onMessage(String message) {
            return message
        }
    }
}
