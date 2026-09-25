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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * The response timeout of {@link RawRequestOptions} can shorten the wait for the response, and
 * the read timeout the client is configured with still applies.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawResponseTimeoutTest {
    static final String SPEC_NAME = "RawResponseTimeoutTest";

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);
    private static final long UPSTREAM_DELAY_MILLIS = 2000;

    @Test
    void longerResponseTimeoutDoesNotOutlastTheReadTimeout() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            Assertions.assertThrows(HttpClientException.class, () -> exchange(client, slowRequest(server),
                RawRequestOptions.proxy().toBuilder().responseTimeout(Duration.ofSeconds(10)).build()).close());
        }
    }

    @Test
    void shorterResponseTimeoutFailsBeforeTheReadTimeout() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            long start = System.nanoTime();
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, slowRequest(server),
                RawRequestOptions.proxy().toBuilder().responseTimeout(Duration.ofMillis(100)).build()).close());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            Assertions.assertTrue(elapsed.compareTo(READ_TIMEOUT) < 0, "The exchange failed after " + elapsed);
        }
    }

    @Test
    void readTimeoutAppliesWithoutAResponseTimeout() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            Assertions.assertThrows(HttpClientException.class, () -> exchange(client, slowRequest(server), RawRequestOptions.proxy()).close());
        }
    }

    private static HttpRequest<?> slowRequest(ServerUnderTest server) {
        return HttpRequest.GET(server.getURL().get() + "/raw-response-timeout/slow?delay=" + UPSTREAM_DELAY_MILLIS);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            "micronaut.http.client.read-timeout", READ_TIMEOUT.toMillis() + "ms"
        ));
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request, RawRequestOptions options) {
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, options)).block();
        Assertions.assertInstanceOf(MutableByteBodyHttpResponse.class, response);
        return (ByteBodyHttpResponse<?>) response;
    }

    @Controller("/raw-response-timeout")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay) {
            return Mono.delay(Duration.ofMillis(delay)).thenReturn("slow");
        }
    }
}
