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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A handler route that consumes every type ({@code consumesAll()}) reads its body as a route that
 * consumes {@code *}{@code /*}: without a content type with the reader of any type, and with a
 * content type with the reader of that type.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteConsumesAllTest {
    public static final String SPEC_NAME = "HandlerRouteConsumesAllTest";

    @Test
    void aBodyOfACustomTypeIsRead() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn-all/string", "hello").contentType("application/x-custom"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("string hello")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn-all/custom", "Fred").contentType("application/x-custom"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("custom Fred")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn-all/pojo", "{\"name\":\"Fred\"}").contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("pojo Fred")
                .build());
        }
    }

    @Test
    void aBodyWithoutAContentTypeIsRead() throws IOException, InterruptedException {
        try (ServerUnderTest server = server()) {
            Optional<Integer> port = server.getPort();
            // the HTTP client of the TCK sends a content type: a request without one needs a socket
            assumeTrue(port.isPresent(), "The server has no port");
            // the JDK client of this case trusts no self-signed certificate of a TLS server
            assumeFalse(server.getApplicationContext().getProperty("micronaut.server.ssl.enabled", Boolean.class).orElse(false), "The server speaks plain HTTP");
            String base = server.getScheme().orElse("http") + "://localhost:" + port.get();
            try (HttpClient client = HttpClient.newHttpClient()) {
                assertEquals("pojo Fred", postWithoutContentType(client, base + "/fn-all/pojo", "{\"name\":\"Fred\"}"));
                assertEquals("string hello", postWithoutContentType(client, base + "/fn-all/string", "hello"));
            }
        }
    }

    private static String postWithoutContentType(HttpClient client, String uri, String body) throws IOException, InterruptedException {
        java.net.http.HttpResponse<String> response = client.send(
            java.net.http.HttpRequest.newBuilder(URI.create(uri)).POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Introspected
    @ReflectiveAccess
    record Pojo(@Nullable String name) {
    }

    record Custom(String name) {
    }

    @Singleton
    @Consumes("application/x-custom")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CustomReader implements MessageBodyReader<Custom> {
        @Override
        public Custom read(Argument<Custom> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            try {
                return new Custom(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Cannot read the body", e);
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private static HttpResponse<?> text(String text) {
            return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/fn-all/string", Argument.of(String.class), (request, pathVariables, body) -> text("string " + body))
                .consumesAll();
            routes.POST("/fn-all/pojo", Argument.of(Pojo.class), (request, pathVariables, body) -> text("pojo " + (body == null ? null : body.name())))
                .consumesAll();
            routes.POST("/fn-all/custom", Argument.of(Custom.class), (request, pathVariables, body) -> text("custom " + (body == null ? null : body.name())))
                .consumesAll();
        }
    }
}
