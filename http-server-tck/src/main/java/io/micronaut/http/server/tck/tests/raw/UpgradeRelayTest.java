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
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
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

        Relay(RawHttpClient client, UpstreamAddress upstream) {
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
     * refuses the switch with a {@code 200} when asked to.
     */
    static final class EchoUpstream implements AutoCloseable {
        final CountDownLatch upgraded = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
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
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(new String(buffer, 0, n, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
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
