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
package io.micronaut.http.client.netty;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class BodyDecodeFailureConnectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Transfer-Encoding: chunked\r\n\r\n5\r\nhello\r\nZZ\r\n",
        "Content-Encoding: gzip\r\nTransfer-Encoding: chunked\r\n\r\nb\r\n\u001f\u008b\b\0\0\0\0\0\0\u0003ÿ\r\n"
    })
    void aBodyThatCannotBeDecodedClosesTheConnection(String response) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             HttpClient client = HttpClient.create(URI.create("http://127.0.0.1:" + server.getLocalPort()).toURL())) {
            CompletableFuture<?> pending = Mono.from(client.exchange(HttpRequest.GET("/broken"), String.class)).toFuture();
            try (Socket socket = server.accept()) {
                readHead(socket.getInputStream());
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n" + response).getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
                Assertions.assertThrows(Exception.class, () -> pending.get(10, TimeUnit.SECONDS));
                Assertions.assertTrue(closedWithin(socket, 5000), "The client kept the connection of the broken response open");
            }
        }
    }

    private static void readHead(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (!sb.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("closed");
            }
            sb.append((char) b);
        }
    }

    private static boolean closedWithin(Socket socket, int millis) throws IOException {
        socket.setSoTimeout(millis);
        long end = System.currentTimeMillis() + millis;
        InputStream in = socket.getInputStream();
        byte[] buf = new byte[1024];
        try {
            while (System.currentTimeMillis() < end) {
                if (in.read(buf) < 0) {
                    return true;
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (java.net.SocketException e) {
            return true;
        }
        return false;
    }
}
