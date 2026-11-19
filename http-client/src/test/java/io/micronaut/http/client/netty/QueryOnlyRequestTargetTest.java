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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * The request target sent for a query-only request to a root-mounted service.
 */
class QueryOnlyRequestTargetTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final String OK = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok";

    @Test
    void aQueryOnlyRequestToARootMountedServiceSendsSlashAsPath() throws Exception {
        // The load balancer resolves the service instance URI without a path,
        // so a query-only request targets a URI with an empty path. RFC 9112
        // §3.2.1 requires the client to send "/" as the path in that case.
        try (Upstream upstream = new Upstream();
             Client client = new Client(upstream)) {
            CompletableFuture<HttpResponse<?>> pending = client.raw(HttpRequest.GET("?q=x"), null);
            Connection connection = upstream.connection(0);
            connection.awaitReceived("GET /?q=x HTTP/1.1\r\n");
            connection.write(OK);
            assertResponse(pending, 200, "ok");
        }
    }

    private static void assertResponse(CompletableFuture<HttpResponse<?>> pending, int code, String body) throws Exception {
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(code, response.code());
            Assertions.assertEquals(body, response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
        }
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            Assertions.assertTrue(System.nanoTime() < deadline, message);
            Thread.sleep(10);
        }
    }

    /**
     * A load balanced client pointed at the upstream socket server.
     */
    private static final class Client implements AutoCloseable {
        final ApplicationContext ctx;
        final DefaultHttpClient client;

        Client(Upstream upstream) {
            ctx = ApplicationContext.run();
            ServiceInstance instance = ServiceInstance.of("query-only", URI.create(upstream.uri("")));
            LoadBalancer loadBalancer = discriminator -> Publishers.just(instance);
            client = DefaultHttpClient.builder()
                .loadBalancer(loadBalancer)
                .configuration(ctx.getBean(HttpClientConfiguration.class))
                .build();
        }

        CompletableFuture<HttpResponse<?>> raw(MutableHttpRequest<?> request, CloseableByteBody body) {
            return Mono.<HttpResponse<?>>from(client.exchange(request, body, (Thread) null)).toFuture();
        }

        @Override
        public void close() {
            client.close();
            ctx.close();
        }
    }

    /**
     * Accepts connections and records what the client sends on each.
     */
    private static final class Upstream implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

        Upstream() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::accept, "query-only-target-upstream");
            thread.setDaemon(true);
            thread.start();
        }

        String uri(String path) {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
        }

        Connection connection(int index) throws InterruptedException {
            await(() -> connections.size() > index, () -> "The client did not open connection " + index);
            return connections.get(index);
        }

        private void accept() {
            try {
                while (true) {
                    Connection connection = new Connection(serverSocket.accept());
                    connections.add(connection);
                    Thread thread = new Thread(connection::read, "query-only-target-connection");
                    thread.setDaemon(true);
                    thread.start();
                }
            } catch (IOException ignored) {
                // closed by the test
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            for (Connection connection : connections) {
                connection.close();
            }
        }
    }

    private static final class Connection {
        private final Socket socket;
        private final StringBuffer received = new StringBuffer();

        Connection(Socket socket) {
            this.socket = socket;
        }

        String received() {
            return received.toString();
        }

        private void read() {
            try (Socket s = socket) {
                InputStream in = s.getInputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    received.append(new String(buffer, 0, n, StandardCharsets.ISO_8859_1));
                }
            } catch (IOException ignored) {
                // closed by the test
            }
        }

        void awaitReceived(String text) throws InterruptedException {
            await(() -> occurrences(received(), text) >= 1, () -> "Did not receive " + text.strip() + ", received: " + received());
        }

        void write(String text) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write(text.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        void close() throws IOException {
            socket.close();
        }
    }
}
