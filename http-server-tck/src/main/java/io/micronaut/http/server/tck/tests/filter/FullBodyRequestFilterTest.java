/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Request Filter tests that require Netty specifics.
 *
 * This is so they can be ignored in Servlet, etc where Netty isn't involved.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FullBodyRequestFilterTest {
    public static final String SPEC_NAME = "FullBodyRequestFilterTest";

    @Test
    public void requestFilterBinding() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.POST("/request-filter/binding", "{\"foo\":10}").contentType(MediaType.APPLICATION_JSON_TYPE))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("application/json {\"foo\":10}")
                    .build());
                Assertions.assertEquals(
                    List.of("binding application/json {\"foo\":10}"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })

            .run();
    }

    @ServerFilter
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyServerFilter {

        List<String> events = new ArrayList<>();

        @RequestFilter("/request-filter/binding")
        public void requestFilterBinding(
            @Header String contentType,
            @Body byte[] bytes,
            FilterContinuation<HttpResponse<?>> continuation) {
            events.add("binding " + contentType + " " + new String(bytes, StandardCharsets.UTF_8));
            continuation.proceed();
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {

        @Post("/request-filter/binding")
        public String requestFilterBinding(@Header String contentType, @Body byte[] bytes) {
            return contentType + " " + new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
