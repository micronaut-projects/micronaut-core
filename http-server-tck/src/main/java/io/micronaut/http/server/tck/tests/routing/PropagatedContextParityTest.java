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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The propagated context seen by a handler route is the propagated context seen by the controller
 * route that does the same, with the same annotation filters: every scenario is answered once by a
 * controller and once by handler routes, on the same path of two servers, and what the route, its
 * continuations, the filters and the error and status routes observe must be the same.
 *
 * <p>An annotation filter adds a trace and an MDC context for every request, like the MDC filter
 * of the documentation. The routes under {@code /parity/r} also have filters of their own: an
 * annotation filter of that path for the controllers, route filters for the handler routes, which
 * add a second element, change the MDC context, and observe the context in the response filters.
 * An observation is the trace element, the element of the route filter, the MDC value and whether
 * {@link ServerRequestContext#currentRequest()} has the request.</p>
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class PropagatedContextParityTest {
    public static final String SPEC_NAME = "PropagatedContextParityTest";
    private static final String VARIANT = "parity.variant";
    private static final String CONTROLLER = "controller";
    private static final String FUNCTIONAL = "functional";
    private static final String TRACE = "X-Trace";

    private static final String JSON = "{\"name\":\"hello\"}";
    private static final String FORM = "name=hello";
    private static final String ELEMENTS = "[\"hello\",\"world\"]";
    private static final String BOUNDARY = "parity-boundary";
    private static final String MULTIPART = "--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"name\"\r\n"
        + "\r\n"
        + "hello\r\n"
        + "--" + BOUNDARY + "--\r\n";
    private static final String MULTIPART_TYPE = MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY;

    @Test
    void routes() throws Exception {
        assertParity(List.of(
            Exchange.get("sync", "/parity/r/sync"),
            Exchange.get("blocking executor", "/parity/r/blocking"),
            Exchange.get("async, completed stage", "/parity/r/async"),
            Exchange.get("async, continuation on a plain thread", "/parity/r/thread"),
            Exchange.get("async, task and continuation on the IO executor", "/parity/r/io")
        ));
    }

    @Test
    void filtersOnAnExecutorAndAsynchronousFilters() throws Exception {
        assertParity(List.of(
            Exchange.get("request filter, changes the context when its stage completes", "/parity/af/async-request"),
            Exchange.get("request filter on the blocking executor", "/parity/af/executor-request"),
            Exchange.get("response filter, changes the context when its stage completes", "/parity/af/async-response"),
            Exchange.get("response filter on the blocking executor", "/parity/af/executor-response")
        ));
    }

    @Test
    void bodiesSentWithTheHeaders() throws Exception {
        assertParity(bodies(Timing.TOGETHER));
    }

    @Test
    void bodiesSentAfterTheHeaders() throws Exception {
        assertParity(bodies(Timing.LATE));
    }

    @Test
    void errors() throws Exception {
        assertParity(List.of(
            Exchange.get("error route, controller local / handler error", "/parity/r/fail-local"),
            Exchange.get("error route, global / handler error", "/parity/r/fail-global"),
            Exchange.get("async error route for a failed stage", "/parity/r/fail-async"),
            Exchange.get("status route for a response status", "/parity/r/status-response"),
            Exchange.get("status route for an exception status", "/parity/r/status-exception"),
            Exchange.get("exception handler", "/parity/r/handled"),
            Exchange.get("exception handler for a failed stage", "/parity/r/handled-async"),
            Exchange.get("status route, no route (404)", "/parity/missing"),
            new Exchange("async status route, no route for the method (405)", "DELETE", "/parity/get-only", null, null, Timing.NONE)
        ));
    }

    private static List<Exchange> bodies(Timing timing) {
        String suffix = timing == Timing.LATE ? ", body sent late" : ", body sent with the headers";
        return List.of(
            new Exchange("text: @Body CompletableFuture<String> / text()" + suffix, "POST", "/parity/r/text", MediaType.TEXT_PLAIN, "hello", timing),
            new Exchange("json: @Body CompletableFuture<Map> / body(Map)" + suffix, "POST", "/parity/r/json", MediaType.APPLICATION_JSON, JSON, timing),
            new Exchange("form: @Body CompletableFuture<Map> / form()" + suffix, "POST", "/parity/r/form", MediaType.APPLICATION_FORM_URLENCODED, FORM, timing),
            new Exchange("elements: @Body Publisher<String> / elements()" + suffix, "POST", "/parity/r/elements", MediaType.APPLICATION_JSON, ELEMENTS, timing),
            new Exchange("parts: @Body MultipartBody / parts()" + suffix, "POST", "/parity/r/parts", MULTIPART_TYPE, MULTIPART, timing),
            new Exchange("bound text: @Body String / body handler" + suffix, "POST", "/parity/r/bound-text", MediaType.TEXT_PLAIN, "hello", timing),
            new Exchange("bound form: @Body Map / form handler" + suffix, "POST", "/parity/r/bound-form", MediaType.APPLICATION_FORM_URLENCODED, FORM, timing)
        );
    }

    private static void assertParity(List<Exchange> exchanges) throws Exception {
        Map<Exchange, String> controller = observe(CONTROLLER, exchanges);
        Map<Exchange, String> functional = observe(FUNCTIONAL, exchanges);
        List<Executable> assertions = new ArrayList<>();
        for (Exchange exchange : exchanges) {
            String expected = controller.get(exchange);
            String actual = functional.get(exchange);
            // the table of the observations, in the output of the test
            System.out.println("PARITY\t" + exchange.name() + "\t" + expected + "\t" + actual);
            assertions.add(() -> assertEquals(expected, actual, exchange.name()));
        }
        assertAll(assertions);
    }

    private static Map<Exchange, String> observe(String variant, List<Exchange> exchanges) throws Exception {
        Map<Exchange, String> observations = new LinkedHashMap<>();
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(VARIANT, variant))) {
            // the requests are written on a socket, to send a body after the head
            Assumptions.assumeTrue(server.getPort().isPresent(), "The server under test has a port");
            Assumptions.assumeFalse(server.getApplicationContext().getProperty("micronaut.server.ssl.enabled", Boolean.class).orElse(false), "The server under test speaks plain HTTP");
            for (Exchange exchange : exchanges) {
                observations.put(exchange, exchange.send(server));
            }
        }
        return observations;
    }

    /**
     * @return The propagated context, the MDC and the request in scope
     */
    static String describe() {
        return describeContext() + ",request=" + (ServerRequestContext.currentRequest().isPresent() ? "yes" : "no");
    }

    /**
     * @return The propagated context and the MDC in scope
     */
    static String describeContext() {
        PropagatedContext context = PropagatedContext.getOrEmpty();
        return "trace=" + context.find(Trace.class).map(Trace::id).orElse("none")
            + ",route=" + context.find(RouteTrace.class).map(RouteTrace::id).orElse("none")
            + ",mdc=" + Objects.requireNonNullElse(MDC.get("trace"), "none");
    }

    static HttpResponse<String> text(HttpStatus status, String body) {
        return HttpResponse.<String>status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    static HttpResponse<String> text(String body) {
        return text(HttpStatus.OK, body);
    }

    /**
     * A body that completes on a plain thread, after the continuation was added.
     *
     * @return The observations inside the route and in the continuation
     */
    static CompletableFuture<String> continuationOnAPlainThread() {
        String inside = describe();
        CompletableFuture<String> completed = new CompletableFuture<>();
        CompletableFuture<String> result = completed.thenApply(value -> "in=" + inside + ";cont=" + describe());
        new Thread(() -> completed.complete("done")).start();
        return result;
    }

    /**
     * @param io The IO executor, which propagates the context of the thread that submits a task
     * @return The observations of the task and of its continuation
     */
    static CompletableFuture<String> continuationOnTheIoExecutor(ExecutorService io) {
        return CompletableFuture.supplyAsync(PropagatedContextParityTest::describe, io)
            .thenApply(task -> "task=" + task + ";cont=" + describe());
    }

    static <T> CompletableFuture<T> failOnTheIoExecutor(ExecutorService io, RuntimeException failure) {
        return CompletableFuture.supplyAsync(() -> {
            throw failure;
        }, io);
    }

    private static String describeResponse(byte[] response) {
        String text = new String(response, StandardCharsets.UTF_8);
        int end = text.indexOf("\r\n\r\n");
        String[] lines = text.substring(0, end).split("\r\n");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        String body = text.substring(end + 4);
        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            body = dechunk(body);
        }
        String status = lines[0].split(" ")[1];
        return status + " " + body
            + " | before=" + headers.getOrDefault("x-before", "-")
            + " | after=" + headers.getOrDefault("x-after", "-")
            + " | after2=" + headers.getOrDefault("x-after2", "-")
            + " | outer=" + headers.getOrDefault("x-outer", "-");
    }

    private static String dechunk(String chunked) {
        StringBuilder body = new StringBuilder();
        int position = 0;
        while (true) {
            int lineEnd = chunked.indexOf("\r\n", position);
            int size = Integer.parseInt(chunked.substring(position, lineEnd).trim(), 16);
            if (size == 0) {
                return body.toString();
            }
            body.append(chunked, lineEnd + 2, lineEnd + 2 + size);
            position = lineEnd + 2 + size + 2;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    enum Timing {
        /**
         * No body.
         */
        NONE,
        /**
         * The body is written with the head of the request.
         */
        TOGETHER,
        /**
         * The body is written once the route runs, or a moment after the head if the route waits
         * for the body.
         */
        LATE
    }

    /**
     * A request, sent on a socket so that the body can be sent after the head.
     *
     * @param name        The scenario
     * @param method      The method
     * @param path        The path
     * @param contentType The content type
     * @param body        The body
     * @param timing      When the body is sent
     */
    record Exchange(String name, String method, String path, @Nullable String contentType, @Nullable String body, Timing timing) {
        static Exchange get(String name, String path) {
            return new Exchange(name, "GET", path, null, null, Timing.NONE);
        }

        /**
         * @param server The server
         * @return The observation: the status, the body and the headers of the filters
         */
        String send(ServerUnderTest server) throws IOException, InterruptedException {
            Reading reading = server.getApplicationContext().getBean(Reading.class);
            CountDownLatch routeRuns = reading.arm();
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder head = new StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: localhost\r\n")
                .append(TRACE).append(": t1\r\n")
                .append("Connection: close\r\n");
            if (contentType != null) {
                head.append("Content-Type: ").append(contentType).append("\r\n");
            }
            if (body != null) {
                head.append("Content-Length: ").append(bytes.length).append("\r\n");
            }
            head.append("\r\n");
            try (Socket socket = new Socket("localhost", server.getPort().orElseThrow())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                byte[] headBytes = head.toString().getBytes(StandardCharsets.US_ASCII);
                if (timing == Timing.LATE) {
                    out.write(headBytes);
                    out.flush();
                    // a route that waits for the body is not called before it arrives
                    routeRuns.await(1, TimeUnit.SECONDS);
                    out.write(bytes);
                } else {
                    ByteArrayOutputStream whole = new ByteArrayOutputStream();
                    whole.write(headBytes);
                    whole.write(bytes);
                    out.write(whole.toByteArray());
                }
                out.flush();
                return describeResponse(readAll(socket.getInputStream()));
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * The element the annotation filter adds.
     *
     * @param id The trace
     */
    record Trace(String id) implements PropagatedContextElement {
    }

    /**
     * The element the filters of the route add.
     *
     * @param id The trace
     */
    record RouteTrace(String id) implements PropagatedContextElement {
    }

    static final class LocalFailure extends RuntimeException {
        LocalFailure() {
            super("local failure");
        }
    }

    static final class GlobalFailure extends RuntimeException {
        GlobalFailure() {
            super("global failure");
        }
    }

    static final class AsyncFailure extends RuntimeException {
        AsyncFailure() {
            super("async failure");
        }
    }

    static final class HandledFailure extends RuntimeException {
        HandledFailure() {
            super("handled failure");
        }
    }

    /**
     * Signals that the route runs, so that a late body is sent once it does.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Reading {
        private volatile CountDownLatch latch = new CountDownLatch(1);

        CountDownLatch arm() {
            CountDownLatch armed = new CountDownLatch(1);
            latch = armed;
            return armed;
        }

        void reading() {
            latch.countDown();
        }
    }

    /**
     * Like the MDC filter of the documentation: adds the trace of the request to the propagated
     * context and to the MDC, for the controllers and the handler routes.
     */
    @ServerFilter("/parity/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = request.getHeaders().get(TRACE);
            if (trace != null) {
                propagatedContext.add(new Trace(trace));
                propagatedContext.add(new MdcPropagationContext(Map.of("trace", trace)));
            }
        }

        @ResponseFilter
        void observe(MutableHttpResponse<?> response) {
            response.header("X-Outer", describe());
        }
    }

    /**
     * Handles {@link HandledFailure} for the controllers and the handler routes.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class HandledFailureHandler implements ExceptionHandler<HandledFailure, HttpResponse<?>> {
        @Override
        public HttpResponse<?> handle(HttpRequest request, HandledFailure exception) {
            return text(HttpStatus.I_AM_A_TEAPOT, "handler:" + describe());
        }
    }

    static final class RouteFilters {
        private RouteFilters() {
        }

        static void before(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            request.setAttribute("before", describe());
            String trace = request.getHeaders().get(TRACE);
            propagatedContext.add(new RouteTrace("r-" + trace));
            propagatedContext.add(new MdcPropagationContext(Map.of("trace", "r-" + trace)));
        }

        static void after(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            response.header("X-Before", request.getAttribute("before", String.class).orElse("missing"));
            response.header("X-After", describe());
            propagatedContext.add(new RouteTrace("resp-" + request.getHeaders().get(TRACE)));
        }

        static CompletableFuture<@Nullable HttpResponse<?>> beforeAsync(ExecutorService io, HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            request.setAttribute("before", describe());
            return CompletableFuture.supplyAsync(() -> request.getHeaders().get(TRACE), io).thenApply(trace -> {
                // added once the filter looked the trace up, on the IO executor
                propagatedContext.add(new RouteTrace("async-" + trace));
                return null;
            });
        }

        static void beforeOnExecutor(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            request.setAttribute("before", describe());
            propagatedContext.add(new RouteTrace("blocking-" + request.getHeaders().get(TRACE)));
        }

        static CompletableFuture<Void> afterAsync(ExecutorService io, HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            response.header("X-After", describe());
            return CompletableFuture.runAsync(() -> propagatedContext.add(new RouteTrace("async-resp-" + request.getHeaders().get(TRACE))), io);
        }

        static void afterOnExecutor(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            response.header("X-After", describe());
            propagatedContext.add(new RouteTrace("blocking-resp-" + request.getHeaders().get(TRACE)));
        }

        static void after2(HttpRequest<?> request, MutableHttpResponse<?> response) {
            if (!response.getHeaders().contains("X-Before")) {
                request.getAttribute("before", String.class).ifPresent(before -> response.header("X-Before", before));
            }
            response.header("X-After2", describe());
        }
    }

    // ------------------------------------------------------------------ controllers

    /**
     * The filters of the routes under {@code /parity/r} for the controllers: after the trace
     * filter, like the route filters of a handler route.
     */
    @ServerFilter("/parity/r/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(100)
    static class ControllerRouteRequestFilter {
        @RequestFilter
        void before(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            RouteFilters.before(request, propagatedContext);
        }
    }

    /**
     * Runs first of the response filters of the route: the higher order.
     */
    @ServerFilter("/parity/r/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(300)
    static class ControllerRouteFirstResponseFilter {
        @ResponseFilter
        void after(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            RouteFilters.after(request, response, propagatedContext);
        }
    }

    @ServerFilter("/parity/r/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(200)
    static class ControllerRouteSecondResponseFilter {
        @ResponseFilter
        void after(HttpRequest<?> request, MutableHttpResponse<?> response) {
            RouteFilters.after2(request, response);
        }
    }

    @ServerFilter("/parity/af/async-request")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(100)
    static class ControllerAsyncRequestFilter {
        private final ExecutorService io;

        ControllerAsyncRequestFilter(@Named(TaskExecutors.IO) ExecutorService io) {
            this.io = io;
        }

        @RequestFilter
        CompletableFuture<@Nullable HttpResponse<?>> before(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            return RouteFilters.beforeAsync(io, request, propagatedContext);
        }
    }

    @ServerFilter("/parity/af/executor-request")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(100)
    static class ControllerExecutorRequestFilter {
        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        void before(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            RouteFilters.beforeOnExecutor(request, propagatedContext);
        }
    }

    @ServerFilter("/parity/af/async-response")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(300)
    static class ControllerAsyncResponseFilter {
        private final ExecutorService io;

        ControllerAsyncResponseFilter(@Named(TaskExecutors.IO) ExecutorService io) {
            this.io = io;
        }

        @ResponseFilter
        CompletableFuture<@Nullable MutableHttpResponse<?>> after(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            return RouteFilters.afterAsync(io, request, response, propagatedContext).thenApply(ignored -> null);
        }
    }

    @ServerFilter("/parity/af/executor-response")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(300)
    static class ControllerExecutorResponseFilter {
        @ResponseFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        void after(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            RouteFilters.afterOnExecutor(request, response, propagatedContext);
        }
    }

    @ServerFilter("/parity/af/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    @Order(200)
    static class ControllerAsyncSecondResponseFilter {
        @ResponseFilter
        void after(HttpRequest<?> request, MutableHttpResponse<?> response) {
            RouteFilters.after2(request, response);
        }
    }

    @Controller("/parity/af")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    static class ParityFilterController {
        @Get(value = "/{name}", produces = MediaType.TEXT_PLAIN)
        String describeRoute(String name) {
            return describe();
        }
    }

    @Controller("/parity/r")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    static class ParityController {
        private final ExecutorService io;
        private final Reading reading;

        ParityController(@Named(TaskExecutors.IO) ExecutorService io, Reading reading) {
            this.io = io;
            this.reading = reading;
        }

        @Get(value = "/sync", produces = MediaType.TEXT_PLAIN)
        String sync() {
            return describe();
        }

        @Get(value = "/blocking", produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String blocking() {
            return describe();
        }

        @Get(value = "/async", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> async() {
            return CompletableFuture.completedFuture(describe());
        }

        @Get(value = "/thread", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> thread() {
            return continuationOnAPlainThread();
        }

        @Get(value = "/io", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> io() {
            return continuationOnTheIoExecutor(io);
        }

        @Post(value = "/text", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        CompletableFuture<String> textFuture(@Body CompletableFuture<String> body) {
            reading.reading();
            return body.thenApply(value -> value + ":" + describe());
        }

        @Post(value = "/json", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_JSON)
        CompletableFuture<String> json(@Body CompletableFuture<Map<String, String>> body) {
            reading.reading();
            return body.thenApply(value -> value.get("name") + ":" + describe());
        }

        @Post(value = "/form", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        CompletableFuture<String> form(@Body CompletableFuture<Map<String, String>> body) {
            reading.reading();
            return body.thenApply(value -> value.get("name") + ":" + describe());
        }

        @Post(value = "/elements", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_JSON)
        Mono<String> elements(@Body Publisher<String> body) {
            reading.reading();
            AtomicReference<String> first = new AtomicReference<>();
            return Flux.from(body)
                .doOnNext(element -> first.compareAndSet(null, describe()))
                .then(Mono.fromSupplier(() -> "elem=" + first.get() + ";done=" + describe()));
        }

        @Post(value = "/parts", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Mono<String> parts(@Body MultipartBody body) {
            reading.reading();
            AtomicReference<String> first = new AtomicReference<>();
            return Flux.from(body)
                .doOnNext(part -> first.compareAndSet(null, describe()))
                .then(Mono.fromSupplier(() -> "elem=" + first.get() + ";done=" + describe()));
        }

        @Post(value = "/bound-text", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        String boundText(@Body String body) {
            reading.reading();
            return body + ":" + describe();
        }

        @Post(value = "/bound-form", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        String boundForm(@Body Map<String, String> body) {
            reading.reading();
            return body.get("name") + ":" + describe();
        }

        @Get("/fail-local")
        String failLocal() {
            throw new LocalFailure();
        }

        @Get("/fail-global")
        String failGlobal() {
            throw new GlobalFailure();
        }

        @Get("/fail-async")
        CompletableFuture<String> failAsync() {
            return failOnTheIoExecutor(io, new AsyncFailure());
        }

        @Get("/status-response")
        HttpResponse<String> statusResponse() {
            return text(HttpStatus.CONFLICT, "conflict");
        }

        @Get("/status-exception")
        String statusException() {
            throw new HttpStatusException(HttpStatus.GONE, "gone");
        }

        @Get("/handled")
        String handled() {
            throw new HandledFailure();
        }

        @Get("/handled-async")
        CompletableFuture<String> handledAsync() {
            return failOnTheIoExecutor(io, new HandledFailure());
        }

        @Error(LocalFailure.class)
        HttpResponse<String> local(HttpRequest<?> request, LocalFailure failure) {
            return text(HttpStatus.I_AM_A_TEAPOT, "error:" + describe());
        }
    }

    @Controller("/parity")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = CONTROLLER)
    static class ParityErrorController {
        @Get(value = "/get-only", produces = MediaType.TEXT_PLAIN)
        String getOnly() {
            return describe();
        }

        @Error(global = true, exception = GlobalFailure.class)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> global(HttpRequest<?> request, GlobalFailure failure) {
            return text(HttpStatus.I_AM_A_TEAPOT, "error:" + describe());
        }

        @Error(global = true, exception = AsyncFailure.class)
        @Produces(MediaType.TEXT_PLAIN)
        CompletableFuture<HttpResponse<String>> async(HttpRequest<?> request, AsyncFailure failure) {
            return CompletableFuture.completedFuture(text(HttpStatus.I_AM_A_TEAPOT, "async error:" + describe()));
        }

        @Error(global = true, status = HttpStatus.CONFLICT)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> conflict(HttpRequest<?> request) {
            return text(HttpStatus.CONFLICT, "status:" + describe());
        }

        @Error(global = true, status = HttpStatus.GONE)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> gone(HttpRequest<?> request) {
            return text(HttpStatus.GONE, "status:" + describe());
        }

        @Error(global = true, status = HttpStatus.NOT_FOUND)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> notFound(HttpRequest<?> request) {
            return text(HttpStatus.NOT_FOUND, "status:" + describe());
        }

        @Error(global = true, status = HttpStatus.METHOD_NOT_ALLOWED)
        @Produces(MediaType.TEXT_PLAIN)
        CompletableFuture<HttpResponse<String>> notAllowed(HttpRequest<?> request) {
            return CompletableFuture.completedFuture(text(HttpStatus.METHOD_NOT_ALLOWED, "async status:" + describe()));
        }
    }

    // ------------------------------------------------------------------ handler routes

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = VARIANT, value = FUNCTIONAL)
    static class ParityRoutes implements HttpRoutes {
        private final ExecutorService io;
        private final Reading reading;

        ParityRoutes(@Named(TaskExecutors.IO) ExecutorService io, Reading reading) {
            this.io = io;
            this.reading = reading;
        }

        /**
         * The route filters, the filters of the controllers.
         */
        private static HttpRouteSpec filtered(HttpRouteSpec route) {
            return route
                .before((request, propagatedContext) -> {
                    RouteFilters.before(request, propagatedContext);
                    return null;
                })
                .after(RouteFilters::after)
                .after((request, response) -> RouteFilters.after2(request, response));
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            filtered(routes.GET("/parity/r/sync", (request, pathVariables) -> text(describe())));
            filtered(routes.GET("/parity/r/blocking", (request, pathVariables) -> text(describe()))
                .executeOn(TaskExecutors.BLOCKING));
            filtered(routes.asyncGET("/parity/r/async", (request, pathVariables) -> CompletableFuture.completedFuture(text(describe()))));
            filtered(routes.asyncGET("/parity/r/thread", (request, pathVariables) -> continuationOnAPlainThread().thenApply(PropagatedContextParityTest::text)));
            filtered(routes.asyncGET("/parity/r/io", (request, pathVariables) -> continuationOnTheIoExecutor(io).thenApply(PropagatedContextParityTest::text)));

            filtered(routes.asyncPOST("/parity/r/text", (request, pathVariables) -> {
                reading.reading();
                return request.text().thenApply(value -> text(value + ":" + describe()));
            }).consumes(MediaType.TEXT_PLAIN_TYPE));
            filtered(routes.asyncPOST("/parity/r/json", (request, pathVariables) -> {
                reading.reading();
                return request.body(Argument.mapOf(String.class, String.class)).thenApply(value -> text(value.get("name") + ":" + describe()));
            }).consumes(MediaType.APPLICATION_JSON_TYPE));
            filtered(routes.asyncPOST("/parity/r/form", (request, pathVariables) -> {
                reading.reading();
                return request.form().thenApply(form -> text(form.find("name", String.class).orElse("none") + ":" + describe()));
            }).consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            filtered(routes.asyncPOST("/parity/r/elements", (request, pathVariables) -> {
                reading.reading();
                AtomicReference<String> first = new AtomicReference<>();
                return request.elements(String.class).forEach(element -> {
                    first.compareAndSet(null, describe());
                    return CompletableFuture.completedFuture(null);
                }).thenApply(ignored -> text("elem=" + first.get() + ";done=" + describe()));
            }).consumes(MediaType.APPLICATION_JSON_TYPE));
            filtered(routes.asyncPOST("/parity/r/parts", (request, pathVariables) -> {
                reading.reading();
                AtomicReference<String> first = new AtomicReference<>();
                return request.parts().forEach(part -> {
                    first.compareAndSet(null, describe());
                    return part.text();
                }).thenApply(ignored -> text("elem=" + first.get() + ";done=" + describe()));
            }).consumes(MediaType.MULTIPART_FORM_DATA_TYPE));
            filtered(routes.POST("/parity/r/bound-text", Argument.of(String.class), (request, pathVariables, body) -> {
                reading.reading();
                return text(body + ":" + describe());
            }).consumes(MediaType.TEXT_PLAIN_TYPE));
            filtered(routes.POST("/parity/r/bound-form", (request, pathVariables, form) -> {
                reading.reading();
                return text(form.find("name", String.class).orElse("none") + ":" + describe());
            }).consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE));

            filtered(routes.GET("/parity/r/fail-local", (request, pathVariables) -> {
                throw new LocalFailure();
            }));
            filtered(routes.GET("/parity/r/fail-global", (request, pathVariables) -> {
                throw new GlobalFailure();
            }));
            filtered(routes.asyncGET("/parity/r/fail-async", (request, pathVariables) -> failOnTheIoExecutor(io, new AsyncFailure())));
            filtered(routes.GET("/parity/r/status-response", (request, pathVariables) -> text(HttpStatus.CONFLICT, "conflict")));
            filtered(routes.GET("/parity/r/status-exception", (request, pathVariables) -> {
                throw new HttpStatusException(HttpStatus.GONE, "gone");
            }));
            filtered(routes.GET("/parity/r/handled", (request, pathVariables) -> {
                throw new HandledFailure();
            }));
            filtered(routes.asyncGET("/parity/r/handled-async", (request, pathVariables) -> failOnTheIoExecutor(io, new HandledFailure())));

            routes.GET("/parity/af/async-request", (request, pathVariables) -> text(describe()))
                .beforeAsync((request, propagatedContext) -> RouteFilters.beforeAsync(io, request, propagatedContext))
                .after((request, response) -> RouteFilters.after2(request, response));
            routes.GET("/parity/af/executor-request", (request, pathVariables) -> text(describe()))
                .before(TaskExecutors.BLOCKING, (request, propagatedContext) -> {
                    RouteFilters.beforeOnExecutor(request, propagatedContext);
                    return null;
                })
                .after((request, response) -> RouteFilters.after2(request, response));
            routes.GET("/parity/af/async-response", (request, pathVariables) -> text(describe()))
                .afterAsync((request, response, propagatedContext) -> RouteFilters.afterAsync(io, request, response, propagatedContext))
                .after((request, response) -> RouteFilters.after2(request, response));
            routes.GET("/parity/af/executor-response", (request, pathVariables) -> text(describe()))
                .after(TaskExecutors.BLOCKING, RouteFilters::afterOnExecutor)
                .after((request, response) -> RouteFilters.after2(request, response));

            routes.GET("/parity/get-only", (request, pathVariables) -> text(describe()));

            routes.error(LocalFailure.class, (request, error) -> text(HttpStatus.I_AM_A_TEAPOT, "error:" + describe()));
            routes.error(GlobalFailure.class, (request, error) -> text(HttpStatus.I_AM_A_TEAPOT, "error:" + describe()));
            routes.errorAsync(AsyncFailure.class, (request, error) ->
                CompletableFuture.completedFuture(text(HttpStatus.I_AM_A_TEAPOT, "async error:" + describe())));
            routes.status(HttpStatus.CONFLICT, request -> text(HttpStatus.CONFLICT, "status:" + describe()));
            routes.status(HttpStatus.GONE, request -> text(HttpStatus.GONE, "status:" + describe()));
            routes.status(HttpStatus.NOT_FOUND, request -> text(HttpStatus.NOT_FOUND, "status:" + describe()));
            routes.statusAsync(HttpStatus.METHOD_NOT_ALLOWED, request ->
                CompletableFuture.completedFuture(text(HttpStatus.METHOD_NOT_ALLOWED, "async status:" + describe())));
        }
    }
}
