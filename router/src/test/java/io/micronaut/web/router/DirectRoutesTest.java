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
package io.micronaut.web.router;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.DirectRouteSpec;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import io.micronaut.web.router.direct.DirectRequest;
import io.micronaut.web.router.direct.DirectRouteBuilder;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.DirectRouteSupport;
import io.micronaut.web.router.direct.HttpDirectRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lookup of direct routes, {@link DirectRouteLookup}, as a server runtime sees it: the URI
 * templates, the specificity and the order of the router, the conditions a direct route may
 * have, and the routes the application fails to start with.
 */
class DirectRoutesTest {

    static final String SPEC_NAME = "DirectRoutesTest";
    /**
     * The response factory of the server of the test: it counts the responses it creates.
     */
    static final CountingFactory FACTORY = new CountingFactory();

    private static ApplicationContext context;
    private static DirectRouteLookup directRoutes;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.context-path", "/ctx"));
        directRoutes = context.getBean(DirectRouteLookup.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void aResponseGivenAsAValueIsComposedForEachRequestAndOnlyItsBodyIsShared() {
        int created = FACTORY.created;
        HttpResponse<?> first = find("GET", "/ctx/health");
        HttpResponse<?> second = find("GET", "/ctx/health");
        assertNotSame(first, second);
        // copied with the factory of the server
        assertEquals(created + 2, FACTORY.created);
        // the text is encoded once, when the route is declared
        assertSame(first.body(), second.body());
        assertEquals(200, first.code());
        assertEquals("UP", text(first));
        // exactly the headers of the response: the route adds none
        assertTrue(first.getHeaders().isEmpty(), first.getHeaders().asMap().toString());
        first.toMutableResponse().header("X-Changed", "yes");
        assertNull(find("GET", "/ctx/health").getHeaders().get("X-Changed"));
    }

    @Test
    void aByteArrayIsCopiedWhenTheRouteIsDeclared() {
        assertArrayEquals("bytes".getBytes(StandardCharsets.UTF_8), (byte[]) find("GET", "/ctx/bytes").body());
        assertEquals("application/octet-stream", find("GET", "/ctx/bytes").getHeaders().get("Content-Type"));
    }

    @Test
    void theRoutesAreUnderTheContextPathAndTheirPrefix() {
        assertNull(find("GET", "/health"));
        assertEquals("pong", text(find("GET", "/ctx/api/ping")));
    }

    @Test
    void theMostSpecificTemplateWinsThenTheOrder() {
        assertEquals("special", text(find("GET", "/ctx/files/special")));
        assertEquals("file other", text(find("GET", "/ctx/files/other")));
        // equally specific: the lowest order wins
        assertEquals("first", text(find("GET", "/ctx/ordered")));
        // equally specific and of the same order: ambiguous, like the router
        assertEquals(400, find("GET", "/ctx/ambiguous").code());
    }

    @Test
    void aHeadRequestIsAnsweredByTheGetRouteUnlessAHeadRouteMatches() {
        assertEquals("UP", text(find("HEAD", "/ctx/health")));
        assertEquals("explicit head", text(find("HEAD", "/ctx/explicit-head")));
        assertNull(find("POST", "/ctx/health"));
    }

    @Test
    void theConditionsReadTheMethodTheHeadersTheCookiesTheHostAndThePeer() {
        assertNull(find("GET", "/ctx/beta"));
        assertEquals("beta", text(find(request("GET", "/ctx/beta").header("X-Channel", "BETA"))));
        assertEquals("beta", text(find(request("GET", "/ctx/beta").header("Cookie", "a=1; channel=beta"))));
        assertEquals("internal", text(find(request("GET", "/ctx/internal").header("Host", "admin.local:8080").peer("10.1.2.3"))));
        assertNull(find(request("GET", "/ctx/internal").header("Host", "admin.local").peer("192.168.0.1")));
        assertNull(find(request("GET", "/ctx/internal").header("Host", "public.example").peer("10.1.2.3")));
        assertEquals("blocked", text(find(request("GET", "/ctx/anything/here").peer("203.0.113.9"))));
        assertNull(find(request("GET", "/ctx/anything/here").peer("198.51.100.1")));
    }

    @Test
    void aRouteOfSeveralMethodsIsConfiguredTogether() {
        assertEquals(403, find(request("GET", "/ctx/blocked/a/b").header("X-Block", "yes")).code());
        assertEquals(403, find(request("POST", "/ctx/blocked/a").header("X-Block", "yes")).code());
        assertEquals(403, find(request("HEAD", "/ctx/blocked/a").header("X-Block", "yes")).code());
        assertNull(find(request("PUT", "/ctx/blocked/a").header("X-Block", "yes")));
        assertNull(find(request("POST", "/ctx/blocked/a")));
    }

    @Test
    void theConstraintsAndTheFunctionsReadThePathVariables() {
        assertEquals("shop north", text(find("GET", "/ctx/shops/north")));
        assertNull(find("GET", "/ctx/shops/west"));
        assertEquals("item 7", text(find("GET", "/ctx/items/7")));
        // the variable does not convert
        assertEquals(500, find("GET", "/ctx/items/seven").code());
    }

    @Test
    void aSupplierCreatesAResponseForEachRequest() {
        assertEquals("1", text(find("GET", "/ctx/counter")));
        assertEquals("2", text(find("GET", "/ctx/counter")));
        // the function composes the response with the factory of the server
        int created = FACTORY.created;
        find("GET", "/ctx/counter");
        assertEquals(created + 1, FACTORY.created);
        // declined: as if no direct route matched
        assertNull(find("GET", "/ctx/null"));
        assertEquals(500, find("GET", "/ctx/failing").code());
    }

    @Test
    void aResponseOfAnotherFactoryIsReturnedAsItIs() {
        HttpResponse<?> foreign = find("GET", "/ctx/foreign");
        assertEquals(202, foreign.code());
        assertEquals("foreign", foreign.body());
    }

    @Test
    void theDirectRoutesAreNotRoutesOfTheRouter() {
        Router router = context.getBean(Router.class);
        assertNull(router.findClosest(HttpRequest.GET("/ctx/health")));
        assertNotNull(router.findClosest(HttpRequest.GET("/ctx/ordinary")));
        // nor are they answered as ordinary routes when no direct route matches
        assertNull(find(request("GET", "/ctx/ordinary")));
    }

    @Test
    void aRuntimeWithoutDirectRouteSupportFailsToStart() {
        RuntimeException error = assertThrows(RuntimeException.class,
            () -> ApplicationContext.run(Map.of("spec.name", "DirectRoutesTestUnsupported")).close());
        assertTrue(message(error).contains("the server runtime does not answer them"), message(error));
    }

    @Test
    void aConditionThatNeedsTheRequestIsRejected() {
        assertRejected(routes -> routes.GET("/lambda").where(RouteCondition.custom(request -> true)).respond(HttpResponse.ok()), "a condition of a lambda");
        assertRejected(routes -> routes.GET("/client").where(RouteCondition.remoteAddress("10.0.0.0/8")).respond(HttpResponse.ok()), "a client address condition");
        assertRejected(routes -> routes.GET("/nested")
            .where(RouteCondition.header("X-A").or(RouteCondition.remoteAddress("10.0.0.0/8")))
            .respond(HttpResponse.ok()), "a client address condition");
    }

    @Test
    void aQueryConditionDecodesTheQueryOnlyWhenARouteWithOneIsTried() {
        TestRequest debug = request("GET", "/ctx/search").query("mode", "debug");
        assertEquals("debug search", text(find(debug)));
        assertEquals(1, debug.queryReads);
        TestRequest plain = request("GET", "/ctx/search").query("mode", "other");
        assertEquals("search", text(find(plain)));
        // a request no route with a query condition matches never reads the query
        TestRequest health = request("GET", "/ctx/health").query("mode", "debug");
        assertEquals("UP", text(find(health)));
        assertEquals(0, health.queryReads);
    }

    @Test
    void aTimeConditionReadsTheClock() {
        assertEquals("open", text(find("GET", "/ctx/window")));
        assertNull(find("GET", "/ctx/closed"));
    }

    @Test
    void aRouteDeclaredAfterTheBeanReturnedIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> Routes.builder.GET("/late", HttpResponse.ok()));
        assertTrue(error.getMessage().contains("The direct routes are already built"), error.getMessage());
        assertThrows(IllegalStateException.class, () -> Routes.builder.path("/late", late -> { }));
    }

    @Test
    void anAsynchronousRouteIsPendingThenAnsweredByFindAsync() throws Exception {
        int calls = Routes.asyncCalls;
        // matched, not called
        assertSame(DirectRouteLookup.PENDING, find("GET", "/ctx/async/hello"));
        assertEquals(calls, Routes.asyncCalls);
        assertEquals("hello async", text(findAsync("GET", "/ctx/async/hello")));
        assertEquals(calls + 1, Routes.asyncCalls);
        // the implicit HEAD route
        assertSame(DirectRouteLookup.PENDING, find("HEAD", "/ctx/async/hello"));
        assertEquals("hello async", text(findAsync("HEAD", "/ctx/async/hello")));
        // no route matches
        assertNull(directRoutes.findAsync(request("GET", "/ctx/async-none"), FACTORY));
    }

    @Test
    void aRouteOnAnExecutorRunsThereAfterItIsMatchedOnTheCallingThread() throws Exception {
        assertSame(DirectRouteLookup.PENDING, find("GET", "/ctx/on-executor/7"));
        Thread caller = Thread.currentThread();
        assertEquals("item 7", text(findAsync("GET", "/ctx/on-executor/7")));
        assertSame(caller, Routes.constraintThread);
        assertNotSame(caller, Routes.executorThread);
        // the blocking executor: virtual threads where available
        assertTrue(Routes.executorThread.isVirtual() || Routes.executorThread.getName().contains("blocking"), Routes.executorThread.getName());
        // a value on an executor, and nonBlocking, the default, which undoes it
        assertSame(DirectRouteLookup.PENDING, find("GET", "/ctx/value-on-executor"));
        assertEquals("value", text(findAsync("GET", "/ctx/value-on-executor")));
        assertEquals("not pending", text(find("GET", "/ctx/non-blocking")));
    }

    @Test
    void anAsynchronousRouteDeclinesFailsAndIsCancelled() throws Exception {
        CompletionStage<@Nullable HttpResponse<?>> declined = directRoutes.findAsync(request("GET", "/ctx/async/declined"), FACTORY);
        assertNotNull(declined);
        assertNull(declined.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(500, findAsync("GET", "/ctx/async/failed").code());
        assertEquals(500, findAsync("GET", "/ctx/async/throwing").code());
        assertEquals(500, findAsync("GET", "/ctx/async/no-stage").code());
        assertEquals(500, findAsync("GET", "/ctx/throwing-on-executor").code());

        CompletionStage<@Nullable HttpResponse<?>> hanging = directRoutes.findAsync(request("GET", "/ctx/async/hanging"), FACTORY);
        assertNotNull(hanging);
        CompletableFuture<HttpResponse<?>> routeStage = Routes.hanging;
        assertFalse(routeStage.isDone());
        hanging.toCompletableFuture().cancel(false);
        // the stage of the route is cancelled with the stage of the lookup
        assertTrue(routeStage.isCancelled());
    }

    @Test
    void everyRuntimeWithDirectRouteSupportAnswersAsynchronousRoutes() {
        // DirectRouteSupport has no method: its presence is enough for the asynchronous routes too
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "DirectRoutesTestPlainSupport"))) {
            DirectRouteLookup lookup = context.getBean(DirectRouteLookup.class);
            assertSame(DirectRouteLookup.PENDING, lookup.find(request("GET", "/report"), FACTORY));
            CompletionStage<@Nullable HttpResponse<?>> report = lookup.findAsync(request("GET", "/report"), FACTORY);
            assertNotNull(report);
            assertEquals("report", report.toCompletableFuture().join().body());
        }
    }

    @Test
    void anUnknownExecutorFailsToStart() {
        assertRejected(routes -> routes.GET("/missing").executeOn("missing").respond(HttpResponse.ok()),
            "No executor configured for name: missing, of the direct route GET /missing");
    }

    @Test
    void aCustomMethodIsRejected() {
        assertRejected(routes -> routes.route(HttpMethod.CUSTOM, "/custom").respond(HttpResponse.ok()), "HttpMethod.CUSTOM is not the name of a method");
    }

    @Test
    void aRouteWithoutATerminalFailsToStart() {
        assertRejected(routes -> routes.GET("/pending").order(-1),
            "The direct route GET /pending declared by DirectRoutesTest.RejectedRoutes has no response: end it with respond or respondAsync");
    }

    @Test
    void aRouteWithoutATerminalUnderAPrefixFailsWhenItsLambdaReturns() {
        assertRejected(routes -> routes.path("/internal", internal -> {
            internal.GET("/ping", HttpResponse.ok("pong"));
            internal.route(Set.of(HttpMethod.POST), "/pending");
        }), "The direct route POST /internal/pending declared by DirectRoutesTest.RejectedRoutes has no response");
    }

    @Test
    void aRouteEndsWithOneTerminal() {
        assertRejected(routes -> {
            DirectRouteSpec route = routes.GET("/once");
            route.respond(HttpResponse.ok());
            route.order(1);
        }, "The direct route GET /once declared by DirectRoutesTest.RejectedRoutes was already ended");
    }

    private static void assertRejected(Consumer<DirectRouteBuilder> declare, String message) {
        assertRejected(declare, message, "DirectRoutesTestRejected");
    }

    private static void assertRejected(Consumer<DirectRouteBuilder> declare, String message, String specName) {
        RejectedRoutes.declare = declare;
        try {
            RuntimeException error = assertThrows(RuntimeException.class,
                () -> ApplicationContext.run(Map.of("spec.name", specName)).close());
            assertTrue(message(error).contains(message), message(error));
        } finally {
            RejectedRoutes.declare = null;
        }
    }

    private static String message(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    private static @Nullable HttpResponse<?> find(String method, String path) {
        return find(request(method, path));
    }

    private static @Nullable HttpResponse<?> find(TestRequest request) {
        return directRoutes.find(request, FACTORY);
    }

    private static @Nullable HttpResponse<?> findAsync(String method, String path) throws Exception {
        CompletionStage<@Nullable HttpResponse<?>> stage = directRoutes.findAsync(request(method, path), FACTORY);
        assertNotNull(stage, method + " " + path);
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static TestRequest request(String method, String path) {
        return new TestRequest(method, path);
    }

    private static String text(@Nullable HttpResponse<?> response) {
        if (response == null) {
            return "no response";
        }
        Object body = response.body();
        return body instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(body);
    }

    /**
     * A response factory of a server runtime.
     */
    static final class CountingFactory implements HttpResponseFactory {
        volatile int created;

        @Override
        public <T> MutableHttpResponse<T> ok(@Nullable T body) {
            created++;
            return HttpResponseFactory.INSTANCE.ok(body);
        }

        @Override
        public <T> MutableHttpResponse<T> status(HttpStatus status, @Nullable String reason) {
            created++;
            return HttpResponseFactory.INSTANCE.status(status, reason);
        }

        @Override
        public <T> MutableHttpResponse<T> status(int status, @Nullable String reason) {
            created++;
            return HttpResponseFactory.INSTANCE.status(status, reason);
        }

        @Override
        public <T> MutableHttpResponse<T> status(HttpStatus status, @Nullable T body) {
            created++;
            return HttpResponseFactory.INSTANCE.status(status, body);
        }
    }

    /**
     * A request as a server received it.
     */
    static final class TestRequest implements DirectRequest {
        private final String method;
        private final String path;
        private final List<Map.Entry<String, String>> headers = new ArrayList<>();
        private final List<Map.Entry<String, String>> query = new ArrayList<>();
        private @Nullable InetSocketAddress peer;
        int queryReads;

        TestRequest(String method, String path) {
            this.method = method;
            this.path = path;
        }

        TestRequest header(String name, String value) {
            headers.add(Map.entry(name, value));
            return this;
        }

        TestRequest query(String name, String value) {
            query.add(Map.entry(name, value));
            return this;
        }

        @Override
        public List<String> queryParameters(String name) {
            queryReads++;
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> parameter : query) {
                if (parameter.getKey().equals(name)) {
                    values.add(parameter.getValue());
                }
            }
            return values;
        }

        TestRequest peer(String address) {
            peer = new InetSocketAddress(address, 50000);
            return this;
        }

        @Override
        public String methodName() {
            return method;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public List<String> headers(String name) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> header : headers) {
                if (header.getKey().equalsIgnoreCase(name)) {
                    values.add(header.getValue());
                }
            }
            return values;
        }

        @Override
        public @Nullable InetSocketAddress peerAddress() {
            return peer;
        }
    }

    /**
     * The server runtime of the test answers direct routes.
     */
    @Singleton
    @Requires(property = "spec.name", pattern = "DirectRoutesTest(Rejected|PlainSupport)?")
    static class TestDirectRouteSupport implements DirectRouteSupport {
    }

    @Singleton
    @Requires(property = "spec.name", value = "DirectRoutesTestPlainSupport")
    static class PlainSupportRoutes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/health", HttpResponse.ok("UP"));
            routes.GET("/report").respondAsync(direct -> CompletableFuture.completedFuture(HttpResponse.ok("report")));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes {
        static volatile DirectRouteBuilder builder;
        static volatile int asyncCalls;
        static volatile Thread constraintThread;
        static volatile Thread executorThread;
        static volatile CompletableFuture<HttpResponse<?>> hanging;
        private int counter;

        @Override
        public void routes(DirectRouteBuilder routes) {
            builder = routes;
            routes.GET("/health", HttpResponse.ok("UP"));
            byte[] bytes = "bytes".getBytes(StandardCharsets.UTF_8);
            routes.GET("/bytes", HttpResponse.ok(bytes).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE));
            // the array is copied when the route is declared
            bytes[0] = 'X';
            routes.path("/api", api -> api.GET("/ping", HttpResponse.ok("pong")));

            routes.GET("/files/{name}").respond(direct -> direct.responses().ok("file " + direct.pathVariables().getString("name")));
            routes.GET("/files/special", HttpResponse.ok("special"));
            routes.GET("/ordered").order(2).respond(HttpResponse.ok("second"));
            routes.GET("/ordered").order(1).respond(HttpResponse.ok("first"));
            routes.GET("/ambiguous", HttpResponse.ok("one"));
            routes.GET("/ambiguous", HttpResponse.ok("two"));

            routes.HEAD("/explicit-head", HttpResponse.ok("explicit head"));
            routes.GET("/explicit-head", HttpResponse.ok("get"));

            routes.GET("/beta").where(RouteCondition.any(
                RouteCondition.header("X-Channel", ValueMatcher.equalTo("beta").ignoringCase()),
                RouteCondition.cookie("channel", ValueMatcher.equalTo("beta"))))
                .respond(HttpResponse.ok("beta"));
            routes.GET("/internal")
                .where(RouteCondition.peerAddress("10.0.0.0/8"))
                .where(RouteCondition.host("admin.local"))
                .respond(HttpResponse.ok("internal"));
            routes.GET("/{+path}")
                .where(RouteCondition.peerAddress("203.0.113.0/24"))
                .respond(HttpResponse.status(HttpStatus.FORBIDDEN).body("blocked"));

            routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST), "/blocked/{+path}")
                .where(RouteCondition.header("X-Block"))
                .respond(HttpResponse.status(HttpStatus.FORBIDDEN));

            routes.GET("/shops/{shop}")
                .constrain("shop", List.of("north", "south"))
                .respond(direct -> direct.responses().ok("shop " + direct.pathVariables().getString("shop")));
            routes.GET("/items/{id}").respond(direct -> direct.responses().ok("item " + direct.pathVariables().getLong("id")));

            routes.GET("/counter").respond(direct -> direct.responses().ok(String.valueOf(++counter)));
            routes.GET("/null").respond(direct -> null);
            // a response of another factory
            routes.GET("/foreign").respond(direct -> HttpResponse.accepted().body("foreign"));
            routes.GET("/search").where(RouteCondition.query("mode", "debug")).order(-1).respond(HttpResponse.ok("debug search"));
            routes.GET("/search", HttpResponse.ok("search"));
            routes.GET("/window")
                .where(RouteCondition.between(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600)))
                .respond(HttpResponse.ok("open"));
            routes.GET("/closed").where(RouteCondition.after(Instant.now().plusSeconds(3600))).respond(HttpResponse.ok("closed"));
            routes.GET("/failing").respond(direct -> {
                throw new IllegalStateException("failed");
            });

            routes.GET("/async/hello").respondAsync(direct -> {
                asyncCalls++;
                return CompletableFuture.supplyAsync(() -> direct.responses().ok("hello async"));
            });
            routes.GET("/async/declined").respondAsync(direct -> CompletableFuture.supplyAsync(() -> null));
            routes.GET("/async/failed").respondAsync(direct -> CompletableFuture.failedFuture(new IllegalStateException("failed")));
            routes.GET("/async/throwing").respondAsync(direct -> {
                throw new IllegalStateException("failed");
            });
            routes.GET("/async/no-stage").respondAsync(direct -> null);
            routes.GET("/async/hanging").respondAsync(direct -> {
                CompletableFuture<HttpResponse<?>> stage = new CompletableFuture<>();
                hanging = stage;
                return stage;
            });
            routes.GET("/on-executor/{id}").constrain(variables -> {
                constraintThread = Thread.currentThread();
                return true;
            }).executeOn(TaskExecutors.BLOCKING)
            .respond(direct -> {
                executorThread = Thread.currentThread();
                return direct.responses().ok("item " + direct.pathVariables().getLong("id"));
            });
            routes.GET("/value-on-executor").executeOn(TaskExecutors.BLOCKING).respond(HttpResponse.ok("value"));
            routes.GET("/non-blocking").executeOn(TaskExecutors.BLOCKING).nonBlocking().respond(HttpResponse.ok("not pending"));
            routes.GET("/throwing-on-executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> {
                throw new IllegalStateException("failed");
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OrdinaryRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/ordinary", (request, pathVariables) -> HttpResponse.ok("ordinary"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "DirectRoutesTestUnsupported")
    static class UnsupportedRoutes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/health", HttpResponse.ok("UP"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "DirectRoutesTestRejected")
    static class RejectedRoutes implements HttpDirectRoutes {
        static volatile @Nullable Consumer<DirectRouteBuilder> declare;

        @Override
        public void routes(DirectRouteBuilder routes) {
            Consumer<DirectRouteBuilder> routesToDeclare = declare;
            if (routesToDeclare != null) {
                routesToDeclare.accept(routes);
            }
        }
    }
}
