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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code micronaut.server.netty.max-connections} closes the connections over the limit as soon as
 * they are accepted, and a closed connection frees its place.
 */
class MaxConnectionsTest {
    private static final String SPEC = "MaxConnectionsTest";

    @Test
    void connectionsOverTheLimitAreClosed() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", SPEC,
            "micronaut.server.netty.max-connections", 1))) {
            try (Socket first = connect(server)) {
                Assertions.assertTrue(get(first).startsWith("HTTP/1.1 200"));
                try (Socket second = connect(server)) {
                    Assertions.assertTrue(isClosedByServer(second), "The second connection was served");
                }
            }
            // the place of the closed connection is free again
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (true) {
                try (Socket third = connect(server)) {
                    if (!isClosedByServer(third)) {
                        break;
                    }
                }
                Assertions.assertTrue(System.nanoTime() < deadline, "The closed connection did not free its place");
                Thread.sleep(20);
            }
        }
    }

    @Test
    void noLimitByDefault() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
             Socket first = connect(server);
             Socket second = connect(server);
             Socket third = connect(server)) {
            Assertions.assertTrue(get(first).startsWith("HTTP/1.1 200"));
            Assertions.assertTrue(get(second).startsWith("HTTP/1.1 200"));
            Assertions.assertTrue(get(third).startsWith("HTTP/1.1 200"));
        }
    }

    private static Socket connect(EmbeddedServer server) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort());
        socket.setSoTimeout(10_000);
        return socket;
    }

    /**
     * Send a request and read the start of the response, or {@code ""} if the connection was
     * closed.
     */
    private static String get(Socket socket) throws IOException {
        socket.getOutputStream().write("GET /max-connections HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[64];
        int read = in.read(buffer);
        return read <= 0 ? "" : new String(buffer, 0, read, StandardCharsets.US_ASCII);
    }

    private static boolean isClosedByServer(Socket socket) {
        try {
            return get(socket).isEmpty();
        } catch (SocketException e) {
            // reset
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    @Controller("/max-connections")
    @Requires(property = "spec.name", value = SPEC)
    static class OkController {
        @Get
        String ok() {
            return "ok";
        }
    }
}
