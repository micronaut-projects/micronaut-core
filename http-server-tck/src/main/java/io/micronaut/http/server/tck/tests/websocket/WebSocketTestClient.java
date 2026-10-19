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
package io.micronaut.http.server.tck.tests.websocket;

import io.micronaut.http.tck.ServerUnderTest;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Connects to the server under test with the JDK WebSocket client, so that the TCK does not
 * depend on a Micronaut WebSocket client. Over TLS it trusts the self-signed certificate of the
 * server under test.
 */
final class WebSocketTestClient implements AutoCloseable {

    /**
     * How long a test waits for a message, a close or a handshake, in seconds.
     */
    static final long TIMEOUT = 10;

    private final HttpClient http;
    private final URI base;
    private final URI httpBase;

    WebSocketTestClient(ServerUnderTest server) {
        URL url = server.getURL().orElseThrow();
        boolean secure = "https".equals(url.getProtocol());
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (secure) {
            builder.sslContext(trustAll());
        }
        http = builder.build();
        base = URI.create((secure ? "wss" : "ws") + "://localhost:" + url.getPort());
        httpBase = URI.create(url.getProtocol() + "://localhost:" + url.getPort());
    }

    Connection connect(String path) throws Exception {
        return connect(path, b -> { });
    }

    Connection connect(String path, Consumer<WebSocket.Builder> customizer) throws Exception {
        Connection connection = new Connection();
        WebSocket.Builder builder = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT));
        customizer.accept(builder);
        connection.ws = builder.buildAsync(base.resolve(path), connection).get(TIMEOUT, TimeUnit.SECONDS);
        return connection;
    }

    /**
     * @return The status of the response to a handshake the server refuses
     */
    int handshakeStatus(String path, Consumer<WebSocket.Builder> customizer) {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        customizer.accept(builder);
        ExecutionException error = assertThrows(ExecutionException.class,
            () -> builder.buildAsync(base.resolve(path), new Connection()).get(TIMEOUT, TimeUnit.SECONDS));
        return assertInstanceOf(WebSocketHandshakeException.class, error.getCause()).getResponse().statusCode();
    }

    /**
     * @return The status of a plain GET request, not an upgrade
     */
    int plainGetStatus(String path) throws Exception {
        return http.sendAsync(HttpRequest.newBuilder(httpBase.resolve(path)).version(HttpClient.Version.HTTP_1_1).GET().build(),
                HttpResponse.BodyHandlers.discarding())
            .get(TIMEOUT, TimeUnit.SECONDS)
            .statusCode();
    }

    @Override
    public void close() {
        // close() waits for the WebSockets that are still open, e.g. of a test that failed
        http.shutdownNow();
    }

    private static SSLContext trustAll() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new TrustAll()}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The close frame the client received.
     *
     * @param code   The status code
     * @param reason The reason
     */
    record Closed(int code, String reason) {
    }

    /**
     * Collects the messages of a connection.
     */
    static final class Connection implements WebSocket.Listener {
        final BlockingQueue<Object> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> pongs = new LinkedBlockingQueue<>();
        final CompletableFuture<Closed> closed = new CompletableFuture<>();
        WebSocket ws;
        private final StringBuilder text = new StringBuilder();
        private final ByteArrayOutputStream binary = new ByteArrayOutputStream();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            binary.writeBytes(bytes);
            if (last) {
                messages.add(binary.toByteArray());
                binary.reset();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            pongs.add(StandardCharsets.UTF_8.decode(message).toString());
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(new Closed(statusCode, reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.completeExceptionally(error);
        }

        Object nextMessage() throws InterruptedException {
            Object message = messages.poll(TIMEOUT, TimeUnit.SECONDS);
            assertNotNull(message, "no message");
            return message;
        }

        String next() throws InterruptedException {
            return assertInstanceOf(String.class, nextMessage());
        }

        void send(String message) throws Exception {
            ws.sendText(message, true).get(TIMEOUT, TimeUnit.SECONDS);
        }

        Closed closed() throws Exception {
            return closed.get(TIMEOUT, TimeUnit.SECONDS);
        }

        /**
         * Sends a close frame and waits for the close frame of the server.
         */
        Closed close(int code, String reason) throws Exception {
            ws.sendClose(code, reason).get(TIMEOUT, TimeUnit.SECONDS);
            return closed();
        }
    }

    /**
     * Trusts the self-signed certificate of the server under test, whatever its host name.
     */
    private static final class TrustAll extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            // not a server
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            // the self-signed certificate of the server under test
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            // not a server
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            // the self-signed certificate of the server under test
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // not a server
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // the self-signed certificate of the server under test
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
