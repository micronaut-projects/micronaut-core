/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface FilterErrorTest {
    @Test
    default void testFilterThrowingExceptionHandledByExceptionHandlerThrowingException() throws IOException {
        TestScenario.builder()
            .specName("FilterErrorSpec3")
            .request(HttpRequest.GET("/filter-error-spec-3")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("from exception handler")
                        .build());
                ExceptionException filter = server.getApplicationContext().getBean(ExceptionException.class);
                assertEquals(1, filter.executedCount.get());
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, filter.responseStatus.getAndSet(null));
            })
            .run();
    }

    @Test
    default void testTheErrorRouteIsTheRouteMatch() throws IOException {
        TestScenario.builder()
            .request(HttpRequest.GET("/filter-error-spec-4/status").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .specName("FilterErrorSpec4")
            .assertion((server, request) -> {
                    AssertionUtils.assertDoesNotThrow(server, request,
                        HttpResponseAssertion.builder()
                            .status(HttpStatus.OK)
                            .build());
                    ExceptionRoute filter = server.getApplicationContext().getBean(ExceptionRoute.class);
                    RouteMatch match = filter.routeMatch.getAndSet(null);
                    assertTrue(match instanceof MethodBasedRouteMatch);
                    assertEquals("testStatus", ((MethodBasedRouteMatch) match).getName());
                })
            .run();
    }

    @Test
    default void testNonOncePerRequestFilterThrowingErrorDoesNotLoop() throws IOException {
        TestScenario.builder()
            .request(HttpRequest.GET("/filter-error-spec")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .specName("FilterErrorSpec2")
                .assertion((server, request) -> {
                    AssertionUtils.assertThrows(server, request,
                        HttpResponseAssertion.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .body("from filter exception handler")
                            .build());
                    FirstEvery filter = server.getApplicationContext().getBean(FirstEvery.class);
                    assertEquals(1, filter.executedCount.get());
                }).run();
    }

    @Test
    default void testErrorsEmittedFromSecondFilterInteractingWithExceptionHandlers() throws IOException {
        TestScenario.builder()
            .request(HttpRequest.GET("/filter-error-spec").
            header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .header("X-Passthru", StringUtils.TRUE))
            .specName("FilterErrorSpec")
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .body("from NEXT filter exception handle").build());

                First first = server.getApplicationContext().getBean(First.class);
                Next next = server.getApplicationContext().getBean(Next.class);

                assertEquals(1, first.executedCount.get());
                assertEquals(HttpStatus.BAD_REQUEST, first.responseStatus.getAndSet(null));
                assertEquals(1, next.executedCount.get());
            }).run();
    }

    @Test
    default void testErrorsEmittedFromFiltersInteractingWithExceptionHandlers() throws IOException {
        TestScenario.builder()
            .specName("FilterErrorSpec")
            .request(HttpRequest.GET("/filter-error-spec").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .body("from filter exception handler").build());

                First first = server.getApplicationContext().getBean(First.class);
                Next next = server.getApplicationContext().getBean(Next.class);

                assertEquals(1, first.executedCount.get());
                assertNull(first.responseStatus.getAndSet(null));
                assertEquals(0, next.executedCount.get());
            })
            .run();
    }

    class FilterExceptionException extends RuntimeException {
    }

    class FilterException extends RuntimeException {
    }

    class NextFilterException extends RuntimeException {
    }

    @Requires(property = "spec.name", value = "FilterErrorSpec")
    @Filter(Filter.MATCH_ALL_PATTERN)
    class First implements HttpServerFilter {
        AtomicInteger executedCount = new AtomicInteger(0);
        AtomicReference<HttpStatus> responseStatus = new AtomicReference<>();

        private void setResponse(MutableHttpResponse<?> r) {
            responseStatus.set(r.status());
        }

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet();
            if (StringUtils.isTrue(request.getHeaders().get("X-Passthru"))) {
                return Publishers.then(chain.proceed(request), this::setResponse);
            }
            return Publishers.just(new FilterException());
        }

        @Override
        public int getOrder() {
            return 10;
        }
    }

    @Requires(property = "spec.name", value = "FilterErrorSpec")
    @Filter(Filter.MATCH_ALL_PATTERN)
    static class Next implements HttpServerFilter {
        AtomicInteger executedCount = new AtomicInteger(0);

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet();
            return Publishers.just(new NextFilterException());
        }

        @Override
        public int getOrder() {
            return 20;
        }
    }

    @Requires(property = "spec.name", value = "FilterErrorSpec2")
    @Filter(Filter.MATCH_ALL_PATTERN)
    static class FirstEvery implements HttpServerFilter {
        AtomicInteger executedCount = new AtomicInteger(0);

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet();
            return Publishers.just(new FilterException());
        }

        @Override
        public int getOrder() {
            return 10;
        }
    }

    @Requires(property = "spec.name", value = "FilterErrorSpec3")
    @Filter(Filter.MATCH_ALL_PATTERN)
    class ExceptionException implements HttpServerFilter {
        AtomicInteger executedCount = new AtomicInteger(0);
        AtomicReference<HttpStatus> responseStatus = new AtomicReference<>();

        private void setResponse(MutableHttpResponse<?> r) {
            responseStatus.set(r.status());
        }

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet();
            return Publishers.then(chain.proceed(request),
                this::setResponse);
        }

        @Override
        public int getOrder() {
            return 10;
        }
    }

    @Requires(property = "spec.name", value = "FilterErrorSpec4")
    @Filter(Filter.MATCH_ALL_PATTERN)
    class ExceptionRoute implements HttpServerFilter {
        AtomicReference<RouteMatch<?>> routeMatch = new AtomicReference<>();

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.then(chain.proceed(request),
                httpResponse -> routeMatch.set(httpResponse.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).get()));
        }

        @Override
        public int getOrder() {
            return 10;
        }
    }

    @Requires(condition = FilterCondition.class)
    @Controller("/filter-error-spec")
    class NeverReachedController {
        @Get
        String get() {
            return "OK";
        }
    }

    @Requires(condition = FilterCondition.class)
    @Controller("/filter-error-spec-3")
    class HandledByHandlerController {
        @Get
        String get() {
            throw new FilterExceptionException();
        }
    }

    @Requires(condition = FilterCondition.class)
    @Controller("/filter-error-spec-4")
    class HandledByErrorRouteController {
        @Get("/exception")
        String getException() {
            throw new FilterExceptionException();
        }

        @Get("/status")
        HttpStatus getStatus() {
            return HttpStatus.NOT_FOUND;
        }

        @Error(exception = FilterExceptionException.class)
        @Status(HttpStatus.OK)
        void testException() {

        }

        @Error(status = HttpStatus.NOT_FOUND)
        @Status(HttpStatus.OK)
        void testStatus() {

        }
    }

    class FilterCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context) {
            return context.getProperty("spec.name", String.class)
                .map(val -> val.equals("FilterErrorSpec4") || val.equals("FilterErrorSpec3") || val.equals("FilterErrorSpec2") || val.equals("FilterErrorSpec"))
                .orElse(false);
        }
    }

    @Requires(condition = FilterCondition.class)
    @Singleton
    class FilterExceptionExceptionHandler implements ExceptionHandler<FilterExceptionException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, FilterExceptionException exception) {
            throw new RuntimeException("from exception handler");
        }
    }

    @Requires(condition = FilterCondition.class)
    @Singleton
    class FilterExceptionHandler implements ExceptionHandler<FilterException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, FilterException exception) {
            return HttpResponse.badRequest("from filter exception handler");
        }
    }

    @Requires(condition = FilterCondition.class)
    @Singleton
    class NextFilterExceptionHandler implements ExceptionHandler<NextFilterException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, NextFilterException exception) {
            return HttpResponse.badRequest("from NEXT filter exception handler");
        }
    }
}
