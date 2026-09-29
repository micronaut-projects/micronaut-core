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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class AsyncRawHttpClientTest {
    static final String SPEC_NAME = "AsyncRawHttpClientTest";

    @Test
    void injectedClientExchangesRawBytes() throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncRawHttpClient client = server.getApplicationContext().getBean(AsyncRawHttpClient.class);
            assertEcho(client, server);
        }
    }

    @Test
    void createdClientExchangesRawBytes() throws Exception {
        try (ServerUnderTest server = server();
             AsyncRawHttpClient client = AsyncRawHttpClient.create(server.getURL().get().toURI())) {
            assertEcho(client, server);
        }
    }

    @Test
    void registryClientExchangesRawBytes() throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncRawHttpClient client = server.getApplicationContext().getBean(RawHttpClientRegistry.class)
                .getAsyncRawClient(HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_1_1), server.getURL().get().toString(), null);
            assertEcho(client, server);
        }
    }

    @Test
    void exchangeWithOptions() throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncRawHttpClient client = server.getApplicationContext().getBean(AsyncRawHttpClient.class);
            HttpResponse<?> response = client.exchange(HttpRequest.GET(server.getURL().get() + "/async-raw/redirect"), null, RawRequestOptions.proxy())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
            try (ByteBodyHttpResponse<?> byteBodyResponse = Assertions.assertInstanceOf(MutableByteBodyHttpResponse.class, response)) {
                Assertions.assertEquals(303, byteBodyResponse.code());
                Assertions.assertEquals("/async-raw/echo", byteBodyResponse.getHeaders().get(HttpHeaders.LOCATION));
            }
        }
    }

    @Test
    void exchangeFailure() throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncRawHttpClient client = server.getApplicationContext().getBean(AsyncRawHttpClient.class);
            URI unreachable = URI.create("http://127.0.0.1:" + unusedPort() + "/async-raw/echo");
            Assertions.assertTrue(client.exchange(HttpRequest.GET(unreachable), null)
                .toCompletableFuture()
                .handle((response, error) -> error != null)
                .get(10, TimeUnit.SECONDS));
        }
    }

    private static int unusedPort() throws java.io.IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void assertEcho(AsyncRawHttpClient client, ServerUnderTest server) throws Exception {
        HttpResponse<?> response = client.exchange(
                HttpRequest.POST(server.getURL().get() + "/async-raw/echo", null).contentType(MediaType.TEXT_PLAIN_TYPE),
                ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("hello".getBytes(StandardCharsets.UTF_8)))
            .toCompletableFuture().get(10, TimeUnit.SECONDS);
        try (ByteBodyHttpResponse<?> byteBodyResponse = Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response)) {
            Assertions.assertEquals(200, byteBodyResponse.code());
            Assertions.assertEquals("hello", byteBodyResponse.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Controller("/async-raw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncRawController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body;
        }

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.seeOther(URI.create("/async-raw/echo"));
        }
    }
}
