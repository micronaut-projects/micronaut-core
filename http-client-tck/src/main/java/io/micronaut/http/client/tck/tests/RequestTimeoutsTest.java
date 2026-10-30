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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RequestTimeouts;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The {@link RequestTimeouts} of a request replace the timeouts of the client configuration for
 * that exchange only.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RequestTimeoutsTest {
    static final String SPEC_NAME = "RequestTimeoutsTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final String PARTIAL_RESPONSE = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\nhello";
    private static final String RESPONSE = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 5\r\n\r\nhello";

    @Test
    void aLongerReadTimeoutOfTheRequest() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server("500ms");
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/slow");
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofSeconds(10)).build());
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(request, String.class)).toFuture();
            RawUpstream.Connection connection = awaitRequest(upstream);
            // longer than the read timeout of the client, and the overall limit derived from it
            TimeUnit.MILLISECONDS.sleep(2000);
            connection.write(RESPONSE);

            HttpResponse<String> response = pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertEquals("hello", response.body());
        }
    }

    @Test
    void aShorterReadTimeoutOfTheRequest() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server("30s");
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/silent");
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofMillis(300)).build());
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(request, String.class)).toFuture();
            awaitRequest(upstream);

            ReadTimeoutException timeout = assertTimeout(pending);
            Assertions.assertFalse(timeout.isHeadersReceived());
        }
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client has no read timeout for the body
    void aShorterReadTimeoutOfTheRequestWhileTheBodyIsRead() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server("30s");
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/stalled");
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofMillis(300)).build());
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(request, String.class)).toFuture();
            awaitRequest(upstream).write(PARTIAL_RESPONSE);

            ReadTimeoutException timeout = assertTimeout(pending);
            Assertions.assertTrue(timeout.isHeadersReceived());
        }
    }

    @Test
    void aRawExchangeTakesTheReadTimeoutOfTheRequest() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server("30s");
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            MutableHttpRequest<?> request = HttpRequest.GET(upstream.uri("/silent"));
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofMillis(300)).build());
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(client.exchange(request, null, null)).toFuture();
            awaitRequest(upstream);

            ReadTimeoutException timeout = assertTimeout(pending);
            Assertions.assertFalse(timeout.isHeadersReceived());
        }
    }

    @Test
    void theRequestTimeoutOfTheRequest() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server("30s");
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/silent");
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().requestTimeout(Duration.ofMillis(300)).build());
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(request, String.class)).toFuture();
            awaitRequest(upstream);
            assertTimeout(pending);

            // the next exchange of the client has the timeouts of the configuration again
            CompletableFuture<HttpResponse<String>> next = Mono.from(client.exchange(HttpRequest.GET("/slow"), String.class)).toFuture();
            RawUpstream.Connection connection = awaitRequest(upstream);
            TimeUnit.MILLISECONDS.sleep(600);
            connection.write(RESPONSE);
            Assertions.assertEquals("hello", next.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).body());
        }
    }

    @Test
    void aRedirectKeepsTheTimeoutsOfTheRequest() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server("30s");
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            MutableHttpRequest<?> request = HttpRequest.GET("/redirect");
            ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofMillis(300)).build());
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(request, String.class)).toFuture();
            awaitRequest(upstream).write("HTTP/1.1 302 Found\r\nLocation: " + upstream.uri("/silent") + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            awaitRequest(upstream);

            assertTimeout(pending);
        }
    }

    private static RawUpstream.Connection awaitRequest(RawUpstream upstream) throws InterruptedException {
        RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
        Assertions.assertNotNull(connection, "The client did not connect");
        Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
        return connection;
    }

    private static ReadTimeoutException assertTimeout(CompletableFuture<?> pending) {
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return Assertions.assertInstanceOf(ReadTimeoutException.class, failure.getCause(), () -> String.valueOf(failure.getCause()));
    }

    private static ServerUnderTest server(String readTimeout) {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            "micronaut.http.client.read-timeout", readTimeout
        ));
    }
}
