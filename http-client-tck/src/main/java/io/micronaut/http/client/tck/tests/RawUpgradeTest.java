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
package io.micronaut.http.client.tck.tests;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A request that allows upgrades and is answered with {@code 101 Switching Protocols} returns an
 * {@link UpgradedHttpResponse}: the connection carries the new protocol in both directions until
 * it is closed. The JDK client cannot switch a connection and fails such a request up front.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawUpgradeTest {
    static final String SPEC_NAME = "RawUpgradeTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final int LARGE_STREAM_CHUNK = 16 * 1024;
    private static final int LARGE_STREAM_CHUNKS = 1024;
    private static final long LARGE_STREAM_SIZE = (long) LARGE_STREAM_CHUNK * LARGE_STREAM_CHUNKS;

    @Test
    void upgradeSwitchesTheConnection() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                HttpClientException failure = Assertions.assertThrows(HttpClientException.class, () -> exchange(client, upgradeRequest(upstream)));
                Assertions.assertTrue(failure.getMessage().contains("cannot switch"), failure.getMessage());
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(upgradeRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            String head = connection.received().toLowerCase();
            Assertions.assertTrue(head.contains("upgrade: echo"), "The Upgrade header was not relayed: " + head);
            Assertions.assertTrue(head.contains("connection: upgrade"), "Connection does not name the upgrade: " + head);
            connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(101, response.code());
                UpgradedHttpResponse<?> upgraded = UpgradedHttpResponse.unwrap(response);
                Assertions.assertNotNull(upgraded, "Not an upgraded response: " + response.getClass());
                Assertions.assertEquals("echo", upgraded.getProtocol());

                // bytes to the peer, after the switch: a stream that stays open, like the other side of a relay
                Sinks.Many<ReadBuffer> outbound = Sinks.many().unicast().onBackpressureBuffer();
                upgraded.send(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(outbound.asFlux()));
                Assertions.assertEquals(Sinks.EmitResult.OK, outbound.tryEmitNext(ReadBufferFactory.getJdkFactory().copyOf("hello", StandardCharsets.UTF_8)));
                awaitReceived(connection, "hello");
                // bytes from the peer, after the switch
                connection.write("HELLO");
                String echoed = Flux.from(upgraded.byteBody().toByteArrayPublisher())
                    .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                    .blockFirst(Duration.ofSeconds(TIMEOUT_SECONDS));
                Assertions.assertEquals("HELLO", echoed);
                // the stream to the peer ends: the connection is closed
                outbound.tryEmitComplete();
                Assertions.assertTrue(connection.awaitClosed(TIMEOUT_SECONDS), "The connection was not closed when the sent stream ended");
            }
        }
    }

    @Test
    void switchToOneOfTheOfferedProtocols() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            // the protocols offered on two field lines, one of them in a list: the server selects one
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(offeringRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                UpgradedHttpResponse<?> upgraded = UpgradedHttpResponse.unwrap(response);
                Assertions.assertNotNull(upgraded, "Not an upgraded response: " + response.getClass());
                Assertions.assertEquals("echo", upgraded.getProtocol());
            }
        }
    }

    @Test
    void switchToAProtocolThatWasNotOfferedFails() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(offeringRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: example/2\r\nConnection: Upgrade\r\n\r\n");
            ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(HttpClientException.class, failure.getCause());
        }
    }

    @Test
    void switchToAnotherProtocolFails() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(upgradeRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            if (!isJdkClient(client)) {
                RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
                Assertions.assertNotNull(connection, "The client did not connect");
                Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
                connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: other\r\nConnection: Upgrade\r\n\r\n");
            }
            ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(HttpClientException.class, failure.getCause());
        }
    }

    @Test
    void responseWithoutSwitchIsAnOrdinaryResponse() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(upgradeRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nno");
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertNull(UpgradedHttpResponse.unwrap(response));
                Assertions.assertEquals("no", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void bytesSentWithTheSwitchAreTheFirstOfTheBody() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(upgradeRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            // the first bytes of the new protocol in the same write as the 101, so they arrive with it
            connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\nGREETING");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                UpgradedHttpResponse<?> upgraded = UpgradedHttpResponse.unwrap(response);
                Assertions.assertNotNull(upgraded, "Not an upgraded response: " + response.getClass());
                Sinks.Many<ReadBuffer> outbound = Sinks.many().unicast().onBackpressureBuffer();
                upgraded.send(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(outbound.asFlux()));
                // more bytes after the switch, to tell whether the first ones were lost or delayed
                connection.write("MORE");
                Assertions.assertEquals("GREETINGMORE", readAtLeast(upgraded, 12), "The bytes sent with the 101 are not the first of the body");
                outbound.tryEmitComplete();
            }
        }
    }

    @Test
    void largeStreamToThePeerIsSentCompletely() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(upgradeRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            long requestBytes = connection.received().length();
            connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                UpgradedHttpResponse<?> upgraded = UpgradedHttpResponse.unwrap(response);
                Assertions.assertNotNull(upgraded, "Not an upgraded response: " + response.getClass());
                // a body that ends, larger than the socket buffers: the connection is closed once the last of its bytes is sent
                upgraded.send(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(new byte[(int) LARGE_STREAM_SIZE]));
                Assertions.assertTrue(connection.awaitClosed(TIMEOUT_SECONDS * 3), "The connection was not closed when the sent stream ended");
                Assertions.assertEquals(LARGE_STREAM_SIZE, connection.bytesReceived.get() - requestBytes, "Not all bytes of the sent stream arrived");
            }
        }
    }

    @Test
    void largeStreamFromThePeerIsReadCompletely() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(upgradeRequest(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                UpgradedHttpResponse<?> upgraded = UpgradedHttpResponse.unwrap(response);
                Assertions.assertNotNull(upgraded, "Not an upgraded response: " + response.getClass());
                Sinks.Many<ReadBuffer> outbound = Sinks.many().unicast().onBackpressureBuffer();
                upgraded.send(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(outbound.asFlux()));
                // the peer sends a large stream and closes: everything before its close is read
                Thread writer = new Thread(() -> {
                    try {
                        byte[] chunk = new byte[LARGE_STREAM_CHUNK];
                        for (int i = 0; i < LARGE_STREAM_CHUNKS; i++) {
                            connection.write(chunk);
                        }
                        connection.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }, "raw-upstream-writer");
                writer.setDaemon(true);
                writer.start();
                Long read = Flux.from(upgraded.byteBody().toByteArrayPublisher())
                    .map(bytes -> (long) bytes.length)
                    .reduce(0L, Long::sum)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS * 3));
                writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                Assertions.assertEquals(LARGE_STREAM_SIZE, read, "Not all bytes of the received stream were read");
                outbound.tryEmitComplete();
            }
        }
    }

    private static MutableHttpRequest<?> offeringRequest(RawUpstream upstream) {
        return HttpRequest.GET(upstream.uri("/switch"))
            .header(HttpHeaders.CONNECTION, "upgrade")
            .header(HttpHeaders.UPGRADE, "example/1, websocket")
            .header(HttpHeaders.UPGRADE, "echo");
    }

    private static MutableHttpRequest<?> upgradeRequest(RawUpstream upstream) {
        return HttpRequest.GET(upstream.uri("/switch"))
            .header(HttpHeaders.CONNECTION, "upgrade")
            .header(HttpHeaders.UPGRADE, "echo");
    }

    private static boolean isJdkClient(RawHttpClient client) {
        return client.getClass().getName().contains(".jdk.");
    }

    private static void exchange(RawHttpClient client, HttpRequest<?> request) {
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, RawRequestOptions.proxy())).block();
        if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
            byteBodyResponse.close();
        }
    }

    /**
     * Read the bytes the peer sent, until at least {@code n} arrived, or the time is up: then
     * what did arrive, so that the failure shows it. Waits on a latch rather than a reactor
     * timeout: the shared parallel scheduler would create its threads in the leak scope of this
     * test, and a later test that allocates on them fails once the scope is closed.
     */
    private static String readAtLeast(UpgradedHttpResponse<?> upgraded, int n) throws InterruptedException {
        StringBuilder received = new StringBuilder();
        CountDownLatch enough = new CountDownLatch(1);
        Disposable reading = Flux.from(upgraded.byteBody().toByteArrayPublisher())
            .subscribe(bytes -> {
                synchronized (received) {
                    received.append(new String(bytes, StandardCharsets.ISO_8859_1));
                    if (received.length() >= n) {
                        enough.countDown();
                    }
                }
            }, e -> enough.countDown(), enough::countDown);
        try {
            enough.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            reading.dispose();
        }
        synchronized (received) {
            return received.toString();
        }
    }

    private static void awaitReceived(RawUpstream.Connection connection, String text) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!connection.received().endsWith(text)) {
            Assertions.assertTrue(System.nanoTime() < deadline, "Not received: " + text + ", got: " + connection.received());
            Thread.sleep(20);
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }
}
