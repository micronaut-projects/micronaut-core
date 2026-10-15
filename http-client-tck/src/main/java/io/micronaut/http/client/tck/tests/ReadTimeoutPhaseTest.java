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

import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A read timeout says whether it elapsed while the response was awaited, or while its body was
 * read: only the first may be retried, and a relay can still answer it with a 504.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class ReadTimeoutPhaseTest {
    static final String SPEC_NAME = "ReadTimeoutPhaseTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final String PARTIAL_RESPONSE = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\nhello";

    @Test
    void rawTimeoutBeforeTheHeaders() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = rawExchange(client, upstream.uri("/silent"));
            awaitRequest(upstream);

            ReadTimeoutException timeout = assertTimeout(pending);
            Assertions.assertFalse(timeout.isHeadersReceived());
        }
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client has no read timeout for the body
    void rawTimeoutWhileTheBodyIsRead() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = rawExchange(client, upstream.uri("/stalled"));
            awaitRequest(upstream).write(PARTIAL_RESPONSE);

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                ReadTimeoutException timeout = assertTimeout(response.byteBody().buffer());
                Assertions.assertTrue(timeout.isHeadersReceived());
            }
        }
    }

    @Test
    void bufferingClientTimeoutBeforeTheHeaders() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(HttpRequest.GET("/silent"), String.class)).toFuture();
            awaitRequest(upstream);

            ReadTimeoutException timeout = assertTimeout(pending);
            Assertions.assertFalse(timeout.isHeadersReceived());
        }
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client has no read timeout for the body
    void bufferingClientTimeoutWhileTheBodyIsRead() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, upstream.uri("/").toURL())) {
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(HttpRequest.GET("/stalled"), String.class)).toFuture();
            awaitRequest(upstream).write(PARTIAL_RESPONSE);

            ReadTimeoutException timeout = assertTimeout(pending);
            Assertions.assertTrue(timeout.isHeadersReceived());
        }
    }

    private static CompletableFuture<HttpResponse<?>> rawExchange(RawHttpClient client, URI uri) {
        return Mono.<HttpResponse<?>>from(client.exchange(HttpRequest.GET(uri), null, null, RawRequestOptions.proxy())).toFuture();
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

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            "micronaut.http.client.read-timeout", "500ms"
        ));
    }
}
