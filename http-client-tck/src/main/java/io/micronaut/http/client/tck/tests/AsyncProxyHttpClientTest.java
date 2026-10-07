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
import io.micronaut.http.client.AsyncProxyHttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * The exchanges of {@link ProxyHttpClientTest} with an {@link AsyncProxyHttpClient}: injected,
 * the async view of the {@link ProxyHttpClient}, and the default adapter of a client that only
 * has the reactive methods.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class AsyncProxyHttpClientTest {
    static final String SPEC_NAME = "AsyncProxyHttpClientTest";

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy", "reactive adapter"})
    void proxyReturnsTheBodyBytes(String kind) throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncProxyHttpClient client = client(server, kind);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.POST(server.getURL().get() + "/async-proxy-client/echo", "hello").contentType(MediaType.TEXT_PLAIN_TYPE), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("hello", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy", "reactive adapter"})
    void proxyFollowsRedirects(String kind) throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncProxyHttpClient client = client(server, kind);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/async-proxy-client/redirect-from"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("redirect successful", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy", "reactive adapter"})
    void proxyComputesTheHostHeader(String kind) throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncProxyHttpClient client = client(server, kind);
            URI uri = URI.create(server.getURL().get() + "/async-proxy-client/host");
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(uri).header(HttpHeaders.HOST, "gateway.example"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(uri.getHost() + ":" + uri.getPort(), response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy", "reactive adapter"})
    void proxyCanRetainTheHostHeader(String kind) throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncProxyHttpClient client = client(server, kind);
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/async-proxy-client/host").header(HttpHeaders.HOST, "gateway.example"),
                ProxyRequestOptions.builder().retainHostHeader().build())) {
                Assertions.assertEquals("gateway.example", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy", "reactive adapter"})
    void aFailedExchangeFailsTheStage(String kind) throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncProxyHttpClient client = client(server, kind);
            CompletionStage<MutableHttpResponse<?>> stage = client.proxy(HttpRequest.GET("http://localhost:1/unreachable"));
            Assertions.assertThrows(Exception.class, () -> stage.toCompletableFuture().get(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"injected", "toAsyncProxy", "reactive adapter"})
    void cancellingTheStageCancelsTheExchange(String kind) throws Exception {
        try (ServerUnderTest server = server()) {
            AsyncProxyHttpClient client = client(server, kind);
            CompletionStage<MutableHttpResponse<?>> stage = client.proxy(HttpRequest.GET(server.getURL().get() + "/async-proxy-client/host"));
            stage.toCompletableFuture().cancel(true);
            Assertions.assertTrue(stage.toCompletableFuture().isDone());
            // the client goes on
            try (ByteBodyHttpResponse<?> response = proxy(client, HttpRequest.GET(server.getURL().get() + "/async-proxy-client/host"), ProxyRequestOptions.getDefault())) {
                Assertions.assertEquals(200, response.code());
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static AsyncProxyHttpClient client(ServerUnderTest server, String kind) {
        return switch (kind) {
            case "injected" -> server.getApplicationContext().getBean(AsyncProxyHttpClient.class);
            case "toAsyncProxy" -> server.getApplicationContext().getBean(ProxyHttpClient.class).toAsyncProxy();
            default -> {
                ProxyHttpClient proxyHttpClient = server.getApplicationContext().getBean(ProxyHttpClient.class);
                // only the reactive methods: the default async view adapts them
                ProxyHttpClient reactiveOnly = new ProxyHttpClient() {
                    @Override
                    public Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
                        return proxyHttpClient.proxy(request);
                    }

                    @Override
                    public Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request, ProxyRequestOptions options) {
                        return proxyHttpClient.proxy(request, options);
                    }
                };
                yield reactiveOnly.toAsyncProxy();
            }
        };
    }

    private static ByteBodyHttpResponse<?> proxy(AsyncProxyHttpClient client, HttpRequest<?> request, ProxyRequestOptions options) throws Exception {
        MutableHttpResponse<?> response = client.proxy(request, options).toCompletableFuture().get(10, TimeUnit.SECONDS);
        Assertions.assertInstanceOf(MutableByteBodyHttpResponse.class, response);
        return (ByteBodyHttpResponse<?>) response;
    }

    @Controller("/async-proxy-client")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AsyncProxyClientController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body;
        }

        @Get("/redirect-from")
        HttpResponse<?> redirectFrom() {
            return HttpResponse.redirect(URI.create("/async-proxy-client/redirect-to"));
        }

        @Get(value = "/redirect-to", produces = MediaType.TEXT_PLAIN)
        String redirectTo() {
            return "redirect successful";
        }

        @Get(value = "/host", produces = MediaType.TEXT_PLAIN)
        String host(@Header(HttpHeaders.HOST) String host) {
            return host;
        }
    }
}
