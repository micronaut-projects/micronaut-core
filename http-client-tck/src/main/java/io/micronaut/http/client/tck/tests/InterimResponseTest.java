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
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * An interim (1xx) response of the upstream is not the response of the exchange: the final
 * response that follows it is.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class InterimResponseTest {
    static final String SPEC_NAME = "InterimResponseTest";
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void earlyHintsPrecedeTheResponse() throws Exception {
        assertFinalResponse("/hints",
            "HTTP/1.1 103 Early Hints\r\nLink: </style.css>; rel=preload\r\n\r\n" +
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok");
    }

    @Test
    void severalInterimResponsesPrecedeTheResponse() throws Exception {
        assertFinalResponse("/processing",
            "HTTP/1.1 102 Processing\r\n\r\n" +
            "HTTP/1.1 103 Early Hints\r\nLink: </style.css>; rel=preload\r\n\r\n" +
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok");
    }

    @Test
    void unexpectedContinuePrecedesTheResponse() throws Exception {
        assertFinalResponse("/continue",
            "HTTP/1.1 100 Continue\r\n\r\n" +
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok");
    }

    private static void assertFinalResponse(String path, String upstreamResponse) throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(HttpRequest.GET(upstream.uri(path)), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write(upstreamResponse);

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertFalse(response.getHeaders().contains("Link"), "The headers of the interim response are not the response headers");
                Assertions.assertEquals("ok", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }
}
