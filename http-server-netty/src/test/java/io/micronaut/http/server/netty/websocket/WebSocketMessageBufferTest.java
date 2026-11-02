/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The buffer of a WebSocket message is valid while the message handler runs, and a message decoded by a codec is
 * decoded whole, not from its last fragment.
 */
class WebSocketMessageBufferTest {
    private static final String SPEC_NAME = "WebSocketMessageBufferTest";
    static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();

    private EmbeddedServer server;

    @BeforeEach
    void start() {
        RECEIVED.clear();
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", SPEC_NAME));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void byteBufParameterIsReadable() throws Exception {
        try (Socket socket = upgrade("/message-buffer/bytebuf")) {
            frame(socket.getOutputStream(), 0x82, "hello");
            Assertions.assertEquals("hello", RECEIVED.poll(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void codecDecodesASingleFrameMessage() throws Exception {
        try (Socket socket = upgrade("/message-buffer/codec")) {
            frame(socket.getOutputStream(), 0x81, "{\"name\":\"whole\"}");
            Assertions.assertEquals("whole", RECEIVED.poll(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void codecDecodesAllFragmentsOfAMessage() throws Exception {
        try (Socket socket = upgrade("/message-buffer/codec")) {
            frame(socket.getOutputStream(), 0x01, "{\"name\":");
            frame(socket.getOutputStream(), 0x80, "\"fragmented\"}");
            Assertions.assertEquals("fragmented", RECEIVED.poll(5, TimeUnit.SECONDS));
        }
    }

    private Socket upgrade(String path) throws Exception {
        Socket socket = new Socket("127.0.0.1", server.getPort());
        socket.setSoTimeout(5000);
        socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (!new String(out.toByteArray(), StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
            int b = in.read();
            Assertions.assertNotEquals(-1, b, "closed before the upgrade");
            out.write(b);
        }
        Assertions.assertTrue(new String(out.toByteArray(), StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101 "), out.toString("ISO-8859-1"));
        return socket;
    }

    private static void frame(OutputStream out, int firstByte, String payload) throws Exception {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        // masked with a zero key, payloads below 126 bytes
        out.write(firstByte);
        out.write(0x80 | data.length);
        out.write(new byte[4]);
        out.write(data);
        out.flush();
    }

    @Introspected
    public static class Named {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @ServerWebSocket("/message-buffer/bytebuf")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ByteBufWs {
        @OnMessage
        void message(ByteBuf message) {
            RECEIVED.add(message.toString(StandardCharsets.UTF_8));
        }
    }

    @ServerWebSocket("/message-buffer/codec")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CodecWs {
        @OnMessage
        void message(Named message) {
            RECEIVED.add(message.getName());
        }
    }
}
