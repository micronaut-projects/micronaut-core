/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class RequestFilterCompletionStageFutureProceedTest {
    public static final String SPEC_NAME = "RequestFilterCompletionStageFutureProceedTest";

    @Test
    public void requestFilterProceedWithCompletableFuture() throws IOException {
        TestScenario.builder()
                .specName(SPEC_NAME)
                .request(HttpRequest.GET("/foobar").header("X-FOOBAR", "123"))
                .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.ACCEPTED)
                        .build()))
                .run();

        TestScenario.builder()
                .specName(SPEC_NAME)
                .request(HttpRequest.GET("/foobar"))
                .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.UNAUTHORIZED)
                        .build()))
                .run();
    }

    /*
    //tag::clazz[]
    @ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
    class FooBarFilter {
    //end::clazz[]
    */
    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
    static class FooBarFilter {
    //tag::methods[]
        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> filter(@NonNull HttpRequest<?> request) {
            if (request.getHeaders().contains("X-FOOBAR")) {
                // proceed
                return CompletableFuture.completedFuture(null);
            } else {
                return CompletableFuture.completedFuture(HttpResponse.unauthorized());
            }
        }
    }
    //end::methods[]

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/foobar")
    static class FooBarController {
        @Get
        @Status(HttpStatus.ACCEPTED)
        void index() {
            // no-op
        }
    }
}
