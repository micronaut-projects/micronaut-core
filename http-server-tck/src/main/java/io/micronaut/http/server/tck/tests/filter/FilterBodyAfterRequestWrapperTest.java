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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter method with a {@link Body} argument after an earlier filter continued with an
 * {@link HttpRequestWrapper}: it binds the bytes of the wrapped request, or the body the wrapper
 * replaced, like a route argument.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterBodyAfterRequestWrapperTest {
    public static final String SPEC_NAME = "FilterBodyAfterRequestWrapperTest";

    @Test
    void aFilterBindsTheBodyOfTheWrappedRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter original", post(server, "/fbw/plain"));
        }
    }

    @Test
    void aFilterBindsTheBodyTheWrapperReplaced() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("filter SANITIZED", post(server, "/fbw/sanitized"));
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

    @ServerFilter("/fbw/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class WrappingFilter {

        @RequestFilter
        HttpRequest<?> wrap(HttpRequest<?> request) {
            if (request.getPath().endsWith("/sanitized")) {
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
            return new HttpRequestWrapper<>(request);
        }
    }

    @ServerFilter("/fbw/**")
    @Order(20)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {

        @RequestFilter
        HttpResponse<String> body(@Body String body) {
            return HttpResponse.ok("filter " + body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Controller("/fbw")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    static class BodyController {

        @Post("/plain")
        String plain(@Body String body) {
            return "route " + body;
        }

        @Post("/sanitized")
        String sanitized(@Body String body) {
            return "route " + body;
        }
    }
}
