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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.FilterSpec;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The executor of a filter of a route, of a group and of a server filter, chosen on the
 * {@link FilterSpec} a filter method returns: {@code executeOn} runs the filter on the executor,
 * like a filter method annotated {@code @ExecuteOn}, {@code nonBlocking} on the thread of the
 * filter chain, and the last call wins.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteFilterExecutorTest {
    public static final String SPEC_NAME = "HandlerRouteFilterExecutorTest";
    private static final String EXECUTOR = "route-filter-executor";
    private static final String EXECUTOR_THREAD = "route-filter-executor-thread";
    private static final String BEFORE = "X-Before-Thread";
    private static final String AFTER = "X-After-Thread";

    @Test
    void aRouteFilterRunsOnTheExecutorItIsGiven() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/route"), String.class);
            assertEquals(EXECUTOR_THREAD, response.body());
            assertEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void anAsynchronousRouteFilterIsCalledOnTheExecutorItIsGiven() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/async"), String.class);
            assertEquals(EXECUTOR_THREAD, response.body());
            assertEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void aNonBlockingFilterRunsOnTheThreadOfTheFilterChain() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/non-blocking"), String.class);
            assertNotEquals(EXECUTOR_THREAD, response.body());
            assertNotEquals("none", response.body());
            assertNotEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void theLastChoiceOfTheExecutorOfAFilterWins() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals(EXECUTOR_THREAD, server.exchange(HttpRequest.GET("/fe/last/executor"), String.class).body());
            String nonBlocking = server.exchange(HttpRequest.GET("/fe/last/non-blocking"), String.class).body();
            assertNotEquals(EXECUTOR_THREAD, nonBlocking);
            assertNotEquals("none", nonBlocking);
        }
    }

    @Test
    void theDeclarationContinuesOnTheRouteAfterAnd() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/and")
                .accept(MediaType.TEXT_PLAIN_TYPE), String.class);
            assertEquals(EXECUTOR_THREAD, response.body());
            assertEquals("true", response.getHeaders().get("X-And"));
            assertEquals(MediaType.TEXT_PLAIN, response.getContentType().map(MediaType::getName).orElse(null));
        }
    }

    @Test
    void aGroupFilterRunsOnTheExecutorItIsGiven() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/group/route"), String.class);
            assertEquals(EXECUTOR_THREAD, response.body());
            assertEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void aGroupResponseFilterFiltersTheErrorRouteOnTheExecutorItIsGiven() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/group/fails"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("error route", response.body());
            assertEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void aGroupResponseFilterFiltersTheStatusRouteOnTheExecutorItIsGiven() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/group/missing"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("status route", response.body());
            assertEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void aServerFilterRunsOnTheExecutorItIsGiven() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/fe/server/route"), String.class);
            assertEquals(EXECUTOR_THREAD, response.body());
            assertEquals(EXECUTOR_THREAD, response.getHeaders().get(AFTER));
        }
    }

    @Test
    void theExecutorOfAFilterIsFixedWhenTheRouterIsBuilt() throws IOException {
        try (ServerUnderTest server = server()) {
            // changed after the router took the route: ignored, like a setting of the route
            server.getApplicationContext().getBean(FilterExecutorRoutes.class).late.executeOn(EXECUTOR);
            String thread = server.exchange(HttpRequest.GET("/fe/late"), String.class).body();
            assertNotEquals(EXECUTOR_THREAD, thread);
            assertNotEquals("none", thread);
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static void before(MutableHttpRequest<?> request) {
        request.setAttribute(BEFORE, Thread.currentThread().getName());
    }

    private static void after(MutableHttpResponse<?> response) {
        response.header(AFTER, Thread.currentThread().getName());
    }

    private static HttpResponse<?> thread(HttpRequest<?> request) {
        return HttpResponse.ok(request.getAttribute(BEFORE, String.class).orElse("none")).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FilterExecutorRoutes implements HttpRoutes {
        FilterSpec<HttpRouteSpec> late;

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/fe/route", (request, pathVariables) -> thread(request))
                .before(HandlerRouteFilterExecutorTest::before).executeOn(EXECUTOR)
                .and()
                .after((request, response) -> after(response)).executeOn(EXECUTOR);
            routes.GET("/fe/async", (request, pathVariables) -> thread(request))
                .beforeAsync(request -> {
                    before(request);
                    return CompletableFuture.completedFuture(null);
                }).executeOn(EXECUTOR)
                .and()
                .afterAsync((request, response) -> {
                    after(response);
                    return CompletableFuture.completedFuture(null);
                }).executeOn(EXECUTOR);
            routes.GET("/fe/non-blocking", (request, pathVariables) -> thread(request))
                .before(HandlerRouteFilterExecutorTest::before).nonBlocking()
                .and()
                .after((request, response) -> after(response)).nonBlocking();
            routes.GET("/fe/last/executor", (request, pathVariables) -> thread(request))
                .before(HandlerRouteFilterExecutorTest::before).nonBlocking().executeOn(EXECUTOR);
            routes.GET("/fe/last/non-blocking", (request, pathVariables) -> thread(request))
                .before(HandlerRouteFilterExecutorTest::before).executeOn(EXECUTOR).nonBlocking();
            routes.GET("/fe/and", (request, pathVariables) -> thread(request))
                .before(HandlerRouteFilterExecutorTest::before).executeOn(EXECUTOR)
                .and()
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .after((request, response) -> response.header("X-And", "true"));
            late = routes.GET("/fe/late", (request, pathVariables) -> thread(request))
                .before(HandlerRouteFilterExecutorTest::before);

            routes.path("/fe/group", group -> {
                group.before(HandlerRouteFilterExecutorTest::before).executeOn(EXECUTOR);
                group.after((request, response) -> after(response)).executeOn(EXECUTOR);
                group.GET("/route", (request, pathVariables) -> thread(request));
                group.GET("/fails", (request, pathVariables) -> {
                    throw new FilterExecutorFailure();
                });
                group.GET("/missing", (request, pathVariables) -> HttpResponse.notFound());
                group.error(FilterExecutorFailure.class, (request, error) ->
                    HttpResponse.ok("error route").contentType(MediaType.TEXT_PLAIN_TYPE));
                group.status(HttpStatus.NOT_FOUND, request ->
                    HttpResponse.ok("status route").contentType(MediaType.TEXT_PLAIN_TYPE));
            });

            routes.filter("/fe/server/**")
                .before(HandlerRouteFilterExecutorTest::before).executeOn(EXECUTOR)
                .and()
                .after((request, response) -> after(response)).executeOn(EXECUTOR)
                .and()
                .order(10);
            routes.GET("/fe/server/route", (request, pathVariables) -> thread(request));
        }
    }

    static final class FilterExecutorFailure extends RuntimeException {
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors {
        @Singleton
        @Named(EXECUTOR)
        @Bean(preDestroy = "shutdown")
        ExecutorService routeFilterExecutor() {
            return java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, EXECUTOR_THREAD));
        }
    }
}
