/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.client.netty.trailers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.runtime.server.EmbeddedServer;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The trailers of a request and of a response travel with the body: a relay passes them on
 * untouched, a route reads the trailers of the request it receives, and a route sends the
 * trailers of the body it responds with.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
public final class TrailersRelayTest {
    public static final String SPEC_NAME = "NettyTrailersRelayTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final String REQUEST_BODY = "5\r\nhello\r\n0\r\nx-checksum: abc\r\n\r\n";

    @Test
    void trailersAreRelayedBothWays() throws Exception {
        try (GrpcLikeUpstream upstream = new GrpcLikeUpstream();
             EmbeddedServer server = server();
             Socket socket = connect(server)) {
            server.getApplicationContext().getBean(RelayState.class).uri = upstream.uri();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("POST /trailers-relay HTTP/1.1\r\nHost: localhost\r\nTE: trailers\r\nContent-Type: application/grpc\r\nTransfer-Encoding: chunked\r\n\r\n" + REQUEST_BODY).getBytes(StandardCharsets.US_ASCII));
            out.flush();

            Assertions.assertTrue(upstream.requestReceived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not get the request: " + upstream.request);
            String relayed = upstream.request.toLowerCase(Locale.ROOT);
            Assertions.assertTrue(relayed.contains("te: trailers\r\n"), "TE: trailers was not relayed: " + relayed);
            Assertions.assertTrue(relayed.contains("transfer-encoding: chunked\r\n"), "The relayed request is not chunked: " + relayed);
            Assertions.assertTrue(relayed.endsWith("\r\n0\r\nx-checksum: abc\r\n\r\n"), "The request trailers were not relayed: " + relayed);

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 200 "), head);
            Assertions.assertTrue(head.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked\r\n"), head);
            Assertions.assertEquals("5\r\nhello\r\n0\r\ngrpc-status: 0\r\ngrpc-message: fine\r\n\r\n", readChunkedBody(in));
        }
    }

    /**
     * A relay that inspects a message, e.g. to verify a checksum, consumes one half of
     * {@link ByteBody#split()} before it forwards the other. That half is then complete, with a
     * known length, but it still carries the trailers: the relayed message must stay chunked so
     * that they are not lost.
     */
    @Test
    void trailersAreRelayedAfterTheBodyIsInspected() throws Exception {
        try (GrpcLikeUpstream upstream = new GrpcLikeUpstream();
             EmbeddedServer server = server();
             Socket socket = connect(server)) {
            RelayState relay = server.getApplicationContext().getBean(RelayState.class);
            relay.uri = upstream.uri();
            // the upstream holds its trailers back until the relay has the response, so that
            // the relay inspects a streamed response
            upstream.beforeTrailers = relay.responseInspected;
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            // the data first, the trailers once the route has the request, so that the relay
            // inspects a streamed request
            int trailerStart = REQUEST_BODY.indexOf("0\r\n");
            out.write(("POST /trailers-relay/inspect HTTP/1.1\r\nHost: localhost\r\nTE: trailers\r\nContent-Type: application/grpc\r\nTransfer-Encoding: chunked\r\n\r\n" + REQUEST_BODY.substring(0, trailerStart)).getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Assertions.assertTrue(relay.requestInspected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The route did not get the request");
            out.write(REQUEST_BODY.substring(trailerStart).getBytes(StandardCharsets.US_ASCII));
            out.flush();

            Assertions.assertTrue(upstream.requestReceived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The upstream did not get the request: " + upstream.request);
            String relayed = upstream.request.toLowerCase(Locale.ROOT);
            Assertions.assertTrue(relayed.contains("transfer-encoding: chunked\r\n"), "The relayed request is not chunked: " + relayed);
            Assertions.assertFalse(relayed.contains("content-length:"), "A body with trailers must be chunked: " + relayed);
            Assertions.assertTrue(relayed.endsWith("\r\n0\r\nx-checksum: abc\r\n\r\n"), "The request trailers were not relayed: " + relayed);

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 200 "), head);
            Assertions.assertTrue(head.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked\r\n"), "The relayed response is not chunked: " + head);
            Assertions.assertFalse(head.toLowerCase(Locale.ROOT).contains("content-length:"), "A body with trailers must be chunked: " + head);
            Assertions.assertEquals("5\r\nhello\r\n0\r\ngrpc-status: 0\r\ngrpc-message: fine\r\n\r\n", readChunkedBody(in));
        }
    }

    @Test
    void routeSendsTheTrailersOfItsBody() throws Exception {
        try (EmbeddedServer server = server();
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write("GET /trailers-relay/local HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 200 "), head);
            Assertions.assertTrue(head.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked\r\n"), "A body with trailers must be chunked: " + head);
            Assertions.assertEquals("5\r\nhello\r\n0\r\nx-checksum: abc\r\n\r\n", readChunkedBody(in));
        }
    }

    @Test
    void routeReadsTheTrailersOfTheRequest() throws Exception {
        try (EmbeddedServer server = server();
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("POST /trailers-relay/read HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n" + REQUEST_BODY).getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 200 "), head);
            Assertions.assertEquals("hello, x-checksum=abc", readExactly(in, "hello, x-checksum=abc".length()));

            // a body without trailers: the trailers are empty
            out.write("POST /trailers-relay/read HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nhello".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            head = readHead(in);
            Assertions.assertTrue(head.startsWith("HTTP/1.1 200 "), head);
            Assertions.assertEquals("hello, x-checksum=null", readExactly(in, "hello, x-checksum=null".length()));
        }
    }

    private static String readHead(InputStream in) throws IOException {
        return readUntil(in, "\r\n\r\n");
    }

    /**
     * Read a chunked body up to and including its trailer section.
     */
    private static String readChunkedBody(InputStream in) throws IOException {
        StringBuilder body = new StringBuilder();
        while (true) {
            String chunk = readUntil(in, "\r\n");
            body.append(chunk);
            int size = Integer.parseInt(chunk.substring(0, chunk.length() - 2).trim(), 16);
            if (size == 0) {
                // the trailer section, ended by an empty line
                String line;
                do {
                    line = readUntil(in, "\r\n");
                    body.append(line);
                } while (line.length() > 2);
                return body.toString();
            }
            body.append(readExactly(in, size + 2));
        }
    }

    private static String readUntil(InputStream in, String end) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] terminator = end.getBytes(StandardCharsets.US_ASCII);
        int b;
        while ((b = in.read()) != -1) {
            bytes.write(b);
            if (bytes.size() >= terminator.length) {
                byte[] all = bytes.toByteArray();
                boolean matches = true;
                for (int i = 0; i < terminator.length; i++) {
                    if (all[all.length - terminator.length + i] != terminator[i]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    break;
                }
            }
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    private static String readExactly(InputStream in, int n) throws IOException {
        byte[] bytes = in.readNBytes(n);
        Assertions.assertEquals(n, bytes.length, "The connection ended after " + new String(bytes, StandardCharsets.ISO_8859_1));
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static EmbeddedServer server() {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
    }

    private static Socket connect(EmbeddedServer server) throws IOException {
        int port = server.getPort();
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
                    // the test is the client, no client certificate is checked
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
     * The state the test and the relay share: where the relay sends the requests, set by the
     * test once the upstream is listening, and how far the relay got.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RelayState {
        volatile URI uri;
        /**
         * Counted down when the relay starts to inspect the request.
         */
        final CountDownLatch requestInspected = new CountDownLatch(1);
        /**
         * Counted down when the relay starts to inspect the response.
         */
        final CountDownLatch responseInspected = new CountDownLatch(1);
    }

    @Controller("/trailers-relay")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        private final RawHttpClient client;
        private final RelayState relay;

        // the upstream is a plain HTTP/1.1 socket, even when the suite configures the client for HTTP/2
        Routes(@Client(plaintextMode = HttpVersionSelection.PlaintextMode.HTTP_1) RawHttpClient client, RelayState relay) {
            this.client = client;
            this.relay = relay;
        }

        @Post
        @Consumes(MediaType.ALL)
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> relay(ServerHttpRequest<?> request) {
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), relay.uri.resolve("/grpc").toString());
            request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
            return Mono.from(client.exchange(outbound, request.byteBody().move(), null, RawRequestOptions.proxy()));
        }

        /**
         * Inspect the request and the response before they are relayed: read one half of the
         * body fully, then forward the other half.
         */
        @Post("/inspect")
        @Consumes(MediaType.ALL)
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> inspectAndRelay(ServerHttpRequest<?> request) {
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), relay.uri.resolve("/grpc").toString());
            request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
            return inspect(request.byteBody(), relay.requestInspected)
                .flatMap(ignored -> Mono.from(client.exchange(outbound, request.byteBody().move(), null, RawRequestOptions.proxy())))
                .flatMap(response -> inspect(((ByteBodyHttpResponse<?>) response).byteBody(), relay.responseInspected).thenReturn(response));
        }

        /**
         * Consume one half of the body: once done, the other half is complete with a known length.
         */
        private static Mono<String> inspect(ByteBody body, CountDownLatch inspecting) {
            CloseableByteBody half = body.split(ByteBody.SplitBackpressureMode.FASTEST);
            inspecting.countDown();
            return Mono.fromFuture(half.buffer()).map(bytes -> {
                try (bytes) {
                    return bytes.toString(StandardCharsets.UTF_8);
                }
            });
        }

        @Get("/local")
        @Produces(MediaType.ALL)
        ByteBodyHttpResponse<?> local() {
            HttpHeaders trailers = new SimpleHttpHeaders(Map.of("x-checksum", "abc"), ConversionService.SHARED);
            return ByteBodyHttpResponseWrapper.wrap(
                HttpResponse.ok().contentType(MediaType.TEXT_PLAIN_TYPE),
                BODY_FACTORY.withTrailers(BODY_FACTORY.copyOf("hello", StandardCharsets.UTF_8), CompletableFuture.completedFuture(trailers)));
        }

        @Post("/read")
        @Consumes(MediaType.ALL)
        @Produces(MediaType.TEXT_PLAIN)
        Mono<String> read(ServerHttpRequest<?> request) {
            // the trailers arrive after the body: they are known once the body is consumed
            CompletableFuture<HttpHeaders> trailers = request.byteBody().trailers().toCompletableFuture();
            return Mono.fromFuture(request.byteBody().buffer())
                .map(body -> body.toString(StandardCharsets.UTF_8))
                .flatMap(body -> Mono.fromFuture(trailers).map(t -> body + ", x-checksum=" + t.get("x-checksum")));
        }
    }

    /**
     * A raw upstream that reads one chunked request with trailers and answers with a chunked
     * response with trailers, the way a gRPC server does.
     */
    static final class GrpcLikeUpstream implements AutoCloseable {
        final CountDownLatch requestReceived = new CountDownLatch(1);
        volatile String request = "";
        /**
         * When set, the response trailers are only sent once this latch is counted down.
         */
        volatile CountDownLatch beforeTrailers;
        private final ServerSocket serverSocket;

        GrpcLikeUpstream() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::serve, "grpc-like-upstream");
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
                request = head;
                // a relayed request that lost its trailers may come with a Content-Length
                int contentLength = contentLength(head);
                request = head + (contentLength < 0 ? readChunkedBody(in) : new String(in.readNBytes(contentLength), StandardCharsets.ISO_8859_1));
                requestReceived.countDown();
                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/grpc\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                CountDownLatch latch = beforeTrailers;
                if (latch != null && !latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    return;
                }
                out.write("0\r\ngrpc-status: 0\r\ngrpc-message: fine\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // wait for the relay to close, so that the response is not cut short
                in.read();
            } catch (IOException ignored) {
                // the connection was closed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static int contentLength(String head) {
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    return Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
