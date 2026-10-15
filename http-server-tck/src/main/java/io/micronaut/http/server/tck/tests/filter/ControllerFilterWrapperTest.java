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
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter method that continues with a wrapper that has no mutable view, and a later filter
 * method that changes the request through its {@link MutableHttpRequest} parameter: the body the
 * wrapper replaced stays replaced, and a cookie the later filter adds reaches the route.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ControllerFilterWrapperTest {
    public static final String SPEC_NAME = "ControllerFilterWrapperTest";

    @Test
    void theBodyAWrapperReplacedIsBoundNotTheBytesOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("body SANITIZED", post(server, "/cfw/sanitized"));
            assertEquals("body SANITIZED", post(server, "/cfw/sanitized/mutated"));
        }
    }

    @Test
    void aLaterFilterAddsACookieToTheRequestAWrapperWraps() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("session abc", post(server, "/cfw/cookie"));
        }
    }

    private static String post(ServerUnderTest server, String path) {
        HttpResponse<String> response = server.exchange(HttpRequest.POST(path, "original").contentType(MediaType.TEXT_PLAIN_TYPE), String.class);
        assertEquals(200, response.code());
        return response.body();
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @ServerFilter("/cfw/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class WrappingFilter {

        @RequestFilter
        HttpRequest<?> wrap(HttpRequest<?> request) {
            if (request.getPath().startsWith("/cfw/sanitized")) {
                // a wrapper with another body object and no mutable view, e.g. a sanitized payload
                return new HttpRequestWrapper<Object>((HttpRequest<Object>) request) {
                    @Override
                    public Optional<Object> getBody() {
                        return Optional.of("SANITIZED");
                    }

                    @Override
                    public <T> Optional<T> getBody(Class<T> type) {
                        return type.isInstance("SANITIZED") ? Optional.of(type.cast("SANITIZED")) : Optional.empty();
                    }
                };
            }
            // a request that has no mutable view
            return new HttpRequestWrapper<>(request);
        }
    }

    @ServerFilter("/cfw/**")
    @Order(20)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MutatingFilter {

        @RequestFilter
        void mutate(MutableHttpRequest<?> request) {
            if (request.getPath().endsWith("/mutated")) {
                request.getHeaders().add("X-Mutated", "true");
            } else if (request.getPath().endsWith("/cookie")) {
                request.cookie(Cookie.of("session", "abc"));
            }
        }
    }

    @Controller("/cfw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    static class WrapperController {

        @Post("/sanitized")
        String sanitized(@Body String body) {
            return "body " + body;
        }

        @Post("/sanitized/mutated")
        String sanitizedMutated(@Body String body) {
            return "body " + body;
        }

        @Post("/cookie")
        String cookie(@CookieValue("session") String session) {
            return "session " + session;
        }
    }
}
