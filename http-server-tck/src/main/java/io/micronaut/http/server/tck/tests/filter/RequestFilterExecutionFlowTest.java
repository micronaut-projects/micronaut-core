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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
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
import java.util.concurrent.Executor;

/**
 * Filter methods that return {@link ExecutionFlow} or proceed with an {@link ExecutionFlow} continuation.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestFilterExecutionFlowTest {
    public static final String SPEC_NAME = "RequestFilterExecutionFlowTest";

    @Test
    public void executionFlowFilters() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/execution-flow").header("X-FOOBAR", "123"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("OK")
                .headers(Map.of(
                    "X-Around", "around",
                    "X-Around-Blocking", "around",
                    "X-Response", "response"
                ))
                .build()))
            .run();
    }

    @Test
    public void executionFlowFilterReturnsResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/execution-flow"))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .build()))
            .run();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter("/execution-flow/**")
    static class ExecutionFlowFilter {

        private final Executor executor;

        ExecutionFlowFilter(@Named(TaskExecutors.BLOCKING) Executor executor) {
            this.executor = executor;
        }

        @RequestFilter
        ExecutionFlow<HttpResponse<?>> around(FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation) {
            return continuation.proceed().map(response -> ((MutableHttpResponse<?>) response).header("X-Around", "around"));
        }

        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        ExecutionFlow<HttpResponse<?>> aroundBlocking(FilterContinuation<ExecutionFlow<HttpResponse<?>>> continuation) {
            return continuation.proceed().map(response -> ((MutableHttpResponse<?>) response).header("X-Around-Blocking", "around"));
        }

        @RequestFilter
        ExecutionFlow<@Nullable HttpResponse<?>> authorize(HttpRequest<?> request) {
            if (request.getHeaders().contains("X-FOOBAR")) {
                // proceed, completing on another thread
                return ExecutionFlow.async(executor, () -> ExecutionFlow.just(null));
            }
            return ExecutionFlow.just(HttpResponse.unauthorized());
        }

        @ResponseFilter
        ExecutionFlow<MutableHttpResponse<?>> response(MutableHttpResponse<?> response) {
            return ExecutionFlow.just(response.header("X-Response", "response"));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/execution-flow")
    static class ExecutionFlowController {
        @Get
        String index() {
            return "OK";
        }
    }
}
