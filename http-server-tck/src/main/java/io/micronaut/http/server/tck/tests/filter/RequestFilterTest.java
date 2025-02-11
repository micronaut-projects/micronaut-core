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
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
public class RequestFilterTest {
    public static final String SPEC_NAME = "RequestFilterTest";

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

    @Test
    public void requestFilterImmediateRequestParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/immediate-request-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("requestFilterImmediateRequestParameter /request-filter/immediate-request-parameter"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void requestFilterImmediateMutableRequestParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/immediate-mutable-request-parameter"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("bar")
                .build()))
            .run();
    }

    @Test
    public void requestFilterReplaceRequest() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceMutableRequest() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-mutable-request"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-mutable-request-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceRequestNull() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request-null"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-null")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceRequestEmpty() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request-empty"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-empty")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceRequestPublisher() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request-publisher"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-publisher-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceRequestMono() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request-mono"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-mono-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceRequestCompletable() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request-completable"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-completable-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterReplaceRequestCompletion() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/replace-request-completion"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/replace-request-completion-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterContinuationBlocking() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/continuation-blocking"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("bar")
                    .build());
                Assertions.assertEquals(
                    List.of("requestFilterContinuationBlocking bar"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void requestFilterContinuationReactivePublisher() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/continuation-reactive-publisher"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("bar")
                    .build());
                Assertions.assertEquals(
                    List.of("requestFilterContinuationReactivePublisher bar"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void requestFilterContinuationUpdateRequest() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/continuation-update-request"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("/request-filter/continuation-update-request-2")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterImmediateResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/immediate-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("requestFilterImmediateResponse")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterNullResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/null-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("requestFilterNullResponse"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void requestFilterEmptyOptionalResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/empty-optional-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("requestFilterEmptyOptionalResponse"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void requestFilterPublisherResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/publisher-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("requestFilterPublisherResponse")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterMonoResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/mono-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("requestFilterMonoResponse")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterCompletableResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/completable-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("requestFilterCompletableResponse")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterCompletionResponse() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/completion-response"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("requestFilterCompletionResponse")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterRouteMatch() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/route-match"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("java.lang.String")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterRouteInfo() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/route-info"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("java.lang.String")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterRouteMatchNullable() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/route-match-nullable"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("route-match-nullable")
                    .build());
            })
            .run();
    }

    @Test
    public void requestFilterRouteMatchNonNull() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/request-filter/route-match-nonnull"))
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build());
            })
            .run();
    }

    @ServerFilter
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyServerFilter {
        List<String> events = new ArrayList<>();

        @RequestFilter("/request-filter/immediate-request-parameter")
        public void requestFilterImmediateRequestParameter(HttpRequest<?> request) {
            events.add("requestFilterImmediateRequestParameter " + request.getPath());
        }

        @RequestFilter("/request-filter/binding")
        @ExecuteOn(TaskExecutors.BLOCKING)
        public void requestFilterBinding(
            @Header String contentType,
            @Body byte[] bytes,
            FilterContinuation<HttpResponse<?>> continuation) {
            events.add("binding " + contentType + " " + new String(bytes, StandardCharsets.UTF_8));
            continuation.proceed();
        }

        @RequestFilter("/request-filter/immediate-mutable-request-parameter")
        public void requestFilterImmediateMutableRequestParameter(MutableHttpRequest<?> request) {
            request.setAttribute("foo", "bar");
        }

        @RequestFilter("/request-filter/replace-request")
        public HttpRequest<Object> requestFilterReplaceRequest() {
            return HttpRequest.GET("/request-filter/replace-request-2");
        }

        @RequestFilter("/request-filter/replace-mutable-request")
        public MutableHttpRequest<Object> requestFilterReplaceMutableRequest() {
            return HttpRequest.GET("/request-filter/replace-mutable-request-2");
        }

        @RequestFilter("/request-filter/replace-request-null")
        @Nullable
        public HttpRequest<Object> requestFilterReplaceRequestNull() {
            return null;
        }

        @RequestFilter("/request-filter/replace-request-empty")
        public Optional<HttpRequest<Object>> requestFilterReplaceRequestEmpty() {
            return Optional.empty();
        }

        @RequestFilter("/request-filter/replace-request-publisher")
        public Publisher<HttpRequest<Object>> requestFilterReplaceRequestPublisher() {
            return Flux.just(HttpRequest.GET("/request-filter/replace-request-publisher-2"));
        }

        @RequestFilter("/request-filter/replace-request-mono")
        public Mono<HttpRequest<Object>> requestFilterReplaceRequestMono() {
            return Mono.just(HttpRequest.GET("/request-filter/replace-request-mono-2"));
        }

        @RequestFilter("/request-filter/replace-request-completable")
        public CompletableFuture<HttpRequest<Object>> requestFilterReplaceRequestCompletable() {
            return CompletableFuture.completedFuture(HttpRequest.GET("/request-filter/replace-request-completable-2"));
        }

        @RequestFilter("/request-filter/replace-request-completion")
        public CompletionStage<HttpRequest<Object>> requestFilterReplaceRequestCompletion() {
            return CompletableFuture.completedStage(HttpRequest.GET("/request-filter/replace-request-completion-2"));
        }

        @RequestFilter("/request-filter/continuation-blocking")
        @ExecuteOn(TaskExecutors.BLOCKING)
        public void requestFilterContinuationBlocking(HttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
            request.setAttribute("foo", "bar");
            HttpResponse<?> r = continuation.proceed();
            events.add("requestFilterContinuationBlocking " + r.body());
        }

        @RequestFilter("/request-filter/continuation-reactive-publisher")
        public Publisher<HttpResponse<?>> requestFilterContinuationReactivePublisher(HttpRequest<?> request, FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
            request.setAttribute("foo", "bar");
            return Mono.from(continuation.proceed()).doOnNext(r -> events.add("requestFilterContinuationReactivePublisher " + r.body()));
        }

        @RequestFilter("/request-filter/continuation-update-request")
        @ExecuteOn(TaskExecutors.BLOCKING)
        public void requestFilterContinuationUpdateRequest(FilterContinuation<HttpResponse<?>> continuation) {
            // won't affect the routing decision, but will appear in the controller
            continuation.request(HttpRequest.GET("/request-filter/continuation-update-request-2"));
            continuation.proceed();
        }

        @RequestFilter("/request-filter/immediate-response")
        public HttpResponse<?> requestFilterImmediateResponse() {
            return HttpResponse.ok("requestFilterImmediateResponse");
        }

        @RequestFilter("/request-filter/null-response")
        @Nullable
        public HttpResponse<?> requestFilterNullResponse() {
            events.add("requestFilterNullResponse");
            return null;
        }

        @RequestFilter("/request-filter/empty-optional-response")
        public Optional<HttpResponse<?>> requestFilterEmptyOptionalResponse() {
            events.add("requestFilterEmptyOptionalResponse");
            return Optional.empty();
        }

        @RequestFilter("/request-filter/publisher-response")
        public Publisher<HttpResponse<?>> requestFilterPublisherResponse() {
            return Mono.fromCallable(() -> HttpResponse.ok("requestFilterPublisherResponse"));
        }

        @RequestFilter("/request-filter/mono-response")
        public Mono<HttpResponse<?>> requestFilterMonoResponse() {
            return Mono.fromCallable(() -> HttpResponse.ok("requestFilterMonoResponse"));
        }

        @RequestFilter("/request-filter/completable-response")
        public CompletableFuture<MutableHttpResponse<String>> requestFilterCompletableResponse() {
            return CompletableFuture.completedFuture(HttpResponse.ok("requestFilterCompletableResponse"));
        }

        @RequestFilter("/request-filter/completion-response")
        public CompletionStage<MutableHttpResponse<String>> requestFilterCompletionResponse() {
            return CompletableFuture.completedStage(HttpResponse.ok("requestFilterCompletionResponse"));
        }

        @RequestFilter("/request-filter/route-match")
        public void requestFilterRouteMatch(MutableHttpRequest<?> request, RouteMatch<?> routeMatch) {
            request.setAttribute("route-match-response", routeMatch.getRouteInfo().getReturnType().getType().getName());
        }

        @RequestFilter("/request-filter/route-info")
        public void requestFilterRouteMatch(MutableHttpRequest<?> request, RouteInfo<?> routeInfo) {
            request.setAttribute("route-info-response", routeInfo.getReturnType().getType().getName());
        }

        @RequestFilter("/request-filter/route-match-nullable")
        public HttpResponse<?> requestFilterRouteMatchNullable(MutableHttpRequest<?> request, @Nullable RouteMatch<?> routeMatch) {
            return HttpResponse.ok("route-match-nullable");
        }

        @RequestFilter("/request-filter/route-match-nonnull")
        public HttpResponse<?> requestFilterRouteMatchNonNull(MutableHttpRequest<?> request, RouteMatch<?> routeMatch) {
            return HttpResponse.ok("route-match-nonnull");
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {
        @Get("/request-filter/immediate-request-parameter")
        public String requestFilterImmediateRequestParameter() {
            return "foo";
        }

        @Get("/request-filter/immediate-mutable-request-parameter")
        public String requestFilterImmediateMutableRequestParameter(HttpRequest<?> request) {
            return request.getAttribute("foo").get().toString();
        }

        @Get("/request-filter/replace-request")
        public String requestFilterReplaceRequest(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-mutable-request")
        public String requestFilterReplaceMutableRequest(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-request-null")
        public String requestFilterReplaceRequestNull(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-request-empty")
        public String requestFilterReplaceRequestEmpty(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-request-publisher")
        public String requestFilterReplaceRequestPublisher(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-request-mono")
        public String requestFilterReplaceRequestMono(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-request-completable")
        public String requestFilterReplaceRequestCompletable(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/replace-request-completion")
        public String requestFilterReplaceRequestCompletion(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/continuation-blocking")
        public String requestFilterContinuationBlocking(HttpRequest<?> request) {
            return request.getAttribute("foo").get().toString();
        }

        @Get("/request-filter/continuation-reactive-publisher")
        public String requestFilterContinuationReactivePublisher(HttpRequest<?> request) {
            return request.getAttribute("foo").get().toString();
        }

        @Get("/request-filter/continuation-update-request")
        public String requestFilterContinuationUpdateRequest(HttpRequest<?> request) {
            return request.getPath();
        }

        @Get("/request-filter/null-response")
        public String requestFilterNullResponse(HttpRequest<?> request) {
            return "foo";
        }

        @Get("/request-filter/empty-optional-response")
        public String requestFilterEmptyOptionalResponse(HttpRequest<?> request) {
            return "foo";
        }

        @Post("/request-filter/binding")
        public String requestFilterBinding(@Header String contentType, @Body byte[] bytes) {
            return contentType + " " + new String(bytes, StandardCharsets.UTF_8);
        }

        @Get("/request-filter/route-match")
        public String requestFilterRouteMatch(HttpRequest<?> request) {
            return request.getAttribute("route-match-response", String.class).orElse("none");
        }

        @Get("/request-filter/route-info")
        public String requestFilterRouteInfo(HttpRequest<?> request) {
            return request.getAttribute("route-info-response", String.class).orElse("none");
        }
    }
}
