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
package io.micronaut.http.server.tck.tests.filter.options;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class OptionsFilterTest {
    private static final String SPEC_NAME = "OptionsFilterTest";

    @Test
    public void optionsByDefaultResponds405() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.OPTIONS("/foo/bar"))
            .assertion(AssertionUtils.assertThrowsStatus(HttpStatus.METHOD_NOT_ALLOWED))
            .run();
    }

    @Test
    public void getTest() throws IOException {
        assertion(HttpRequest.GET("/foo/bar"),
            (server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .build()));
    }

    @Test
    public void optionsRoute() throws IOException {
        assertion(HttpRequest.OPTIONS("/options/route"),
                (server, request) ->
                        AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                                .status(HttpStatus.I_AM_A_TEAPOT)
                                .build()));
    }

    @Test
    public void postTest() throws IOException {
        assertion(HttpRequest.POST("/foo/bar", Collections.emptyMap()),
            (server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .build()));
    }

    @Test
    public void optionsTest() throws IOException {
        assertion(HttpRequest.OPTIONS("/foo/bar"),
            (server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                        .assertResponse(httpResponse -> {
                            assertNotNull(httpResponse.getHeaders().get(HttpHeaders.ALLOW));
                            assertNotNull(httpResponse.getHeaders().getAll(HttpHeaders.ALLOW));
                            assertEquals(4, httpResponse.getHeaders().getAll(HttpHeaders.ALLOW).size());
                            assertTrue(httpResponse.getHeaders().getAll(HttpHeaders.ALLOW).stream().anyMatch(v -> v.equals(HttpMethod.GET.toString())));
                            assertTrue(httpResponse.getHeaders().getAll(HttpHeaders.ALLOW).stream().anyMatch(v -> v.equals(HttpMethod.POST.toString())));
                            assertTrue(httpResponse.getHeaders().getAll(HttpHeaders.ALLOW).stream().anyMatch(v -> v.equals(HttpMethod.OPTIONS.toString())));
                            assertTrue(httpResponse.getHeaders().getAll(HttpHeaders.ALLOW).stream().anyMatch(v -> v.equals(HttpMethod.HEAD.toString())));
                        })
                    .build()));
    }

    private static void assertion(HttpRequest<?> request, BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .configuration(Collections.singletonMap("micronaut.server.dispatch-options-requests", StringUtils.TRUE))
            .request(request)
            .assertion(assertion)
            .run();
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {
        @Get("/foo/{id}")
        @Status(HttpStatus.OK)
        public void fooGet(String id) {
        }

        @Post("/foo/{id}")
        @Status(HttpStatus.CREATED)
        public void fooPost(String id) {
        }

        @Options("/options/route")
        @Status(HttpStatus.I_AM_A_TEAPOT)
        public void optionsRoute() {
        }

    }
}
