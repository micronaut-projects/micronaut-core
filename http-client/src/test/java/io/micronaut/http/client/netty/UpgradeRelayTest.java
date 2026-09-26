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
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A route relays an {@code Upgrade} request to an upstream through the raw client and returns
 * its {@code 101 Switching Protocols} response: the server switches its own connection and
 * pipes the bytes both ways, until either side closes.
 */
class UpgradeRelayTest {
    private static final String SPEC_NAME = "NettyUpgradeRelayTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final int LARGE_STREAM_CHUNK = 16 * 1024;
    private static final int LARGE_STREAM_CHUNKS = 256;
    private static final long LARGE_STREAM_SIZE = (long) LARGE_STREAM_CHUNK * LARGE_STREAM_CHUNKS;

    @ParameterizedTest
    @CsvSource({"/upgrade-relay", "/upgrade-relay?activity=true"})
    void switchedProtocolIsRelayedBothWays(String path) throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             EmbeddedServer server = server(upstream);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: example/1, echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 101 "), head);
            Assertions.assertTrue(head.toLowerCase(Locale.ROOT).contains("upgrade: echo"), head);
            Assertions.assertTrue(upstream.upgraded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not see the upgrade");

            out.write("hello".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertEquals("HELLO", readExactly(in, 5));

            // the client closes: the upstream connection is closed too
            socket.close();
            Assertions.assertTrue(upstream.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream connection was not closed");
        }
    }

    @Test
    void upstreamThatDoesNotSwitchIsRelayedNormally() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             EmbeddedServer server = server(upstream);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /upgrade-relay?refuse=true HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 200 "), head);
            Assertions.assertEquals("no", readExactly(in, 2));
        }
    }

    @Test
    void largeStreamsAreRelayedBothWays() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             EmbeddedServer server = server(upstream);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /upgrade-relay?mode=sink HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertTrue(readHead(in).startsWith("HTTP/1.1 101 "));
            Assertions.assertTrue(upstream.upgraded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not see the upgrade");

            byte[] chunk = new byte[LARGE_STREAM_CHUNK];
            for (int i = 0; i < LARGE_STREAM_CHUNKS; i++) {
                out.write(chunk);
            }
            out.flush();
            socket.close();
            Assertions.assertTrue(upstream.closed.await(TIMEOUT_SECONDS * 3, TimeUnit.SECONDS), "The upstream connection was not closed");
            Assertions.assertEquals(LARGE_STREAM_SIZE, upstream.bytesReceived.get(), "Not all bytes of the client reached the upstream");
        }
        try (EchoUpstream upstream = new EchoUpstream();
             EmbeddedServer server = server(upstream);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /upgrade-relay?mode=flood HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertTrue(readHead(in).startsWith("HTTP/1.1 101 "));
            long read = 0;
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                read += n;
            }
            Assertions.assertEquals(LARGE_STREAM_SIZE, read, "Not all bytes of the upstream reached the client");
        }
    }

    @Test
    void switchToAProtocolThatWasNotOfferedFails() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            MutableHttpRequest<?> request = HttpRequest.GET(upstream.uri().resolve("/echo?protocol=other").toString())
                .header("Connection", "Upgrade")
                .header("Upgrade", "echo");
            ExecutionException e = Assertions.assertThrows(ExecutionException.class,
                () -> Mono.from(client.exchange(request, null, null, RawRequestOptions.proxy())).toFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(HttpClientException.class, e.getCause());
            Assertions.assertTrue(e.getCause().getMessage().contains("other"), e.getCause().getMessage());
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "echo|echo|true",
        "ECHO|example/1, echo|true",
        "echo, example/1|example/1,echo|true",
        "other|echo|false",
        "echo, other|echo|false",
        " , |echo|false",
    })
    void selectedProtocolsMustBeOffered(String selected, String offered, boolean expected) {
        Assertions.assertEquals(expected, NettyHttpClient.isOffered(selected, offered));
    }

    @Test
    void upgradeOptions() {
        RawRequestOptions options = RawRequestOptions.proxy().toBuilder().activityTimeout(Duration.ofSeconds(5)).build();
        Assertions.assertTrue(options.isAllowUpgrade());
        Assertions.assertEquals(Duration.ofSeconds(5), options.getActivityTimeout());
        Assertions.assertEquals(options, options.toBuilder().build());
        Assertions.assertEquals(options.hashCode(), options.toBuilder().build().hashCode());
        Assertions.assertNotEquals(options, RawRequestOptions.proxy());
        Assertions.assertNotEquals(RawRequestOptions.proxy(), RawRequestOptions.proxy().toBuilder().allowUpgrade(false).build());
        Assertions.assertThrows(IllegalArgumentException.class, () -> RawRequestOptions.builder().activityTimeout(Duration.ZERO));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RawRequestOptions.builder().activityTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void aResponseWithoutASwitchCarriesNoUpgrade() {
        Assertions.assertNull(UpgradedHttpResponse.unwrap(HttpResponse.ok()));
        Assertions.assertNull(UpgradedHttpResponse.unwrap(new HttpResponseWrapper<>(HttpResponse.ok("body"))));
        Assertions.assertNull(UpgradedHttpResponse.unwrap(new HttpResponseWrapper<>(HttpResponse.ok())));
    }

    private static EmbeddedServer server(EchoUpstream upstream) {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
        server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
        return server;
    }

    private static Socket connect(EmbeddedServer server) throws IOException {
        Socket socket = new Socket("localhost", server.getPort());
        socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        return socket;
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            head.write(b);
            byte[] bytes = head.toByteArray();
            int n = bytes.length;
            if (n >= 4 && bytes[n - 4] == '\r' && bytes[n - 3] == '\n' && bytes[n - 2] == '\r' && bytes[n - 1] == '\n') {
                break;
            }
        }
        return head.toString(StandardCharsets.ISO_8859_1);
    }

    private static String readExactly(InputStream in, int n) throws IOException {
        byte[] bytes = in.readNBytes(n);
        Assertions.assertEquals(n, bytes.length, "The connection ended after " + new String(bytes, StandardCharsets.ISO_8859_1));
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /**
     * Where the relay sends the requests, set by the test once the upstream is listening.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UpstreamAddress {
        volatile URI uri;
    }

    @Controller("/upgrade-relay")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Relay {
        private final RawHttpClient client;
        private final UpstreamAddress upstream;

        Relay(RawHttpClient client, UpstreamAddress upstream) {
            this.client = client;
            this.upstream = upstream;
        }

        @Get
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> relay(ServerHttpRequest<?> request) {
            String query = request.getUri().getRawQuery();
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), upstream.uri.resolve(query == null ? "/echo" : "/echo?" + query).toString());
            request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
            RawRequestOptions options = RawRequestOptions.proxy();
            if (query != null && query.contains("activity=true")) {
                options = options.toBuilder().activityTimeout(Duration.ofSeconds(30)).build();
            }
            return Mono.from(client.exchange(outbound, request.byteBody().move(), null, options));
        }
    }

    /**
     * A raw upstream that switches to an "echo" protocol, answering every byte upper-cased, or
     * refuses the switch with a {@code 200} when asked to. With {@code mode=sink} it only counts
     * the bytes it receives, with {@code mode=flood} it sends a large stream and closes, and with
     * {@code protocol=} it switches to another protocol than the one offered.
     */
    static final class EchoUpstream implements AutoCloseable {
        final CountDownLatch upgraded = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicLong bytesReceived = new AtomicLong();
        private final ServerSocket serverSocket;

        EchoUpstream() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::serve, "echo-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/");
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String head = readHead(in);
                if (head.contains("refuse=true")) {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nno".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    return;
                }
                String protocol = head.contains("protocol=other") ? "other" : "echo";
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: " + protocol + "\r\nConnection: Upgrade\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                upgraded.countDown();
                if (head.contains("mode=flood")) {
                    byte[] chunk = new byte[LARGE_STREAM_CHUNK];
                    for (int i = 0; i < LARGE_STREAM_CHUNKS; i++) {
                        out.write(chunk);
                    }
                    out.flush();
                    return;
                }
                boolean sink = head.contains("mode=sink");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    bytesReceived.addAndGet(n);
                    if (!sink) {
                        out.write(new String(buffer, 0, n, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.ISO_8859_1));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // the connection was closed
            } finally {
                closed.countDown();
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
