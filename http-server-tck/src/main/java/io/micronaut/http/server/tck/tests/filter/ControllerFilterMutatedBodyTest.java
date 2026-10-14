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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The body a controller binds is the body of the request a filter method continued with: the
 * bytes of a server request with another body, or none when a filter cleared the body of a
 * request, even of a wrapper that has no mutable view.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ControllerFilterMutatedBodyTest {
    public static final String SPEC_NAME = "ControllerFilterMutatedBodyTest";

    @Test
    void theBodyOfAServerRequestAFilterContinuedWithIsBound() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("body REPLACED", post(server, "/cmb/replace/required"));
            assertEquals("body REPLACED", post(server, "/cmb/replace/nullable"));
        }
    }

    @Test
    void aRequiredBodyIsNotBoundFromTheBytesOfARequestWhoseBodyAFilterCleared() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, text("/cmb/clear/required"), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
            assertEquals("body null", post(server, "/cmb/clear/nullable"));
        }
    }

    private static MutableHttpRequest<String> text(String path) {
        return HttpRequest.POST(path, "original").contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static String post(ServerUnderTest server, String path) {
        HttpResponse<String> response = server.exchange(text(path), String.class);
        assertEquals(200, response.code());
        return response.body();
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * A server request with another body: the bytes that the body binders of the route read.
     *
     * @param <B> The body type
     */
    static final class ReplacedBody<B> extends HttpRequestWrapper<B> implements ServerHttpRequest<B> {
        private final ByteBody body;

        ReplacedBody(HttpRequest<B> request, ByteBody body) {
            super(request);
            this.body = body;
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public Optional<B> getBody() {
            // not decoded yet
            return Optional.empty();
        }
    }

    @ServerFilter("/cmb/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplacingFilter {

        @RequestFilter
        HttpRequest<?> replace(ServerHttpRequest<?> request) {
            if (request.getPath().startsWith("/cmb/replace/")) {
                CloseableByteBody body = request.byteBodyFactory().copyOf("REPLACED", StandardCharsets.UTF_8);
                if (request instanceof LifecycleHttpRequest<?> lifecycle) {
                    // the request owns its body: released when the request ends
                    lifecycle.addDisposalResource(body::close);
                }
                return new ReplacedBody<>(request, body);
            }
            // a request that has no mutable view
            return new HttpRequestWrapper<>(request);
        }
    }

    @ServerFilter("/cmb/clear/**")
    @Order(20)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClearingFilter {

        @RequestFilter
        HttpRequest<?> clear(MutableHttpRequest<?> request) {
            return request.body(null);
        }
    }

    @Controller("/cmb")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    static class BodyController {

        @Post("/replace/required")
        String replaceRequired(@Body String body) {
            return "body " + body;
        }

        @Post("/replace/nullable")
        String replaceNullable(@Body @Nullable String body) {
            return "body " + body;
        }

        @Post("/clear/required")
        String clearRequired(@Body String body) {
            return "body " + body;
        }

        @Post("/clear/nullable")
        String clearNullable(@Body @Nullable String body) {
            return "body " + body;
        }
    }
}
