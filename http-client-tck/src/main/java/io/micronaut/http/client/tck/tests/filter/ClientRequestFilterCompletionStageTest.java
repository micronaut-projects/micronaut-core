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
package io.micronaut.http.client.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ClientRequestFilterCompletionStageTest {
    public static final String SPEC_NAME = "ClientRequestFilterCompletionStageTest";

    @Test
    public void requestFilterContinuationCompletionStage() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/client-completion-stage/stage"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("bar")
                    .build());
                Assertions.assertEquals(
                    List.of("stage 200"),
                    server.getApplicationContext().getBean(StageClientFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void requestFilterContinuationCompletableFuture() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/client-completion-stage/future"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("replaced")
                    .build());
                Assertions.assertEquals(
                    List.of("future 200"),
                    server.getApplicationContext().getBean(StageClientFilter.class).events
                );
            })
            .run();
    }

    @ClientFilter
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class StageClientFilter {
        final List<String> events = new CopyOnWriteArrayList<>();

        @RequestFilter("/client-completion-stage/stage")
        public CompletionStage<HttpResponse<?>> stage(MutableHttpRequest<?> request, FilterContinuation<CompletionStage<HttpResponse<?>>> continuation) {
            request.header("foo", "bar");
            return continuation.proceed().thenApply(response -> {
                events.add("stage " + response.code());
                return response;
            });
        }

        @RequestFilter("/client-completion-stage/future")
        public CompletableFuture<HttpResponse<?>> future(MutableHttpRequest<?> request, FilterContinuation<CompletableFuture<HttpResponse<?>>> continuation) {
            // the request the continuation proceeds with
            return continuation.request(request.header("foo", "replaced"))
                .proceed()
                .thenApply(response -> {
                    events.add("future " + response.code());
                    return response;
                });
        }
    }

    @Controller("/client-completion-stage")
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class StageController {
        @Get("/stage")
        public String stage(HttpRequest<?> request) {
            return request.getHeaders().get("foo");
        }

        @Get("/future")
        public String future(HttpRequest<?> request) {
            return request.getHeaders().get("foo");
        }
    }
}
