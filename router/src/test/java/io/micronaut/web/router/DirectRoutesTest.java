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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.DirectRouteSpec;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import io.micronaut.web.router.builder.DirectContext;
import io.micronaut.web.router.direct.DirectMatch;
import io.micronaut.web.router.direct.DirectRequest;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.DirectRouteSupport;
import io.micronaut.web.router.direct.InvalidDirectRequestException;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lookup of direct routes, {@link DirectRouteLookup}, as a server runtime sees it, matching a
 * request and then answering it with the {@link DirectMatch} of its kind: the URI
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
        // equally specific and of the same order, without a condition or a constraint: every
        // request would be ambiguous, so the routes fail to build
        assertRejected(routes -> {
            routes.GET("/ambiguous", HttpResponse.ok("one"));
            routes.GET("/ambiguous", HttpResponse.ok("two"));
        }, "The direct route GET /ambiguous declared by DirectRoutesTest.RejectedRoutes and the direct route GET /ambiguous "
            + "declared by DirectRoutesTest.RejectedRoutes have the same URI template and order, and no condition or constraint");
    }

    @Test
    void aHeadRequestIsAnsweredByTheGetRouteUnlessAHeadRouteMatches() {
        assertEquals("UP", text(find("HEAD", "/ctx/health")));
        assertEquals("explicit head", text(find("HEAD", "/ctx/explicit-head")));
        assertNull(find("POST", "/ctx/health"));
    }

    @Test
    void aHeadRouteThatDeclinesContinuesToTheOrdinaryRoutesNotToTheGetRoute() throws Exception {
        int calls = Routes.headDeclines;
        // the HEAD route matched and declined: the GET route does not answer instead
        assertNull(find("HEAD", "/ctx/head-declines"));
        assertEquals(calls + 1, Routes.headDeclines);
        assertEquals("get", text(find("GET", "/ctx/head-declines")));
        // the same for an asynchronous HEAD route, whose stage declines
        assertNull(findAsync("HEAD", "/ctx/async-head-declines"));
        assertEquals("get", text(find("GET", "/ctx/async-head-declines")));
        // and for a HEAD route that declines before an asynchronous GET route: called once
        calls = Routes.headDeclines;
        assertNull(find("HEAD", "/ctx/head-declines-async-get"));
        assertEquals(calls + 1, Routes.headDeclines);
    }

    @Test
    void aHeadRequestRanksTheHeadAndGetRoutesTogetherLikeTheRouter() {
        // the more specific GET route wins over a matching HEAD route
        assertEquals("rank health", text(find("HEAD", "/ctx/rank/health")));
        assertEquals(403, find("HEAD", "/ctx/rank/other").code());
        // equally specific: the explicit HEAD route wins, whatever its order
        assertEquals("explicit head", text(find("HEAD", "/ctx/rank/ordered")));
        assertEquals("get", text(find("GET", "/ctx/rank/ordered")));
    }

    @Test
    void anErrorOfAFunctionIsAnsweredWith500() throws Exception {
        assertEquals(500, find("GET", "/ctx/error/sync").code());
        assertEquals(500, findAsync("GET", "/ctx/error/executor").code());
        assertEquals(500, findAsync("GET", "/ctx/error/async").code());
    }

    @Test
    void aResponseCompletedAfterItsStageWasCancelledIsDiscarded() throws Exception {
        TestDirectRouteSupport.discarded.clear();
        // a function on an executor, still running when the stage is cancelled
        Routes.lateRunning = new CountDownLatch(1);
        Routes.lateRelease = new CountDownLatch(1);
        CompletionStage<@Nullable HttpResponse<?>> onExecutor = start("GET", "/ctx/late/executor");
        assertTrue(Routes.lateRunning.await(5, TimeUnit.SECONDS));
        onExecutor.toCompletableFuture().cancel(false);
        Routes.lateRelease.countDown();
        assertEquals("late executor", text(TestDirectRouteSupport.discarded.poll(5, TimeUnit.SECONDS)));

        // a stage of the route that cancelling does not reach, completed later
        Routes.lateStage = new CompletableFuture<>();
        CompletionStage<@Nullable HttpResponse<?>> later = start("GET", "/ctx/late/stage");
        later.toCompletableFuture().cancel(false);
        Routes.lateStage.complete(FACTORY.ok("late stage"));
        assertEquals("late stage", text(TestDirectRouteSupport.discarded.poll(5, TimeUnit.SECONDS)));

        // a response completed in time is written, never discarded
        assertEquals("hello async", text(findAsync("GET", "/ctx/async/hello")));
        assertNull(TestDirectRouteSupport.discarded.poll());
    }

    @Test
    void aMatchHasTheKindOfItsRouteAndRunsNothing() throws Exception {
        // a value: its match is allocated once
        DirectMatch value = directRoutes.match(request("GET", "/ctx/health"));
        assertInstanceOf(DirectMatch.Sync.class, value);
        assertSame(value, directRoutes.match(request("GET", "/ctx/health")));
        // a function: matched for the request, run by the runtime
        assertInstanceOf(DirectMatch.Sync.class, directRoutes.match(request("GET", "/ctx/items/7")));
        int calls = Routes.asyncCalls;
        DirectMatch async = directRoutes.match(request("GET", "/ctx/async/hello"));
        assertEquals(calls, Routes.asyncCalls);
        CompletableFuture<@Nullable HttpResponse<?>> response = assertInstanceOf(DirectMatch.Async.class, async).respondAsync(FACTORY);
        assertEquals("hello async", text(response.get(5, TimeUnit.SECONDS)));
        assertEquals(calls + 1, Routes.asyncCalls);
        // a function on an executor
        assertInstanceOf(DirectMatch.Async.class, directRoutes.match(request("GET", "/ctx/error/executor")));
    }

    @Test
    void theConstraintsOfAnAsynchronousRouteRunOnce() throws Exception {
        int calls = Routes.asyncConstraints;
        assertEquals("counted 1", text(findAsync("GET", "/ctx/async/counted/1")));
        assertEquals(calls + 1, Routes.asyncConstraints);
    }

    @Test
    void theRuntimePreparesABodyItConsumesOnceForEveryRequest() {
        HttpResponse<?> first = find("GET", "/ctx/consumed");
        HttpResponse<?> second = find("GET", "/ctx/consumed");
        // the runtime of the test copies the body to bytes, once, when the route is declared
        assertEquals("consumed", text(first));
        assertSame(first.body(), second.body());
        // text and byte[] bodies are prepared by the router, and never given to the runtime
        assertFalse(TestDirectRouteSupport.prepared.contains(String.class), TestDirectRouteSupport.prepared.toString());
        assertFalse(TestDirectRouteSupport.prepared.contains(byte[].class), TestDirectRouteSupport.prepared.toString());
    }

    @Test
    void aMethodWithoutDirectRoutesDoesNotReadThePath() {
        TestRequest patch = request("PATCH", "/ctx/health");
        assertNull(find(patch));
        assertEquals(0, patch.pathReads);
        TestRequest head = request("HEAD", "/ctx/health");
        assertEquals("UP", text(find(head)));
        assertEquals(1, head.pathReads);
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
    void anAsynchronousRouteIsStartedByItsMatchAndAnswersLater() throws Exception {
        int calls = Routes.asyncCalls;
        // matched once, and started: the response of the route completes later
        CompletableFuture<@Nullable HttpResponse<?>> pending = start("GET", "/ctx/async/hello");
        assertEquals(calls + 1, Routes.asyncCalls);
        assertEquals("hello async", text(pending.get(5, TimeUnit.SECONDS)));
        assertEquals(calls + 1, Routes.asyncCalls);
        // the implicit HEAD route
        assertEquals("hello async", text(findAsync("HEAD", "/ctx/async/hello")));
        // no route matches
        assertNull(find("GET", "/ctx/async-none"));
    }

    @Test
    void aRouteOnAnExecutorRunsThereAfterItIsMatchedOnTheCallingThread() throws Exception {
        Thread caller = Thread.currentThread();
        assertEquals("item 7", text(findAsync("GET", "/ctx/on-executor/7")));
        assertSame(caller, Routes.constraintThread);
        assertNotSame(caller, Routes.executorThread);
        // the blocking executor: virtual threads where available
        assertTrue(Routes.executorThread.isVirtual() || Routes.executorThread.getName().contains("blocking"), Routes.executorThread.getName());
        // a value on an executor is rejected, and nonBlocking, the default, undoes the executor
        assertRejected(routes -> routes.GET("/value-on-executor").executeOn(TaskExecutors.BLOCKING).respond(HttpResponse.ok("value")),
            "The direct route GET /value-on-executor declared by DirectRoutesTest.RejectedRoutes answers with a response given as a value");
        assertEquals("not pending", text(find("GET", "/ctx/non-blocking")));
    }

    @Test
    void anAsynchronousRouteDeclinesFailsAndIsCancelled() throws Exception {
        assertNull(findAsync("GET", "/ctx/async/declined"));
        assertEquals(500, findAsync("GET", "/ctx/async/failed").code());
        assertEquals(500, findAsync("GET", "/ctx/async/throwing").code());
        assertEquals(500, findAsync("GET", "/ctx/async/no-stage").code());
        assertEquals(500, findAsync("GET", "/ctx/throwing-on-executor").code());

        CompletionStage<@Nullable HttpResponse<?>> hanging = start("GET", "/ctx/async/hanging");
        CompletableFuture<HttpResponse<?>> routeStage = Routes.hanging;
        assertFalse(routeStage.isDone());
        hanging.toCompletableFuture().cancel(false);
        // the stage of the route is cancelled with the stage of the lookup
        assertTrue(routeStage.isCancelled());
    }

    @Test
    void everyRuntimeWithDirectRouteSupportAnswersAsynchronousRoutes() {
        // the presence of DirectRouteSupport is enough for the asynchronous routes too
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "DirectRoutesTestPlainSupport"))) {
            DirectRouteLookup lookup = context.getBean(DirectRouteLookup.class);
            CompletionStage<@Nullable HttpResponse<?>> report = assertInstanceOf(DirectMatch.Async.class, lookup.match(request("GET", "/report")))
                .respondAsync(FACTORY);
            assertEquals("report", report.toCompletableFuture().join().body());
        }
    }

    @Test
    void anUnknownExecutorFailsToStart() {
        // a function: a value on an executor is rejected before its executor is looked up
        assertRejected(routes -> routes.GET("/missing").executeOn("missing").respond(direct -> HttpResponse.ok()),
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

    @Test
    void routesAmbiguousThroughTheirConditionsOrConstraintsAreAnsweredWith400() {
        assertEquals("one", text(find(request("GET", "/ctx/ambiguous-conditions").header("X-One", "1"))));
        assertEquals(400, find(request("GET", "/ctx/ambiguous-conditions").header("X-One", "1").header("X-Two", "2")).code());
        assertEquals("two", text(find("GET", "/ctx/ambiguous-constraints/3")));
        assertEquals(400, find("GET", "/ctx/ambiguous-constraints/2").code());
    }

    @Test
    void exactDuplicatesFailToStartWhateverTheirMethodsWereDeclaredWith() {
        // a route of several methods duplicates the route of one of them
        assertRejected(routes -> {
            routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST), "/both").respond(HttpResponse.ok());
            routes.POST("/both", HttpResponse.accepted());
        }, "The direct route POST /both declared by DirectRoutesTest.RejectedRoutes and the direct route POST /both");
        // a function and a value
        assertRejected(routes -> {
            routes.GET("/mixed").respond(direct -> HttpResponse.ok());
            routes.GET("/mixed", HttpResponse.ok());
        }, "have the same URI template and order, and no condition or constraint");
    }

    @Test
    void theFunctionReadsTheRequest() {
        // a route of several methods reads the method, the implicit HEAD route too
        assertEquals("GET", text(find("GET", "/ctx/method")));
        assertEquals("POST", text(find("POST", "/ctx/method")));
        assertEquals("HEAD", text(find("HEAD", "/ctx/method")));
        // a conditional GET
        assertEquals(304, find(request("GET", "/ctx/etag").header("If-None-Match", "v1")).code());
        HttpResponse<?> changed = find(request("GET", "/ctx/etag").header("If-None-Match", "v0"));
        assertEquals(200, changed.code());
        assertEquals("v1", changed.getHeaders().get("ETag"));
        // the query and the peer, read when the function asks for them
        TestRequest query = request("GET", "/ctx/query").query("q", "a").query("q", "b").peer("10.0.0.1");
        assertEquals("q=a all=[a, b] peer=/10.0.0.1:50000", text(find(query)));
        assertEquals("q=null all=[] peer=null", text(find("GET", "/ctx/query")));
    }

    @Test
    void anInvalidRequestIsNeverAnsweredByADirectRoute() throws Exception {
        // the target: not matched, the runtime answers the request on its ordinary path
        assertNull(directRoutes.match(request("GET", "/ctx/health").invalidPath()));
        // the query a condition reads: not matched
        assertNull(directRoutes.match(request("GET", "/ctx/search").invalidQuery()));
        // the query the function reads: the route declines, on the calling thread, on an executor or in its stage
        assertNull(find(request("GET", "/ctx/query").invalidQuery()));
        assertNull(findAsync(request("GET", "/ctx/query-async").invalidQuery()));
        assertNull(findAsync(request("GET", "/ctx/query-stage").invalidQuery()));
        assertEquals("q=x", text(findAsync(request("GET", "/ctx/query-stage").query("q", "x"))));
    }

    @Test
    void theOverloadsOfTheConstraintsAreTheOnesOfAnOrdinaryRoute() {
        assertEquals("typed 5", text(find("GET", "/ctx/typed/5")));
        assertNull(find("GET", "/ctx/typed/0"));
        // the value does not convert
        assertNull(find("GET", "/ctx/typed/abc"));
        assertEquals("pair", text(find("GET", "/ctx/pair/x/y")));
        assertNull(find("GET", "/ctx/pair/x/z"));
    }

    @Test
    void aContextOfAFunctionCanBeCreatedForATest() {
        TestRequest request = request("GET", "/greetings/ada").header("Accept-Language", "en");
        DirectContext directContext = DirectContext.of(request, Map.of("name", "ada", "id", "7"));
        assertSame(request, directContext.request());
        assertEquals("ada", directContext.pathVariables().getString("name"));
        assertEquals(7L, directContext.pathVariables().getLong("id"));
        assertEquals("en", directContext.request().header("Accept-Language"));
        assertEquals(HttpMethod.GET, directContext.request().method());
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

    /**
     * The response of a synchronous route, as a runtime answers it: {@code null} if no route
     * matches, or the route declines.
     */
    private static @Nullable HttpResponse<?> find(TestRequest request) {
        DirectMatch match = directRoutes.match(request);
        return match == null ? null : assertInstanceOf(DirectMatch.Sync.class, match, request.toString()).respond(FACTORY);
    }

    /**
     * The response of an asynchronous route, once it completes.
     */
    private static @Nullable HttpResponse<?> findAsync(String method, String path) throws Exception {
        return findAsync(request(method, path));
    }

    private static @Nullable HttpResponse<?> findAsync(TestRequest request) throws Exception {
        return start(request).get(5, TimeUnit.SECONDS);
    }

    /**
     * Start an asynchronous route, as a runtime does.
     */
    private static CompletableFuture<@Nullable HttpResponse<?>> start(String method, String path) {
        return start(request(method, path));
    }

    private static CompletableFuture<@Nullable HttpResponse<?>> start(TestRequest request) {
        return assertInstanceOf(DirectMatch.Async.class, directRoutes.match(request), request.toString()).respondAsync(FACTORY);
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
        private boolean invalidPath;
        private boolean invalidQuery;
        int queryReads;
        int pathReads;

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

        /**
         * The target is not valid, which a runtime finds when the path is first read.
         */
        TestRequest invalidPath() {
            invalidPath = true;
            return this;
        }

        /**
         * The query is not valid, which a runtime finds when it is first decoded.
         */
        TestRequest invalidQuery() {
            invalidQuery = true;
            return this;
        }

        @Override
        public List<String> queryParameters(String name) {
            queryReads++;
            if (invalidQuery) {
                throw new InvalidDirectRequestException(new IllegalArgumentException("malformed escape"));
            }
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
            pathReads++;
            if (invalidPath) {
                throw new InvalidDirectRequestException(new IllegalArgumentException("malformed escape"));
            }
            return path;
        }

        @Override
        public String toString() {
            return method + " " + path;
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
        /**
         * The types of the bodies given to {@link #shareableBody(Object)}.
         */
        static final Set<Class<?>> prepared = ConcurrentHashMap.newKeySet();
        /**
         * The responses given to {@link #discard(HttpResponse)}.
         */
        static final BlockingQueue<HttpResponse<?>> discarded = new LinkedBlockingQueue<>();

        @Override
        public Object shareableBody(Object body) {
            prepared.add(body.getClass());
            // a body the runtime of the test consumes when it writes it
            return body instanceof ConsumedBody consumed ? consumed.text().getBytes(StandardCharsets.UTF_8) : body;
        }

        @Override
        public void discard(HttpResponse<?> response) {
            discarded.add(response);
        }
    }

    /**
     * A body the server runtime of the test consumes when it writes it, like a buffer it releases.
     *
     * @param text The text of the body
     */
    record ConsumedBody(String text) {
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
        static volatile int asyncConstraints;
        static volatile int headDeclines;
        static volatile CountDownLatch lateRunning = new CountDownLatch(0);
        static volatile CountDownLatch lateRelease = new CountDownLatch(0);
        static volatile CompletableFuture<HttpResponse<?>> lateStage = new CompletableFuture<>();
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

            routes.GET("/files/{name}").respond(direct -> HttpResponse.ok("file " + direct.pathVariables().getString("name")));
            routes.GET("/files/special", HttpResponse.ok("special"));
            routes.GET("/ordered").order(2).respond(HttpResponse.ok("second"));
            routes.GET("/ordered").order(1).respond(HttpResponse.ok("first"));
            routes.GET("/ambiguous-conditions").where(RouteCondition.header("X-One")).respond(HttpResponse.ok("one"));
            routes.GET("/ambiguous-conditions").where(RouteCondition.header("X-Two")).respond(HttpResponse.ok("two"));
            routes.GET("/ambiguous-constraints/{id}").constrain("id", List.of("1", "2")).respond(HttpResponse.ok("one"));
            routes.GET("/ambiguous-constraints/{id}").constrain("id", List.of("2", "3")).respond(HttpResponse.ok("two"));

            routes.HEAD("/explicit-head", HttpResponse.ok("explicit head"));
            routes.GET("/explicit-head", HttpResponse.ok("get"));
            routes.HEAD("/head-declines").respond(direct -> {
                headDeclines++;
                return null;
            });
            routes.GET("/head-declines", HttpResponse.ok("get"));
            routes.HEAD("/async-head-declines").respondAsync(direct -> CompletableFuture.completedFuture(null));
            routes.GET("/async-head-declines", HttpResponse.ok("get"));
            routes.HEAD("/head-declines-async-get").respond(direct -> {
                headDeclines++;
                return null;
            });
            routes.GET("/head-declines-async-get").respondAsync(direct -> CompletableFuture.completedFuture(HttpResponse.ok("get")));
            routes.GET("/consumed", HttpResponse.ok(new ConsumedBody("consumed")));
            routes.HEAD("/rank/{+rest}", HttpResponse.status(HttpStatus.FORBIDDEN));
            routes.GET("/rank/health", HttpResponse.ok("rank health"));
            routes.HEAD("/rank/ordered").order(5).respond(HttpResponse.ok("explicit head"));
            routes.GET("/rank/ordered").order(1).respond(HttpResponse.ok("get"));
            routes.GET("/error/sync").respond(direct -> {
                throw new AssertionError("failed");
            });
            routes.GET("/error/executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> {
                throw new AssertionError("failed");
            });
            routes.GET("/error/async").respondAsync(direct -> {
                throw new AssertionError("failed");
            });
            routes.GET("/late/executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> {
                lateRunning.countDown();
                try {
                    lateRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return HttpResponse.ok("late executor");
            });
            // a minimal stage: cancelling its CompletableFuture copy does not reach it
            routes.GET("/late/stage").respondAsync(direct -> lateStage.minimalCompletionStage());

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
                .respond(direct -> HttpResponse.ok("shop " + direct.pathVariables().getString("shop")));
            routes.GET("/items/{id}").respond(direct -> HttpResponse.ok("item " + direct.pathVariables().getLong("id")));

            routes.GET("/counter").respond(direct -> HttpResponse.ok(String.valueOf(++counter)));
            routes.GET("/null").respond(direct -> null);
            routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST), "/method").respond(direct -> HttpResponse.ok(direct.request().method().name()));
            routes.GET("/etag").respond(direct -> "v1".equals(direct.request().header("If-None-Match"))
                ? HttpResponse.notModified()
                : HttpResponse.ok("body").header("ETag", "v1"));
            routes.GET("/query").respond(direct -> HttpResponse.ok("q=" + direct.request().queryParameter("q")
                + " all=" + direct.request().queryParameters("q") + " peer=" + direct.request().peerAddress()));
            routes.GET("/query-async").executeOn(TaskExecutors.BLOCKING).respond(direct -> HttpResponse.ok("q=" + direct.request().queryParameter("q")));
            routes.GET("/query-stage").respondAsync(direct -> CompletableFuture.supplyAsync(() -> HttpResponse.ok("q=" + direct.request().queryParameter("q"))));
            routes.GET("/typed/{id}").constrain("id", Long.class, id -> id > 0).respond(direct -> HttpResponse.ok("typed " + direct.pathVariables().getLong("id")));
            routes.GET("/pair/{a}/{b}").constrain(Map.of("a", ValueMatcher.equalTo("x"), "b", ValueMatcher.equalTo("y"))).respond(HttpResponse.ok("pair"));
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
                return CompletableFuture.supplyAsync(() -> HttpResponse.ok("hello async"));
            });
            routes.GET("/async/declined").respondAsync(direct -> CompletableFuture.supplyAsync(() -> null));
            routes.GET("/async/counted/{id}").constrain(variables -> {
                asyncConstraints++;
                return true;
            }).respondAsync(direct -> CompletableFuture.completedFuture(HttpResponse.ok("counted " + direct.pathVariables().getString("id"))));
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
                return HttpResponse.ok("item " + direct.pathVariables().getLong("id"));
            });
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
