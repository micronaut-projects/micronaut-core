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
package io.micronaut.http.client.tck.tests;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The trailers of a response complete {@link io.micronaut.http.body.ByteBody#trailers()} once
 * the body has ended, and the trailers of a request body are sent after its last bytes. The JDK
 * client can do neither: it sends a request body without its trailers, and its chunked body
 * parser fails on a response with trailers (JDK-8354276), so the response tests skip it.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawTrailersTest {
    static final String SPEC_NAME = "RawTrailersTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final String RESPONSE_WITH_TRAILERS = "HTTP/1.1 200 OK\r\nContent-Type: application/grpc\r\nTransfer-Encoding: chunked\r\n\r\n" +
        "5\r\nhello\r\n0\r\ngrpc-status: 0\r\ngrpc-message: fine\r\n\r\n";

    @Test
    void responseTrailersAreReceived() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(request(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            // the TE header of the request is sent as given
            Assertions.assertTrue(connection.received().toLowerCase(Locale.ROOT).contains("te: trailers\r\n"), connection.received());
            // everything at once: the client may hand the body over fully buffered
            connection.write(RESPONSE_WITH_TRAILERS);
            assertResponse(pending);
        }
    }

    @Test
    void responseTrailersAreReceivedOnAStreamedBody() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(request(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            // the head and the first chunk arrive first, so the body is streamed, and the
            // trailers only arrive once the body is being consumed
            int firstPart = RESPONSE_WITH_TRAILERS.indexOf("0\r\n");
            connection.write(RESPONSE_WITH_TRAILERS.substring(0, firstPart));
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                CompletableFuture<HttpHeaders> trailers = response.byteBody().trailers().toCompletableFuture();
                Assertions.assertFalse(trailers.isDone(), "The trailers completed before the body ended");
                CompletableFuture<String> body = Flux.from(response.byteBody().toByteArrayPublisher())
                    .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                    .collect(StringBuilder::new, StringBuilder::append)
                    .map(StringBuilder::toString)
                    .toFuture();
                connection.write(RESPONSE_WITH_TRAILERS.substring(firstPart));
                Assertions.assertEquals("hello", body.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertTrailers(trailers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void requestTrailersAreSentAfterTheBody() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
            // trailers that are only known once the body has been sent, e.g. a checksum
            CompletableFuture<HttpHeaders> trailers = new CompletableFuture<>();
            CloseableByteBody body = factory.withTrailers(factory.copyOf("hello", StandardCharsets.UTF_8), trailers);
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(HttpRequest.POST(upstream.uri("/trailers"), null), body, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            String head = connection.received().toLowerCase(Locale.ROOT);
            Assertions.assertTrue(head.contains("transfer-encoding: chunked\r\n"), "A body with trailers must be chunked: " + head);
            Assertions.assertFalse(head.contains("content-length:"), "A body with trailers must be chunked: " + head);
            if (isJdkClient(client)) {
                // the JDK client ends the body without its trailers
                awaitReceived(connection, "5\r\nhello\r\n0\r\n\r\n");
            } else {
                awaitReceived(connection, "5\r\nhello\r\n");
                trailers.complete(headers("x-checksum", "abc"));
                awaitReceived(connection, "0\r\nx-checksum: abc\r\n\r\n");
            }
            connection.write("HTTP/1.1 204 No Content\r\n\r\n");
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(204, response.code());
            }
        }
    }

    @Test
    void knownRequestTrailersAreSentWithTheBody() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            if (isJdkClient(client)) {
                return;
            }
            ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
            CloseableByteBody body = factory.withTrailers(factory.copyOf("hello", StandardCharsets.UTF_8), CompletableFuture.completedFuture(headers("x-checksum", "abc")));
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(HttpRequest.POST(upstream.uri("/trailers"), null), body, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            awaitReceived(connection, "5\r\nhello\r\n0\r\nx-checksum: abc\r\n\r\n");
            connection.write("HTTP/1.1 204 No Content\r\n\r\n");
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(204, response.code());
            }
        }
    }

    @Test
    void bodyWithoutTrailersCompletesEmpty() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(request(upstream), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 5\r\n\r\nhello");
            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                CompletableFuture<HttpHeaders> trailers = response.byteBody().trailers().toCompletableFuture();
                Assertions.assertEquals("hello", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
                Assertions.assertTrue(trailers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).isEmpty());
            }
        }
    }

    private static void assertResponse(CompletableFuture<HttpResponse<?>> pending) throws Exception {
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(200, response.code());
            CompletableFuture<HttpHeaders> trailers = response.byteBody().trailers().toCompletableFuture();
            String body = Flux.from(response.byteBody().toByteArrayPublisher())
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString)
                .block(Duration.ofSeconds(TIMEOUT_SECONDS));
            Assertions.assertEquals("hello", body);
            assertTrailers(trailers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    private static void assertTrailers(HttpHeaders trailers) {
        Assertions.assertEquals("0", trailers.get("grpc-status"));
        Assertions.assertEquals("fine", trailers.get("grpc-message"));
    }

    private static MutableHttpRequest<?> request(RawUpstream upstream) {
        return HttpRequest.GET(upstream.uri("/trailers"))
            .header(HttpHeaders.TE, "trailers");
    }

    private static HttpHeaders headers(String name, String value) {
        return new SimpleHttpHeaders(Map.of(name, value), ConversionService.SHARED);
    }

    private static boolean isJdkClient(RawHttpClient client) {
        return client.getClass().getName().contains(".jdk.");
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
