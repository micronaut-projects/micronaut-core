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
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Closing a client that shares the event loop of another client fails the requests that wait for
 * a connection, and opens no connection for them.
 */
class ShutdownFailsWaitersTest {

    @Test
    void shutdownFailsQueuedAcquires() throws Exception {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setMaxConcurrentHttp1Connections(1);
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             DefaultHttpClient owner = DefaultHttpClient.builder().uri(URI.create("http://127.0.0.1:" + server.getLocalPort())).build()) {
            DefaultHttpClient client = DefaultHttpClient.builder()
                .uri(URI.create("http://127.0.0.1:" + server.getLocalPort()))
                .configuration(configuration)
                .eventLoopOf(owner)
                .build();
            CompletableFuture<?> first = Mono.from(client.exchange(HttpRequest.GET("/first"), String.class)).toFuture();
            try (Socket socket = server.accept()) {
                readHead(socket.getInputStream());
                CompletableFuture<?> waiting = Mono.from(client.exchange(HttpRequest.GET("/waiting"), String.class)).toFuture();
                // let the second request queue for the only connection
                Thread.sleep(500);
                Assertions.assertFalse(waiting.isDone());

                client.close();

                Assertions.assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
                Assertions.assertThrows(ExecutionException.class, () -> waiting.get(5, TimeUnit.SECONDS),
                    "The request that waited for a connection was not failed when the client closed");
                server.setSoTimeout(1000);
                try (Socket unexpected = server.accept()) {
                    Assertions.fail("The closed client opened a connection: " + unexpected);
                } catch (SocketTimeoutException expected) {
                    // no connection
                }
            }
        }
    }

    @Test
    void eachWaiterGetsItsOwnException() throws Exception {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setMaxConcurrentHttp1Connections(1);
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             DefaultHttpClient owner = DefaultHttpClient.builder().uri(URI.create("http://127.0.0.1:" + server.getLocalPort())).build()) {
            DefaultHttpClient client = DefaultHttpClient.builder()
                .uri(URI.create("http://127.0.0.1:" + server.getLocalPort()))
                .configuration(configuration)
                .eventLoopOf(owner)
                .build();
            CompletableFuture<?> first = Mono.from(client.exchange(HttpRequest.GET("/first"), String.class)).toFuture();
            try (Socket socket = server.accept()) {
                readHead(socket.getInputStream());
                CompletableFuture<?> waiting1 = Mono.from(client.exchange(HttpRequest.GET("/waiting1"), String.class)).toFuture();
                CompletableFuture<?> waiting2 = Mono.from(client.exchange(HttpRequest.GET("/waiting2"), String.class)).toFuture();
                Thread.sleep(500);
                Assertions.assertFalse(waiting1.isDone());
                Assertions.assertFalse(waiting2.isDone());

                client.close();

                Assertions.assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
                UnprocessedRequestException e1 = unprocessed(waiting1);
                UnprocessedRequestException e2 = unprocessed(waiting2);
                Assertions.assertNotSame(e1, e2, "Two waiting requests were failed with the same exception instance");
            }
        }
    }

    @Test
    void closeAfterSharedEventLoopTerminatedDoesNotThrow() throws Exception {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setMaxConcurrentHttp1Connections(1);
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            DefaultHttpClient owner = DefaultHttpClient.builder().uri(URI.create("http://127.0.0.1:" + server.getLocalPort())).build();
            DefaultHttpClient client = DefaultHttpClient.builder()
                .uri(URI.create("http://127.0.0.1:" + server.getLocalPort()))
                .configuration(configuration)
                .eventLoopOf(owner)
                .build();
            CompletableFuture<?> first = Mono.from(client.exchange(HttpRequest.GET("/first"), String.class)).toFuture();
            try (Socket socket = server.accept()) {
                readHead(socket.getInputStream());
                CompletableFuture<?> waiting = Mono.from(client.exchange(HttpRequest.GET("/waiting"), String.class)).toFuture();
                Thread.sleep(500);
                Assertions.assertFalse(waiting.isDone());

                // the shared group goes away before the client that uses it
                owner.close();
                Assertions.assertTrue(owner.connectionManager().eventLoopGroup().awaitTermination(10, TimeUnit.SECONDS));

                Assertions.assertDoesNotThrow(client::close);

                Assertions.assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
                unprocessed(waiting);
            }
        }
    }

    private static UnprocessedRequestException unprocessed(CompletableFuture<?> future) {
        ExecutionException e = Assertions.assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS),
            "The request that waited for a connection was not failed when the client closed");
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnprocessedRequestException u) {
                return u;
            }
        }
        return Assertions.fail("Not failed with an UnprocessedRequestException", e);
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
