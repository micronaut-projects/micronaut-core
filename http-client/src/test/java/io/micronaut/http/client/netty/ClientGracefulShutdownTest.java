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
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.graceful.GracefulShutdownManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * The graceful shutdown of the HTTP client lets the running and waiting requests finish, and
 * closes each connection once no request is left for it.
 */
class ClientGracefulShutdownTest {

    @Test
    void idleClientShutsDownRightAway() throws Exception {
        try (ServerSocket server = server();
             DefaultHttpClient client = client(server, new DefaultHttpClientConfiguration())) {
            client.connectionManager().shutdownGracefully().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @EnumSource(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion.class)
    void idleConnectionIsClosed(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion version) throws Exception {
        try (ServerSocket server = server();
             DefaultHttpClient client = client(server, configuration(version))) {
            CompletableFuture<HttpResponse<String>> response = exchange(client, "/first");
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());
                respond(socket, "first");
                Assertions.assertEquals("first", response.get(5, TimeUnit.SECONDS).body());

                // the connection is kept alive in the pool until the graceful shutdown
                client.connectionManager().shutdownGracefully().toCompletableFuture().get(5, TimeUnit.SECONDS);

                assertClosedByClient(socket);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion.class)
    void runningRequestFinishes(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion version) throws Exception {
        try (ServerSocket server = server();
             DefaultHttpClient client = client(server, configuration(version))) {
            CompletableFuture<HttpResponse<String>> response = exchange(client, "/running");
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());

                CompletableFuture<?> shutdown = client.connectionManager().shutdownGracefully().toCompletableFuture();
                Thread.sleep(300);
                Assertions.assertFalse(shutdown.isDone(), "The graceful shutdown did not wait for the running request");
                Assertions.assertEquals(OptionalLong.of(1), client.connectionManager().reportActiveTasks());
                Assertions.assertFalse(response.isDone());

                respond(socket, "running");

                Assertions.assertEquals("running", response.get(5, TimeUnit.SECONDS).body());
                shutdown.get(5, TimeUnit.SECONDS);
                assertClosedByClient(socket);
                Assertions.assertEquals(OptionalLong.of(0), client.connectionManager().reportActiveTasks());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion.class)
    void waitingRequestIsServed(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion version) throws Exception {
        HttpClientConfiguration configuration = configuration(version);
        configuration.getConnectionPoolConfiguration().setMaxConcurrentHttp1Connections(1);
        try (ServerSocket server = server();
             DefaultHttpClient client = client(server, configuration)) {
            CompletableFuture<HttpResponse<String>> first = exchange(client, "/first");
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());
                CompletableFuture<HttpResponse<String>> waiting = exchange(client, "/waiting");
                // let the second request queue for the only connection
                Thread.sleep(300);
                Assertions.assertFalse(waiting.isDone());

                CompletableFuture<?> shutdown = client.connectionManager().shutdownGracefully().toCompletableFuture();
                Assertions.assertEquals(OptionalLong.of(2), client.connectionManager().reportActiveTasks());

                respond(socket, "first");
                Assertions.assertEquals("first", first.get(5, TimeUnit.SECONDS).body());
                // the waiting request reuses the connection, which closes after it
                readHead(socket.getInputStream());
                Assertions.assertFalse(shutdown.isDone());
                respond(socket, "waiting");
                Assertions.assertEquals("waiting", waiting.get(5, TimeUnit.SECONDS).body());

                shutdown.get(5, TimeUnit.SECONDS);
                assertClosedByClient(socket);
            }
        }
    }

    @Test
    void newRequestIsServedDuringShutdown() throws Exception {
        try (ServerSocket server = server();
             DefaultHttpClient client = client(server, new DefaultHttpClientConfiguration())) {
            CompletableFuture<HttpResponse<String>> first = exchange(client, "/first");
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());
                CompletableFuture<?> shutdown = client.connectionManager().shutdownGracefully().toCompletableFuture();

                // e.g. a request of a server that shuts down gracefully, too
                CompletableFuture<HttpResponse<String>> later = exchange(client, "/later");
                try (Socket second = server.accept()) {
                    second.setSoTimeout(5000);
                    readHead(second.getInputStream());
                    respond(second, "later");
                    Assertions.assertEquals("later", later.get(5, TimeUnit.SECONDS).body());
                    assertClosedByClient(second);
                }
                Assertions.assertFalse(shutdown.isDone());

                respond(socket, "first");
                Assertions.assertEquals("first", first.get(5, TimeUnit.SECONDS).body());
                shutdown.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void stopCompletesGracefulShutdown() throws Exception {
        try (ServerSocket server = server()) {
            DefaultHttpClient client = client(server, new DefaultHttpClientConfiguration());
            CompletableFuture<HttpResponse<String>> response = exchange(client, "/never");
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());
                CompletionStage<?> shutdown = client.connectionManager().shutdownGracefully();
                Assertions.assertSame(shutdown, client.connectionManager().shutdownGracefully());

                // the grace period passes without a response
                client.close();

                shutdown.toCompletableFuture().get(5, TimeUnit.SECONDS);
                Assertions.assertTrue(response.handle((r, e) -> e).get(5, TimeUnit.SECONDS) != null);
            }
        }
    }

    @Test
    void gracefulShutdownOfTheContextWaitsForClientRequests() throws Exception {
        try (ServerSocket server = server();
             ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.lifecycle.graceful-shutdown.enabled", true))) {
            HttpClient client = ctx.createBean(HttpClient.class, URI.create("http://127.0.0.1:" + server.getLocalPort()).toURL());
            CompletableFuture<HttpResponse<String>> response = Mono.from(client.exchange(HttpRequest.GET("/running"), String.class)).toFuture();
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());

                GracefulShutdownManager manager = ctx.getBean(GracefulShutdownManager.class);
                CompletableFuture<?> shutdown = manager.shutdownGracefully().toCompletableFuture();
                Thread.sleep(300);
                Assertions.assertFalse(shutdown.isDone(), "The graceful shutdown did not wait for the client request");
                Assertions.assertEquals(OptionalLong.of(1), manager.reportActiveTasks());

                respond(socket, "running");

                Assertions.assertEquals("running", response.get(5, TimeUnit.SECONDS).body());
                shutdown.get(5, TimeUnit.SECONDS);
                assertClosedByClient(socket);
            }
        }
    }

    @Test
    void contextWaitsForAllClientsToBeIdleAtOnce() throws Exception {
        try (ServerSocket serverA = server();
             ServerSocket serverB = server();
             ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.lifecycle.graceful-shutdown.enabled", true))) {
            HttpClient clientA = ctx.createBean(HttpClient.class, URI.create("http://127.0.0.1:" + serverA.getLocalPort()).toURL());
            HttpClient clientB = ctx.createBean(HttpClient.class, URI.create("http://127.0.0.1:" + serverB.getLocalPort()).toURL());
            CompletableFuture<HttpResponse<String>> responseB = Mono.from(clientB.exchange(HttpRequest.GET("/b"), String.class)).toFuture();
            try (Socket socketB = serverB.accept()) {
                socketB.setSoTimeout(5000);
                readHead(socketB.getInputStream());

                CompletableFuture<?> shutdown = ctx.getBean(GracefulShutdownManager.class).shutdownGracefully().toCompletableFuture();
                // client A is idle in the first steps, then gets a request while B is still busy
                Thread.sleep(200);
                CompletableFuture<HttpResponse<String>> responseA = Mono.from(clientA.exchange(HttpRequest.GET("/a"), String.class)).toFuture();
                try (Socket socketA = serverA.accept()) {
                    socketA.setSoTimeout(5000);
                    readHead(socketA.getInputStream());

                    respond(socketB, "b");
                    Assertions.assertEquals("b", responseB.get(5, TimeUnit.SECONDS).body());
                    Thread.sleep(300);
                    Assertions.assertFalse(shutdown.isDone(), "The graceful shutdown completed while client A still ran a request");

                    respond(socketA, "a");
                    Assertions.assertEquals("a", responseA.get(5, TimeUnit.SECONDS).body());
                    shutdown.get(5, TimeUnit.SECONDS);
                    assertClosedByClient(socketA);
                }
            }
        }
    }

    @Test
    void requestWaitingForLoadBalancerIsActive() throws Exception {
        CompletableFuture<ServiceInstance> selection = new CompletableFuture<>();
        LoadBalancer loadBalancer = discriminator -> Mono.fromFuture(selection);
        try (ServerSocket server = server();
             DefaultHttpClient client = DefaultHttpClient.builder()
                 .loadBalancer(loadBalancer)
                 .configuration(new DefaultHttpClientConfiguration())
                 .build()) {
            CompletableFuture<HttpResponse<String>> response = exchange(client, "/selected");
            Thread.sleep(200);
            CompletableFuture<?> shutdown = client.connectionManager().shutdownGracefully().toCompletableFuture();
            Thread.sleep(300);
            Assertions.assertFalse(shutdown.isDone(), "The graceful shutdown did not wait for the request whose server is being selected");
            Assertions.assertEquals(OptionalLong.of(1), client.connectionManager().reportActiveTasks());

            selection.complete(ServiceInstance.of("test", URI.create("http://127.0.0.1:" + server.getLocalPort())));
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                readHead(socket.getInputStream());
                respond(socket, "selected");
                Assertions.assertEquals("selected", response.get(5, TimeUnit.SECONDS).body());
                shutdown.get(5, TimeUnit.SECONDS);
                assertClosedByClient(socket);
            }
        }
    }

    private static HttpClientConfiguration configuration(HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion version) {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setVersion(version);
        return configuration;
    }

    private static ServerSocket server() throws IOException {
        ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        // accept fails instead of hanging the build when the client does not connect
        server.setSoTimeout(10000);
        return server;
    }

    private static DefaultHttpClient client(ServerSocket server, HttpClientConfiguration configuration) {
        return DefaultHttpClient.builder()
            .uri(URI.create("http://127.0.0.1:" + server.getLocalPort()))
            .configuration(configuration)
            .build();
    }

    private static CompletableFuture<HttpResponse<String>> exchange(DefaultHttpClient client, String path) {
        return Mono.from(client.exchange(HttpRequest.GET(path), String.class)).toFuture();
    }

    private static void respond(Socket socket, String body) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + body.length() + "\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void assertClosedByClient(Socket socket) throws IOException {
        socket.setSoTimeout(5000);
        try {
            Assertions.assertEquals(-1, socket.getInputStream().read(), "The client sent data instead of closing the connection");
        } catch (SocketTimeoutException e) {
            Assertions.fail("The client did not close the connection");
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
}
