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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter method with a nullable {@link Body} argument after an earlier filter cleared the body
 * of the request: the argument binds no body, and the route answers.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterBodyAfterClearedBodyTest {
    public static final String SPEC_NAME = "FilterBodyAfterClearedBodyTest";

    @Test
    void aNullableBodyArgumentBindsTheClearedBody() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            HttpResponse<String> response = server.exchange(HttpRequest.POST("/fbc", "original").contentType(MediaType.TEXT_PLAIN_TYPE), String.class);
            assertEquals(200, response.code());
            assertEquals("route", response.body());
        }
    }

    @ServerFilter("/fbc/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClearingFilter {

        @RequestFilter
        HttpRequest<?> clear(HttpRequest<?> request) {
            return request.mutate().body(null);
        }
    }

    @ServerFilter("/fbc/**")
    @Order(20)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {

        @RequestFilter
        HttpRequest<?> body(HttpRequest<?> request, @Nullable @Body String ignored) {
            return request;
        }
    }

    @Controller("/fbc")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    static class ClearedController {

        @Post
        String post() {
            return "route";
        }
    }
}
