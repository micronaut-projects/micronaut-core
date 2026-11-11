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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * On a plain TCP connection that switched protocols, the end of one direction is a half-close:
 * the other direction still flows until it ends too.
 */
class UpgradeHalfCloseTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void theEndOfTheSentBodyShutsDownTheOutputOnly() throws Exception {
        try (HalfClosingUpstream upstream = new HalfClosingUpstream(false);
             ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UpgradedHttpResponse<?> upgraded = upgrade(client, upstream);
            try (upgraded) {
                upgraded.send(body(Flux.just("hi")));
                // the upstream reads to the end of the bytes of the client, then answers
                byte[] answer = upgraded.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toByteArray();
                Assertions.assertEquals("bye after hi", new String(answer, StandardCharsets.US_ASCII));
            }
            Assertions.assertEquals("hi", upstream.received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    void theEndOfTheInboundBytesLeavesTheOutputOpen() throws Exception {
        try (HalfClosingUpstream upstream = new HalfClosingUpstream(true);
             ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UpgradedHttpResponse<?> upgraded = upgrade(client, upstream);
            try (upgraded) {
                // the upstream shut down its output first: the inbound bytes end
                byte[] first = upgraded.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toByteArray();
                Assertions.assertEquals("first", new String(first, StandardCharsets.US_ASCII));
                // and the client still sends
                upgraded.send(body(Flux.just("late")));
                Assertions.assertEquals("late", upstream.received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void aHalfCloseOfThePeerDoesNotTruncateTheSentBody() throws Exception {
        byte[] large = new byte[8 * 1024 * 1024];
        java.util.Arrays.fill(large, (byte) 'x');
        try (HalfClosingUpstream upstream = new HalfClosingUpstream(false, 1000);
             ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UpgradedHttpResponse<?> upgraded = upgrade(client, upstream);
            try (upgraded) {
                // reading the inbound bytes is what notices the half-close of the upstream
                CompletableFuture<?> inbound = upgraded.byteBody().buffer();
                // the body ends while most of it is still queued; the upstream shuts down its
                // output before it reads: the queued bytes are still written
                upgraded.send(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
                    .adapt(Flux.just("hi".getBytes(StandardCharsets.US_ASCII), large)
                        .map(bytes -> (ReadBuffer) ReadBufferFactory.getJdkFactory().adapt(bytes))));
                Assertions.assertEquals(2 + large.length, upstream.received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).length());
                inbound.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void overTlsTheEndOfTheSentBodyClosesTheConnection() throws Exception {
        try (HalfClosingUpstream upstream = HalfClosingUpstream.tls();
             ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.client.ssl.insecure-trust-all-certificates", true));
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UpgradedHttpResponse<?> upgraded = upgrade(client, upstream);
            try (upgraded) {
                CompletableFuture<?> inbound = upgraded.byteBody().buffer();
                upgraded.send(body(Flux.just("hi")));
                Assertions.assertEquals("hi", upstream.received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                // TLS cannot shut down one direction: the connection is closed, although the
                // upstream still keeps its side open
                inbound.handle((ignored, error) -> null).get(TIMEOUT_SECONDS / 2, TimeUnit.SECONDS);
            }
        }
    }

    private static UpgradedHttpResponse<?> upgrade(RawHttpClient client, HalfClosingUpstream upstream) {
        HttpResponse<?> response = Mono.from(client.exchange(
                HttpRequest.GET(upstream.scheme() + "://127.0.0.1:" + upstream.port() + "/half-close")
                    .header("Connection", "Upgrade")
                    .header("Upgrade", "echo"),
                null, null, RawRequestOptions.proxy()))
            .block(Duration.ofSeconds(TIMEOUT_SECONDS));
        UpgradedHttpResponse<?> upgraded = UpgradedHttpResponse.unwrap(response);
        Assertions.assertNotNull(upgraded, () -> "Not upgraded: " + response);
        return upgraded;
    }

    private static CloseableByteBody body(Flux<String> parts) {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
            .adapt(parts.map(text -> (ReadBuffer) ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.US_ASCII)));
    }

    /**
     * Switches to {@code echo}; then either reads to the end and answers, or sends
     * {@code first}, shuts down its output and reads to the end. Over TLS, it reads to the end
     * and keeps the connection open.
     */
    static final class HalfClosingUpstream implements AutoCloseable {
        final CompletableFuture<String> received = new CompletableFuture<>();
        private final ServerSocket serverSocket;
        private final boolean shutdownFirst;
        private final long shutdownBeforeReadingMillis;
        private final boolean tls;

        HalfClosingUpstream(boolean shutdownFirst) throws IOException {
            this(shutdownFirst, -1);
        }

        /**
         * @param shutdownBeforeReadingMillis When not negative, the upstream waits this long
         *                                    without reading, shuts down its output, then reads
         *                                    to the end
         */
        HalfClosingUpstream(boolean shutdownFirst, long shutdownBeforeReadingMillis) throws IOException {
            this(shutdownFirst, shutdownBeforeReadingMillis, new ServerSocket(0, 50, InetAddress.getLoopbackAddress()), false);
        }

        private HalfClosingUpstream(boolean shutdownFirst, long shutdownBeforeReadingMillis, ServerSocket serverSocket, boolean tls) {
            this.shutdownFirst = shutdownFirst;
            this.shutdownBeforeReadingMillis = shutdownBeforeReadingMillis;
            this.tls = tls;
            this.serverSocket = serverSocket;
            Thread acceptor = new Thread(this::serve, "half-closing-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        static HalfClosingUpstream tls() throws Exception {
            SelfSignedCertificate certificate = new SelfSignedCertificate();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            char[] password = "secret".toCharArray();
            keyStore.setKeyEntry("upstream", certificate.key(), password, new Certificate[]{certificate.cert()});
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers.getKeyManagers(), null, null);
            ServerSocket serverSocket = sslContext.getServerSocketFactory().createServerSocket(0, 50, InetAddress.getLoopbackAddress());
            return new HalfClosingUpstream(false, -1, serverSocket, true);
        }

        String scheme() {
            return tls ? "https" : "http";
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                readHead(in);
                out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                if (tls) {
                    received.complete(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
                    // keep this side open
                    Thread.sleep(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                } else if (shutdownBeforeReadingMillis >= 0) {
                    Thread.sleep(shutdownBeforeReadingMillis);
                    socket.shutdownOutput();
                    received.complete(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
                } else if (shutdownFirst) {
                    out.write("first".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    socket.shutdownOutput();
                    received.complete(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
                } else {
                    String text = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
                    received.complete(text);
                    out.write(("bye after " + text).getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    socket.shutdownOutput();
                    // wait for the client to close
                    in.read();
                }
            } catch (IOException e) {
                received.completeExceptionally(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                received.completeExceptionally(e);
            }
        }

        private static void readHead(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                int b = in.read();
                if (b == -1) {
                    throw new IOException("Connection closed in the head");
                }
                head.write(b);
                matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
