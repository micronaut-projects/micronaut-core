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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestTimeout;
import io.micronaut.http.server.RequestDeadline;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request timeout of a route, see {@link RequestTimeout}: a route that does not produce its
 * response in time is cancelled and answered with a 503.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestTimeoutTest {
    public static final String SPEC_NAME = "RequestTimeoutTest";
    static volatile CountDownLatch reactiveCancelled = new CountDownLatch(1);
    /**
     * Counts down when a slow route that timed out finished anyway: the test waits for it, so the
     * route does not run on after the server of the test closed.
     */
    static volatile CountDownLatch slowFinished = new CountDownLatch(1);
    static volatile CountDownLatch offloadedCancelled = new CountDownLatch(1);
    static volatile CountDownLatch lateFileClosed = new CountDownLatch(1);
    static volatile CountDownLatch cooperativeStop = new CountDownLatch(1);
    private static final long SLOW_MILLIS = 1500;

    private static HttpResponseAssertion unavailable() {
        return HttpResponseAssertion.builder().status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private static HttpResponseAssertion ok(String body) {
        return HttpResponseAssertion.builder().status(HttpStatus.OK).body(body).build();
    }

    @Test
    void blockingRouteTimesOut() throws IOException {
        slowFinished = new CountDownLatch(1);
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/blocking"),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, unavailable());
                awaitSlowRoute();
            });
    }

    @Test
    void aRouteSeesTheReactorContextOfAReactiveFilter() throws IOException {
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/context"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("acme")));
    }

    @Test
    void anOffloadedReactiveRouteIsCancelled() throws IOException {
        offloadedCancelled = new CountDownLatch(1);
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/offloaded-reactive"),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, unavailable());
                await(offloadedCancelled, "the subscription of the offloaded route is cancelled");
            });
    }

    @Test
    void theExpiryIsAnsweredWhileTheBlockingPoolIsBusy() throws IOException {
        slowFinished = new CountDownLatch(1);
        // the only blocking thread is held by the route that times out
        Map<String, Object> configuration = Map.of(
            "micronaut.executors.blocking.type", "fixed",
            "micronaut.executors.blocking.number-of-threads", 1);
        asserts(SPEC_NAME, configuration, HttpRequest.GET("/timeout/blocking"),
            (server, request) -> {
                long start = System.nanoTime();
                AssertionUtils.assertThrows(server, request, unavailable());
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                assertTrue(elapsedMillis < SLOW_MILLIS - 300, "the 503 waited for the route: " + elapsedMillis + "ms");
                awaitSlowRoute();
            });
    }

    @Test
    void aLateFileResponseIsClosed() throws IOException {
        lateFileClosed = new CountDownLatch(1);
        slowFinished = new CountDownLatch(1);
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/late-file"),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, unavailable());
                awaitSlowRoute();
                await(lateFileClosed, "the stream of the late file is closed");
            });
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void awaitSlowRoute() {
        try {
            assertTrue(slowFinished.await(10, TimeUnit.SECONDS), "the slow route finished");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void reactiveRouteTimesOutAndIsCancelled() throws IOException {
        reactiveCancelled = new CountDownLatch(1);
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/reactive"),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, unavailable());
                try {
                    assertTrue(reactiveCancelled.await(5, TimeUnit.SECONDS), "the subscription is cancelled");
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            });
    }

    @Test
    void completableFutureRouteTimesOut() throws IOException {
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/future"),
            (server, request) -> AssertionUtils.assertThrows(server, request, unavailable()));
    }

    @Test
    void aStreamedResponseIsLimitedUntilItsFirstByteOnly() throws IOException {
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/stream"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("abcd")));
    }

    @Test
    void theMethodTimeoutOverridesTheClassTimeout() throws IOException {
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/class/fast"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("fast")));
        slowFinished = new CountDownLatch(1);
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/class/slow"),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, unavailable());
                awaitSlowRoute();
            });
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/class/disabled"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("disabled")));
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/class/longer"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("longer")));
    }

    @Test
    void aFunctionalRouteTimesOut() throws IOException {
        asserts(SPEC_NAME, HttpRequest.GET("/timeout-fn/slow"),
            (server, request) -> AssertionUtils.assertThrows(server, request, unavailable()));
        asserts(SPEC_NAME, HttpRequest.GET("/timeout-fn/fast"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("fast")));
    }

    @Test
    void theHandlerSeesItsDeadlineExpireAndStops() throws IOException {
        cooperativeStop = new CountDownLatch(1);
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/cooperate"),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, unavailable());
                await(cooperativeStop, "the handler saw the expiry and stopped");
            });
    }

    @Test
    void withoutATimeoutASlowRouteAnswers() throws IOException {
        asserts(SPEC_NAME, HttpRequest.GET("/timeout/none"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("none")));
    }

    @Test
    void theServerDefaultAppliesToRoutesWithoutTheirOwn() throws IOException {
        Map<String, Object> configuration = Map.of("micronaut.server.request-timeout", "300ms");
        asserts(SPEC_NAME, configuration, HttpRequest.GET("/timeout/none"),
            (server, request) -> AssertionUtils.assertThrows(server, request, unavailable()));
        // a route that disables its timeout is not limited by the default either
        asserts(SPEC_NAME, configuration, HttpRequest.GET("/timeout/class/disabled"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ok("disabled")));
    }

    @Test
    void theTimeoutCoversReadingTheBody() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            // the body is announced but never arrives
            out.write(("POST /timeout/body HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\n"
                + "Content-Length: 100\r\n\r\npartial").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] head = new byte[12];
            int read = 0;
            while (read < head.length) {
                int n = in.read(head, read, head.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            String statusLine = new String(head, 0, read, StandardCharsets.US_ASCII);
            assertTrue(statusLine.startsWith("HTTP/1.1 503"), statusLine);
        }
    }

    private static Socket connect(ServerUnderTest server) throws IOException {
        int port = server.getPort().orElseThrow();
        Socket socket;
        if (server.getApplicationContext().getProperty("micronaut.server.ssl.enabled", Boolean.class).orElse(false)) {
            socket = trustAll().getSocketFactory().createSocket("localhost", port);
        } else {
            socket = new Socket("localhost", port);
        }
        socket.setSoTimeout(20_000);
        return socket;
    }

    private static SSLContext trustAll() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // the self-signed certificate of the server under test
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } }, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Controller("/timeout")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TimeoutController {

        @Get(value = "/blocking", produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        @RequestTimeout("300ms")
        String blocking() {
            sleep(SLOW_MILLIS);
            slowFinished.countDown();
            return "late";
        }

        @Get(value = "/reactive", produces = MediaType.TEXT_PLAIN)
        @RequestTimeout("300ms")
        Mono<String> reactive() {
            return Mono.<String>never().doOnCancel(() -> reactiveCancelled.countDown());
        }

        @Get(value = "/future", produces = MediaType.TEXT_PLAIN)
        @RequestTimeout("300ms")
        CompletableFuture<String> future() {
            return new CompletableFuture<>();
        }

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        @RequestTimeout("300ms")
        Publisher<String> stream() {
            // the items are emitted by a thread of the request rather than by a shared Reactor
            // scheduler, whose threads would outlive the test
            return Flux.create(sink -> new Thread(() -> {
                for (String item : new String[] {"a", "b", "c", "d"}) {
                    sleep(150);
                    sink.next(item);
                }
                sink.complete();
            }, "request-timeout-stream").start());
        }

        @Get(value = "/cooperate", produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        @RequestTimeout("300ms")
        String cooperate(HttpRequest<?> request) {
            RequestDeadline deadline = RequestDeadline.find(request).orElseThrow();
            if (deadline.remaining().isEmpty() || deadline.getTimeout().toMillis() != 300) {
                throw new IllegalStateException("the deadline of the route is not running");
            }
            // work in steps, and stop once the request was answered with the timeout
            for (int step = 0; step < 200; step++) {
                if (deadline.isExpired()) {
                    cooperativeStop.countDown();
                    return "stopped";
                }
                sleep(25);
            }
            return "finished";
        }

        @Get(value = "/context", produces = MediaType.TEXT_PLAIN)
        @RequestTimeout("5s")
        Mono<String> context() {
            return Mono.deferContextual(context -> Mono.just(context.getOrDefault(TenantFilter.KEY, "missing")));
        }

        @Get(value = "/offloaded-reactive", produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        @RequestTimeout("300ms")
        Mono<String> offloadedReactive() {
            return Mono.<String>never().doOnCancel(() -> offloadedCancelled.countDown());
        }

        @Get(value = "/late-file", produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        @RequestTimeout("300ms")
        StreamedFile lateFile() {
            sleep(600);
            InputStream stream = new ByteArrayInputStream("late".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    lateFileClosed.countDown();
                    super.close();
                }
            };
            slowFinished.countDown();
            return new StreamedFile(stream, MediaType.TEXT_PLAIN_TYPE);
        }

        @Get(value = "/none", produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String none() {
            sleep(600);
            return "none";
        }

        @Post(value = "/body", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        @RequestTimeout("300ms")
        String body(@Body String body) {
            return body;
        }
    }

    @Controller("/timeout/class")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @RequestTimeout("300ms")
    @Produces(MediaType.TEXT_PLAIN)
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class ClassTimeoutController {

        @Get("/fast")
        String fast() {
            return "fast";
        }

        @Get("/slow")
        String slow() {
            sleep(SLOW_MILLIS);
            slowFinished.countDown();
            return "slow";
        }

        @Get("/disabled")
        @RequestTimeout("0")
        String disabled() {
            sleep(600);
            return "disabled";
        }

        @Get("/longer")
        @RequestTimeout("5s")
        String longer() {
            sleep(600);
            return "longer";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TimeoutRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/timeout-fn", group -> {
                group.requestTimeout(Duration.ofMillis(300));
                group.GET("/slow").handleAsync((request, pathVariables) -> new CompletableFuture<HttpResponse<?>>());
                group.GET("/fast").handle((request, pathVariables) -> HttpResponse.ok("fast").contentType(MediaType.TEXT_PLAIN_TYPE));
            });
        }
    }
    /**
     * A reactive filter that writes a value into the Reactor context of the route.
     */
    @Filter("/timeout/context")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TenantFilter implements HttpServerFilter {
        static final String KEY = "request-timeout-tenant";

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request)).contextWrite(context -> context.put(KEY, "acme"));
        }
    }
}
