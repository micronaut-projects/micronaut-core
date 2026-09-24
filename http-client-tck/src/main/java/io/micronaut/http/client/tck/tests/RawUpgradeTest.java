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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
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
