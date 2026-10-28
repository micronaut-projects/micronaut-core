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
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A pooled connection whose response body could not be decoded must not be reused: the rest of
 * the broken response would be read as the response of the next request.
 */
class BodyDecodeFailureConnectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Transfer-Encoding: chunked\r\n\r\n5\r\nhello\r\nZZ\r\n",
        "Content-Encoding: gzip\r\nTransfer-Encoding: chunked\r\n\r\nb\r\n\u001f\u008b\b\0\0\0\0\0\0\u0003ÿ\r\n"
    })
    void aBodyThatCannotBeDecodedIsNotReused(String response) throws Exception {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setEnabled(true);
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             HttpClient client = HttpClient.create(URI.create("http://127.0.0.1:" + server.getLocalPort()).toURL(), configuration)) {
            CompletableFuture<?> pending = Mono.from(client.exchange(HttpRequest.GET("/broken"), String.class)).toFuture();
            try (Socket socket = server.accept()) {
                readHead(socket.getInputStream());
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n" + response).getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
                Assertions.assertThrows(Exception.class, () -> pending.get(10, TimeUnit.SECONDS));

                CompletableFuture<?> next = Mono.from(client.exchange(HttpRequest.GET("/next"), String.class)).toFuture();
                Assertions.assertFalse(receivesMore(socket), "The connection of the broken response was reused");
                server.setSoTimeout(10000);
                try (Socket second = server.accept()) {
                    readHead(second.getInputStream());
                    second.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".getBytes(StandardCharsets.ISO_8859_1));
                    second.getOutputStream().flush();
                    next.get(10, TimeUnit.SECONDS);
                }
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

    private static boolean receivesMore(Socket socket) throws IOException {
        socket.setSoTimeout(2000);
        try {
            return socket.getInputStream().read() >= 0;
        } catch (SocketTimeoutException | SocketException e) {
            return false;
        }
    }
}
