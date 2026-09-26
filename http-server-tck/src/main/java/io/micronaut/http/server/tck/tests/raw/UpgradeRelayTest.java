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
package io.micronaut.http.server.tck.tests.raw;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A route that relays an {@code Upgrade} request to an upstream server through the
 * {@link RawHttpClient} and returns its {@code 101 Switching Protocols} response: the server
 * switches its own connection to the new protocol and pipes the bytes both ways, until either
 * side closes.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
public final class UpgradeRelayTest {
    public static final String SPEC_NAME = "UpgradeRelayTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final int LARGE_STREAM_CHUNK = 16 * 1024;
    private static final int LARGE_STREAM_CHUNKS = 1024;
    private static final long LARGE_STREAM_SIZE = (long) LARGE_STREAM_CHUNK * LARGE_STREAM_CHUNKS;

    @Test
    void switchedProtocolIsRelayedBothWays() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ServerUnderTest server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /upgrade-relay HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 101 "), head);
            Assertions.assertTrue(head.toLowerCase(Locale.ROOT).contains("upgrade: echo"), head);
            Assertions.assertTrue(upstream.upgraded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not see the upgrade");

            // the bytes of the client reach the upstream, and its bytes reach the client
            out.write("hello".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertEquals("HELLO", readExactly(in, 5));
            out.write("bye".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertEquals("BYE", readExactly(in, 3));

            // the client closes: the upstream connection is closed too
            socket.close();
            Assertions.assertTrue(upstream.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream connection was not closed");
        }
    }

    @Test
    void oneOfTheOfferedProtocolsIsRelayed() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ServerUnderTest server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            // the client offers several protocols; the upstream selects one of them
            out.write(("GET /upgrade-relay HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: example/1, example/2\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 101 "), head);
            Assertions.assertTrue(head.toLowerCase(Locale.ROOT).contains("upgrade: echo"), head);
            Assertions.assertTrue(upstream.upgraded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not see the upgrade");
            out.write("hello".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertEquals("HELLO", readExactly(in, 5));
        }
    }

    @Test
    void upstreamThatDoesNotSwitchIsRelayedNormally() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ServerUnderTest server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
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
    void bytesSentWithTheUpgradeRequestReachTheUpstream() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ServerUnderTest server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            // the first bytes of the new protocol in the same write as the request, so they arrive with it
            out.write(("GET /upgrade-relay HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\nhello").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 101 "), head);
            Assertions.assertTrue(upstream.upgraded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not see the upgrade");
            // more bytes after the switch, to tell whether the first ones were lost or delayed
            out.write("more".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertEquals("HELL", readExactly(in, 4), "The bytes sent with the request did not reach the upstream first");
            Assertions.assertEquals("OMORE", readExactly(in, 5));
        }
    }

    @Test
    void largeStreamFromTheClientReachesTheUpstream() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ServerUnderTest server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /upgrade-relay?mode=sink HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 101 "), head);
            Assertions.assertTrue(upstream.upgraded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not see the upgrade");
            // a large stream, then the client closes: everything before its close reaches the upstream
            byte[] chunk = new byte[LARGE_STREAM_CHUNK];
            for (int i = 0; i < LARGE_STREAM_CHUNKS; i++) {
                out.write(chunk);
            }
            out.flush();
            socket.close();
            Assertions.assertTrue(upstream.closed.await(TIMEOUT_SECONDS * 3, TimeUnit.SECONDS), "The upstream connection was not closed");
            Assertions.assertEquals(LARGE_STREAM_SIZE, upstream.bytesReceived.get(), "Not all bytes of the client reached the upstream");
        }
    }

    @Test
    void largeStreamFromTheUpstreamReachesTheClient() throws Exception {
        try (EchoUpstream upstream = new EchoUpstream();
             ServerUnderTest server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(UpstreamAddress.class).uri = upstream.uri();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET /upgrade-relay?mode=flood HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 101 "), head);
            // the upstream sends a large stream and closes: everything before its close reaches the client
            long read = 0;
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                read += n;
            }
            Assertions.assertEquals(LARGE_STREAM_SIZE, read, "Not all bytes of the upstream reached the client");
        }
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            head.write(b);
            if (head.size() >= 4) {
                byte[] bytes = head.toByteArray();
                int n = bytes.length;
                if (bytes[n - 4] == '\r' && bytes[n - 3] == '\n' && bytes[n - 2] == '\r' && bytes[n - 1] == '\n') {
                    break;
                }
            }
        }
        return head.toString(StandardCharsets.ISO_8859_1);
    }

    private static String readExactly(InputStream in, int n) throws IOException {
        byte[] bytes = in.readNBytes(n);
        Assertions.assertEquals(n, bytes.length, "The connection ended after " + new String(bytes, StandardCharsets.ISO_8859_1));
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static Socket connect(ServerUnderTest server) throws IOException {
        int port = server.getPort().orElseThrow();
        Socket socket;
        if (server.getApplicationContext().getProperty("micronaut.server.ssl.enabled", Boolean.class).orElse(false)) {
            // HTTP/1.1 over TLS: without ALPN, a server that also speaks HTTP/2 falls back to HTTP/1.1
            socket = trustAll().getSocketFactory().createSocket("localhost", port);
        } else {
            socket = new Socket("localhost", port);
        }
        socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        return socket;
    }

    private static SSLContext trustAll() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // the self-signed certificate of the server under test
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } }, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
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

        // the upstream is a plain HTTP/1.1 socket, even when the suite configures the client for HTTP/2
        Relay(@Client(plaintextMode = HttpVersionSelection.PlaintextMode.HTTP_1) RawHttpClient client, UpstreamAddress upstream) {
            this.client = client;
            this.upstream = upstream;
        }

        @Get
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> relay(ServerHttpRequest<?> request) {
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), upstream.uri.resolve(request.getUri().getRawQuery() == null ? "/echo" : "/echo?" + request.getUri().getRawQuery()).toString());
            request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
            return Mono.from(client.exchange(outbound, request.byteBody().move(), null, RawRequestOptions.proxy()));
        }
    }

    /**
     * A raw upstream that switches to an "echo" protocol, answering every byte upper-cased, or
     * refuses the switch with a {@code 200} when asked to. With {@code mode=sink} it only counts
     * the bytes it receives until the connection ends, with {@code mode=flood} it sends a large
     * stream after the switch and closes.
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
                out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                upgraded.countDown();
                if (head.contains("mode=flood")) {
                    // send a large stream, then close
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
