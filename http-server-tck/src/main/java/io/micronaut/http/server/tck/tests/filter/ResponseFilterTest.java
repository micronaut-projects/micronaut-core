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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.web.router.RouteAttributes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ResponseFilterTest {
    public static final String SPEC_NAME = "ResponseFilterTest";

    @Test
    public void responseFilterImmediateRequestParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/immediate-request-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("responseFilterImmediateRequestParameter /response-filter/immediate-request-parameter"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterImmediateMutableRequestParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/immediate-mutable-request-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("responseFilterImmediateMutableRequestParameter /response-filter/immediate-mutable-request-parameter"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterResponseParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/response-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("responseFilterResponseParameter foo"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterMutableResponseParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/mutable-response-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterMutableResponseParameter foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterThrowableParameterNotCalledForControllerError() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/throwable-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
                // filter not called, the error is mapped to a response before filters are invoked
                Assertions.assertEquals(
                    List.of(),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterReplaceResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterReplaceResponse foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplaceMutableResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-mutable-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterReplaceMutableResponse foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplaceResponseNull() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-response-null"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplaceResponseEmpty() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-response-empty"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplacePublisherResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-publisher-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterReplacePublisherResponse foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplaceMonoResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-mono-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterReplaceMonoResponse foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplaceCompletableResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-completable-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterReplaceCompletableResponse foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterReplaceCompletionResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/replace-completion-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterReplaceCompletionResponse foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterHeadBody() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.HEAD("/response-filter/head-body"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header("X-HEAD-BODY", "foo")
                    .build());
            })
            .run();
    }

    @ServerFilter
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyServerFilter {
        List<String> events = new ArrayList<>();

        @ResponseFilter("/response-filter/immediate-request-parameter")
        public void responseFilterImmediateRequestParameter(HttpRequest<?> request) {
            events.add("responseFilterImmediateRequestParameter " + request.getPath());
        }

        @ResponseFilter("/response-filter/immediate-mutable-request-parameter")
        public void responseFilterImmediateMutableRequestParameter(MutableHttpRequest<?> request) {
            events.add("responseFilterImmediateMutableRequestParameter " + request.getPath());
        }

        @ResponseFilter("/response-filter/response-parameter")
        public void responseFilterResponseParameter(HttpResponse<?> response) {
            events.add("responseFilterResponseParameter " + response.body());
        }

        @ResponseFilter("/response-filter/mutable-response-parameter")
        public void responseFilterMutableResponseParameter(MutableHttpResponse<?> response) {
            response.body("responseFilterMutableResponseParameter " + response.body());
        }

        @ResponseFilter("/response-filter/throwable-parameter")
        public void responseFilterThrowableParameter(Throwable t) {
            events.add("responseFilterThrowableParameter " + t.getMessage());
        }

        @ResponseFilter("/response-filter/replace-response")
        public HttpResponse<?> responseFilterReplaceResponse(HttpResponse<?> response) {
            return HttpResponse.ok("responseFilterReplaceResponse " + response.body());
        }

        @ResponseFilter("/response-filter/replace-mutable-response")
        public MutableHttpResponse<?> responseFilterReplaceMutableResponse(HttpResponse<?> response) {
            return HttpResponse.ok("responseFilterReplaceMutableResponse " + response.body());
        }

        @ResponseFilter("/response-filter/replace-response-null")
        @Nullable
        public HttpResponse<?> responseFilterReplaceResponseNull() {
            return null;
        }

        @ResponseFilter("/response-filter/replace-response-empty")
        public Optional<HttpResponse<?>> responseFilterReplaceResponseEmpty() {
            return Optional.empty();
        }

        @ResponseFilter("/response-filter/replace-publisher-response")
        public Publisher<MutableHttpResponse<?>> responseFilterReplacePublisherResponse(HttpResponse<?> response) {
            return Flux.just(HttpResponse.ok("responseFilterReplacePublisherResponse " + response.body()));
        }

        @ResponseFilter("/response-filter/replace-mono-response")
        public Mono<MutableHttpResponse<?>> responseFilterReplaceMonoResponse(HttpResponse<?> response) {
            return Mono.just(HttpResponse.ok("responseFilterReplaceMonoResponse " + response.body()));
        }

        @ResponseFilter("/response-filter/replace-completable-response")
        public CompletableFuture<MutableHttpResponse<?>> responseFilterReplaceCompletableResponse(HttpResponse<?> response) {
            return CompletableFuture.completedFuture(HttpResponse.ok("responseFilterReplaceCompletableResponse " + response.body()));
        }

        @ResponseFilter("/response-filter/replace-completion-response")
        public CompletionStage<MutableHttpResponse<?>> responseFilterReplaceCompletionResponse(HttpResponse<?> response) {
            return CompletableFuture.completedStage(HttpResponse.ok("responseFilterReplaceCompletionResponse " + response.body()));
        }

        @ResponseFilter("/response-filter/head-body")
        public void responseFilterHeadBody(MutableHttpResponse<?> response) {
            response.header("X-HEAD-BODY", RouteAttributes.getHeadBody(response).map(o -> (String) o).orElse(""));
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {
        @Get("/response-filter/immediate-request-parameter")
        public String responseFilterImmediateRequestParameter() {
            return "foo";
        }

        @Get("/response-filter/immediate-mutable-request-parameter")
        public String responseFilterImmediateMutableRequestParameter() {
            return "foo";
        }

        @Get("/response-filter/response-parameter")
        public String responseFilterResponseParameter() {
            return "foo";
        }

        @Get("/response-filter/mutable-response-parameter")
        public String responseFilterMutableResponseParameter() {
            return "foo";
        }

        @Get("/response-filter/throwable-parameter")
        public String responseFilterThrowableParameter() {
            throw new RuntimeException("foo");
        }

        @Get("/response-filter/replace-response")
        public String responseFilterReplaceResponse() {
            return "foo";
        }

        @Get("/response-filter/replace-mutable-response")
        public String responseFilterReplaceMutableResponse() {
            return "foo";
        }

        @Get("/response-filter/replace-response-null")
        public String responseFilterReplaceResponseNull() {
            return "foo";
        }

        @Get("/response-filter/replace-response-empty")
        public String responseFilterReplaceResponseEmpty() {
            return "foo";
        }

        @Get("/response-filter/replace-publisher-response")
        public String responseFilterReplacePublisherResponse() {
            return "foo";
        }

        @Get("/response-filter/replace-mono-response")
        public String responseFilterReplaceMonoResponse() {
            return "foo";
        }

        @Get("/response-filter/replace-completable-response")
        public String responseFilterReplaceCompletableResponse() {
            return "foo";
        }

        @Get("/response-filter/replace-completion-response")
        public String responseFilterReplaceCompletionResponse() {
            return "foo";
        }

        @Get("/response-filter/head-body")
        public String responseFilterHeadBody() {
            return "foo";
        }
    }
}
