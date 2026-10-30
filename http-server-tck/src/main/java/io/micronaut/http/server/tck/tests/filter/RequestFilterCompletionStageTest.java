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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestFilterCompletionStageTest {
    public static final String SPEC_NAME = "RequestFilterCompletionStageTest";

    @Test
    public void completionStageContinuations() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/completion-stage").header("X-FOOBAR", "123"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("OK replaced")
                .headers(Map.of(
                    "X-Around", "around",
                    "X-Around-Future", "around",
                    "X-Around-Blocking", "around",
                    "X-Response", "response"
                ))
                .build()))
            .run();
    }

    @Test
    public void completionStageFilterReturnsResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/completion-stage"))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .build()))
            .run();
    }

    @Test
    public void failedDownstreamFailsTheContinuationStage() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/completion-stage/failing").header("X-FOOBAR", "123"))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .headers(Map.of("X-Around", "around"))
                .build()))
            .run();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/completion-stage/**")
    static class CompletionStageFilter {

        private final Executor executor;

        CompletionStageFilter(@Named(TaskExecutors.BLOCKING) Executor executor) {
            this.executor = executor;
        }

        @RequestFilter
        CompletionStage<HttpResponse<?>> around(FilterContinuation<CompletionStage<HttpResponse<?>>> continuation) {
            return continuation.proceed().thenApply(response -> ((MutableHttpResponse<?>) response).header("X-Around", "around"));
        }

        @RequestFilter
        CompletableFuture<HttpResponse<?>> aroundFuture(HttpRequest<?> request, FilterContinuation<CompletableFuture<HttpResponse<?>>> continuation) {
            // the request the continuation proceeds with
            return continuation.request(request.mutate().header("X-Replaced", "replaced"))
                .proceed()
                .thenApply(response -> ((MutableHttpResponse<?>) response).header("X-Around-Future", "around"));
        }

        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        CompletionStage<HttpResponse<?>> aroundBlocking(FilterContinuation<CompletionStage<HttpResponse<?>>> continuation) {
            return continuation.proceed().thenApply(response -> ((MutableHttpResponse<?>) response).header("X-Around-Blocking", "around"));
        }

        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> authorize(HttpRequest<?> request) {
            if (request.getHeaders().contains("X-FOOBAR")) {
                // proceed, completing on another thread
                return CompletableFuture.supplyAsync(() -> null, executor);
            }
            return CompletableFuture.completedFuture(HttpResponse.unauthorized());
        }

        @ResponseFilter
        CompletionStage<MutableHttpResponse<?>> response(MutableHttpResponse<?> response) {
            return CompletableFuture.completedFuture(response.header("X-Response", "response"));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/completion-stage")
    static class CompletionStageController {
        @Get
        String index(@Header("X-Replaced") String replaced) {
            return "OK " + replaced;
        }

        @Get("/failing")
        String failing() {
            throw new IllegalStateException("downstream failure");
        }
    }
}
