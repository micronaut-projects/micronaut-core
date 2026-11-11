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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class ProxyHttpClientTest {
    static final String SPEC_NAME = "ProxyHttpClientTest";

    @Test
    void proxyReturnsTheBodyBytes() throws Exception {
        try (ServerUnderTest server = server()) {
            ProxyHttpClient client = server.getApplicationContext().getBean(ProxyHttpClient.class);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.POST(server.getURL().get() + "/proxy-client/echo", "hello").contentType(MediaType.TEXT_PLAIN_TYPE), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("hello", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void proxyFollowsRedirects() throws Exception {
        try (ServerUnderTest server = server()) {
            ProxyHttpClient client = server.getApplicationContext().getBean(ProxyHttpClient.class);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/proxy-client/redirect-from"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("redirect successful", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void proxyComputesTheHostHeader() throws Exception {
        try (ServerUnderTest server = server()) {
            ProxyHttpClient client = server.getApplicationContext().getBean(ProxyHttpClient.class);
            URI uri = URI.create(server.getURL().get() + "/proxy-client/host");
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(uri).header(HttpHeaders.HOST, "gateway.example"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(uri.getHost() + ":" + uri.getPort(), response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void proxyCanRetainTheHostHeader() throws Exception {
        try (ServerUnderTest server = server()) {
            ProxyHttpClient client = server.getApplicationContext().getBean(ProxyHttpClient.class);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/proxy-client/host").header(HttpHeaders.HOST, "gateway.example"),
                ProxyRequestOptions.builder().retainHostHeader().build())) {
                Assertions.assertEquals("gateway.example", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void proxyDoesNotCarryCookiesBetweenRequests() throws Exception {
        try (ServerUnderTest server = server()) {
            ProxyHttpClient client = server.getApplicationContext().getBean(ProxyHttpClient.class);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/proxy-client/set-cookie"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals("session=user-a; Path=/", response.getHeaders().get(HttpHeaders.SET_COOKIE));
            }
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/proxy-client/cookie"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals("none", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static ByteBodyHttpResponse<?> proxy(ProxyHttpClient client, HttpRequest<?> request, ProxyRequestOptions options) {
        MutableHttpResponse<?> response = Mono.from(client.proxy(request, options)).block();
        Assertions.assertInstanceOf(MutableByteBodyHttpResponse.class, response);
        return (ByteBodyHttpResponse<?>) response;
    }

    @Controller("/proxy-client")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProxyClientController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body;
        }

        @Get("/redirect-from")
        HttpResponse<?> redirectFrom() {
            return HttpResponse.redirect(URI.create("/proxy-client/redirect-to"));
        }

        @Get(value = "/redirect-to", produces = MediaType.TEXT_PLAIN)
        String redirectTo() {
            return "redirect successful";
        }

        @Get("/set-cookie")
        HttpResponse<?> setCookie() {
            return HttpResponse.ok().header(HttpHeaders.SET_COOKIE, "session=user-a; Path=/");
        }

        @Get(value = "/cookie", produces = MediaType.TEXT_PLAIN)
        String cookie(io.micronaut.http.HttpRequest<?> request) {
            String cookie = request.getHeaders().get(HttpHeaders.COOKIE);
            return cookie == null ? "none" : cookie;
        }

        @Get(value = "/host", produces = MediaType.TEXT_PLAIN)
        String host(@Header(HttpHeaders.HOST) String host) {
            return host;
        }
    }
}
