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
package io.micronaut.http.gateway;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The frames of RFC 6455, for a WebSocket client and server on plain sockets.
 */
final class WebSockets {

    static final int TEXT = 0x1;
    static final int BINARY = 0x2;
    static final int CLOSE = 0x8;
    static final int PING = 0x9;
    static final int PONG = 0xA;
    static final String KEY = "dGhlIHNhbXBsZSBub25jZQ==";

    private WebSockets() {
    }

    record Frame(int opcode, byte[] payload) {
        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }

        int closeCode() {
            return payload.length >= 2 ? ((payload[0] & 0xff) << 8) | (payload[1] & 0xff) : -1;
        }

        String closeReason() {
            return payload.length > 2 ? new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8) : "";
        }
    }

    static String accept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void write(OutputStream out, int opcode, byte[] payload, boolean mask) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        int maskBit = mask ? 0x80 : 0;
        if (payload.length < 126) {
            frame.write(maskBit | payload.length);
        } else if (payload.length < 65536) {
            frame.write(maskBit | 126);
            frame.write(payload.length >> 8);
            frame.write(payload.length);
        } else {
            frame.write(maskBit | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((long) payload.length >> (8 * i)));
            }
        }
        if (mask) {
            byte[] key = new byte[4];
            ThreadLocalRandom.current().nextBytes(key);
            frame.write(key);
            byte[] masked = payload.clone();
            for (int i = 0; i < masked.length; i++) {
                masked[i] ^= key[i % 4];
            }
            frame.write(masked);
        } else {
            frame.write(payload);
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    static void writeText(OutputStream out, String text, boolean mask) throws IOException {
        write(out, TEXT, text.getBytes(StandardCharsets.UTF_8), mask);
    }

    static void writeClose(OutputStream out, int code, String reason, boolean mask) throws IOException {
        byte[] r = reason.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + r.length];
        payload[0] = (byte) (code >> 8);
        payload[1] = (byte) code;
        System.arraycopy(r, 0, payload, 2, r.length);
        write(out, CLOSE, payload, mask);
    }

    static Frame read(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            throw new EOFException("The connection closed");
        }
        long length = b1 & 0x7f;
        if (length == 126) {
            length = ((in.read() & 0xff) << 8) | (in.read() & 0xff);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | (in.read() & 0xff);
            }
        }
        byte[] key = null;
        if ((b1 & 0x80) != 0) {
            key = in.readNBytes(4);
        }
        byte[] payload = in.readNBytes((int) length);
        if (payload.length < length) {
            throw new EOFException("The frame was cut");
        }
        if (key != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= key[i % 4];
            }
        }
        return new Frame(b0 & 0x0f, payload);
    }

    static String handshake(String path, String... headers) {
        StringBuilder request = new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\nHost: localhost\r\n");
        boolean connection = false;
        boolean upgrade = false;
        for (String header : headers) {
            request.append(header).append("\r\n");
            connection |= header.toLowerCase().startsWith("connection:");
            upgrade |= header.toLowerCase().startsWith("upgrade:");
        }
        if (!connection) {
            request.append("Connection: Upgrade\r\n");
        }
        if (!upgrade) {
            request.append("Upgrade: websocket\r\n");
        }
        request.append("Sec-WebSocket-Key: ").append(KEY).append("\r\nSec-WebSocket-Version: 13\r\n\r\n");
        return request.toString();
    }
}
