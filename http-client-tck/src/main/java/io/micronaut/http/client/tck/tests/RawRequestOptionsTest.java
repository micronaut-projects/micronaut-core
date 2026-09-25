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
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawRequestOptionsTest {
    static final String SPEC_NAME = "RawRequestOptionsTest";

    private static final byte[] UNCOMPRESSED = "Hello, gzip!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GZIPPED = gzip(UNCOMPRESSED);

    @Test
    void proxyOptionsReturnRedirects() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/redirect-from"), RawRequestOptions.proxy())) {

            Assertions.assertEquals(303, response.code());
            Assertions.assertEquals("/raw-options/redirect-to", response.getHeaders().get(HttpHeaders.LOCATION));
        }
    }

    @Test
    void redirectsCanBeFollowed() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/redirect-from"),
                 RawRequestOptions.proxy().toBuilder().followRedirects(true).build())) {

            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("redirect successful", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void encodedResponseIsNotDecompressed() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/gzip"), RawRequestOptions.proxy())) {

            Assertions.assertEquals("gzip", response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
            Assertions.assertArrayEquals(GZIPPED, response.byteBody().buffer().get().toByteArray());
        }
    }

    @Test
    void redirectedResponseIsNotDecompressed() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/redirect-to-gzip"),
                 RawRequestOptions.proxy().toBuilder().followRedirects(true).build())) {

            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("gzip", response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
            Assertions.assertArrayEquals(GZIPPED, response.byteBody().buffer().get().toByteArray());
        }
    }

    @Test
    void hostHeaderIsComputedFromTheUri() throws Exception {
        URI uri;
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            uri = URI.create(server.getURL().get() + "/raw-options/host");
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(uri).header(HttpHeaders.HOST, "gateway.example"), RawRequestOptions.proxy())) {
                Assertions.assertEquals(uri.getHost() + ":" + uri.getPort(), response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void hostHeaderCanBeRetained() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/host").header(HttpHeaders.HOST, "gateway.example"),
                 RawRequestOptions.proxy().toBuilder().retainHostHeader(true).build())) {

            Assertions.assertEquals("gateway.example", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void responseTimeout() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            long start = System.nanoTime();
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/slow"),
                RawRequestOptions.proxy().toBuilder().responseTimeout(Duration.ofMillis(200)).build()));
            Assertions.assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(4)) < 0);
        }
    }

    @Test
    void cookiesAreNotCarriedBetweenExchanges() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/set-cookie"), RawRequestOptions.proxy())) {
                Assertions.assertEquals("session=user-a; Path=/", response.getHeaders().get(HttpHeaders.SET_COOKIE));
            }
            // a proxy relays exchanges of different users, so it must not keep the cookies an upstream sets
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-options/cookie"), RawRequestOptions.proxy())) {
                Assertions.assertEquals("none", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request, RawRequestOptions options) {
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, options)).block();
        Assertions.assertInstanceOf(MutableByteBodyHttpResponse.class, response);
        return (ByteBodyHttpResponse<?>) response;
    }

    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(data);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Controller("/raw-options")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawOptionsController {
        @Get("/redirect-from")
        HttpResponse<?> redirectFrom() {
            return HttpResponse.seeOther(URI.create("/raw-options/redirect-to"));
        }

        @Get(value = "/redirect-to", produces = MediaType.TEXT_PLAIN)
        String redirectTo() {
            return "redirect successful";
        }

        @Get("/redirect-to-gzip")
        HttpResponse<?> redirectToGzip() {
            return HttpResponse.seeOther(URI.create("/raw-options/gzip"));
        }

        @Get("/gzip")
        HttpResponse<byte[]> gzip() {
            return HttpResponse.ok(GZIPPED)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .contentLength(GZIPPED.length);
        }

        @Get(value = "/host", produces = MediaType.TEXT_PLAIN)
        String host(@Header(HttpHeaders.HOST) String host) {
            return host;
        }

        @Get("/set-cookie")
        HttpResponse<?> setCookie() {
            return HttpResponse.ok().header(HttpHeaders.SET_COOKIE, "session=user-a; Path=/");
        }

        @Get(value = "/cookie", produces = MediaType.TEXT_PLAIN)
        String cookie(HttpRequest<?> request) {
            String cookie = request.getHeaders().get(HttpHeaders.COOKIE);
            return cookie == null ? "none" : cookie;
        }

        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow() {
            return Mono.delay(Duration.ofSeconds(5)).thenReturn("slow");
        }
    }
}
