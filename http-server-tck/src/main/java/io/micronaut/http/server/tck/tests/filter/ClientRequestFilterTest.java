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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ClientRequestFilterTest {
    public static final String SPEC_NAME = "ClientRequestFilterTest";

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
    @Disabled // updating the request is not supported by http client atm
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

    @ClientFilter
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyServerFilter {
        List<String> events = new ArrayList<>();

        @RequestFilter("/request-filter/immediate-request-parameter")
        public void requestFilterImmediateRequestParameter(HttpRequest<?> request) {
            events.add("requestFilterImmediateRequestParameter " + request.getPath());
        }

        @RequestFilter("/request-filter/immediate-mutable-request-parameter")
        public void requestFilterImmediateMutableRequestParameter(MutableHttpRequest<?> request) {
            request.header("foo", "bar");
        }

        @RequestFilter("/request-filter/continuation-blocking")
        @ExecuteOn(TaskExecutors.BLOCKING)
        public void requestFilterContinuationBlocking(MutableHttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
            request.header("foo", "bar");
            HttpResponse<?> r = continuation.proceed();
            events.add("requestFilterContinuationBlocking " + r.body());
        }

        @RequestFilter("/request-filter/continuation-reactive-publisher")
        public Publisher<HttpResponse<?>> requestFilterContinuationReactivePublisher(MutableHttpRequest<?> request, FilterContinuation<Publisher<HttpResponse<?>>> continuation) {
            request.header("foo", "bar");
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

        @RequestFilter("/request-filter/publisher-response")
        public Publisher<HttpResponse<?>> requestFilterPublisherResponse() {
            return Mono.fromCallable(() -> HttpResponse.ok("requestFilterPublisherResponse"));
        }

        @RequestFilter("/request-filter/mono-response")
        public Mono<HttpResponse<?>> requestFilterMonoResponse() {
            return Mono.fromCallable(() -> HttpResponse.ok("requestFilterMonoResponse"));
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
            return request.getHeaders().get("foo");
        }

        @Get("/request-filter/continuation-blocking")
        public String requestFilterContinuationBlocking(HttpRequest<?> request) {
            return request.getHeaders().get("foo");
        }

        @Get("/request-filter/continuation-reactive-publisher")
        public String requestFilterContinuationReactivePublisher(HttpRequest<?> request) {
            return request.getHeaders().get("foo");
        }

        @Get("/request-filter/continuation-update-request")
        public String requestFilterContinuationUpdateRequest(HttpRequest<?> request) {
            return request.getPath();
        }
    }
}
