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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.direct.DirectRouteBuilder;
import io.micronaut.web.router.direct.HttpDirectRoutes;
import io.netty.util.concurrent.FastThreadLocalThread;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.http.server.netty.DirectRouteTest.readResponse;
import static io.micronaut.http.server.netty.DirectRouteTest.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The asynchronous direct routes on the Netty server: a route on an executor, which is matched on
 * the event loop and runs on the executor, and a route whose function completes its response
 * later. The server holds the request until the response is complete, keeps the order of the
 * responses of a connection, hands a declined request to the ordinary routes with its body,
 * cancels the stage when the connection closes and answers a failure with {@code 500}. A
 * blocking message body writer is refused on the event loop, and runs off it for an
 * asynchronous route.
 */
class DirectRouteAsyncTest {
    private static final String SPEC_NAME = "DirectRouteAsyncTest";
    private static final MediaType BLOCKING = MediaType.of("application/x-blocking");
    private static final Executor LATER = CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS);

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;
    private static Threads threads;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.idle-timeout", "5s"));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
        threads = ctx.getBean(Threads.class);
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aRouteOnAnExecutorIsMatchedOnTheEventLoopAndRunsOnTheExecutor() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/async/report/42"), String.class);
        assertEquals("report 42", response.body());
        // matched on the event loop, never blocked there
        assertInstanceOf(FastThreadLocalThread.class, threads.constraint.get());
        Thread blocking = threads.function.get();
        assertFalse(blocking instanceof FastThreadLocalThread, blocking.getName());
        // a direct response gets no header of the server
        assertNull(response.getHeaders().get("Date"));
        assertEquals("9", response.getHeaders().get("Content-Length"));
        assertEquals(0, threads.filtered.get());
    }

    @Test
    void anAsynchronousRouteCompletesOnAnyThread() {
        assertEquals("later", client.toBlocking().retrieve(HttpRequest.GET("/async/later")));
        // the implicit HEAD route
        HttpResponse<String> head = client.toBlocking().exchange(HttpRequest.HEAD("/async/later"), String.class);
        assertEquals(HttpStatus.OK, head.getStatus());
        assertEquals("5", head.getHeaders().get("Content-Length"));
        assertEquals(0, threads.filtered.get());
    }

    @Test
    void aDeclinedRequestContinuesToTheOrdinaryRouteWithItsBody() {
        String body = "x".repeat(100_000);
        assertEquals("stored 100000 chars", client.toBlocking().retrieve(
            HttpRequest.POST("/async/upload", body).contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals("stored 100000 chars", client.toBlocking().retrieve(
            HttpRequest.POST("/async/upload-on-executor", body).contentType(MediaType.TEXT_PLAIN_TYPE)));
        // the ordinary routes run their filters
        assertTrue(threads.filtered.get() >= 2);
        threads.filtered.set(0);
    }

    @Test
    void aFailedStageAndAThrowingFunctionAreAnsweredWith500() {
        for (String path : new String[] {"/async/failed", "/async/throwing", "/async/throwing-on-executor"}) {
            HttpClientResponseException error = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(HttpRequest.GET(path)));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus(), path);
        }
        // the connection is still usable
        assertEquals("later", client.toBlocking().retrieve(HttpRequest.GET("/async/later")));
    }

    @Test
    void aBlockingWriterIsRefusedOnASynchronousRouteAndRunsOffTheEventLoopOtherwise() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/async/blocking-writer/sync")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, refused.getStatus());

        threads.writer.set(null);
        assertEquals("blocking executor", client.toBlocking().retrieve(HttpRequest.GET("/async/blocking-writer/executor")));
        Thread writer = threads.writer.get();
        assertNotNull(writer);
        assertFalse(writer instanceof FastThreadLocalThread, writer.getName());

        // completed on the event loop: the writer runs on the IO executor
        threads.writer.set(null);
        assertEquals("blocking completed", client.toBlocking().retrieve(HttpRequest.GET("/async/blocking-writer/completed")));
        writer = threads.writer.get();
        assertNotNull(writer);
        assertFalse(writer instanceof FastThreadLocalThread, writer.getName());
    }

    @Test
    void pipelinedResponsesKeepTheOrderOfTheRequests() throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            // the asynchronous answer completes after the synchronous ones that follow it
            out.write(concat(request("GET", "/async/later"), request("GET", "/async/now"),
                request("GET", "/async/report/1"), request("GET", "/async/now")));
            assertTrue(readResponse(in, false).endsWith("later"));
            assertTrue(readResponse(in, false).endsWith("now"));
            assertTrue(readResponse(in, false).endsWith("report 1"));
            assertTrue(readResponse(in, false).endsWith("now"));
        }
    }

    @Test
    void theStageIsCancelledWhenTheConnectionCloses() throws Exception {
        CountDownLatch called = new CountDownLatch(1);
        threads.hangingCalled.set(called);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.getOutputStream().write(request("GET", "/async/hanging"));
            socket.getOutputStream().flush();
            assertTrue(called.await(5, TimeUnit.SECONDS));
        }
        CompletableFuture<HttpResponse<?>> stage = threads.hanging.get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!stage.isCancelled() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(stage.isCancelled());
    }

    private static byte[] concat(byte[]... requests) {
        int length = 0;
        for (byte[] request : requests) {
            length += request.length;
        }
        byte[] all = new byte[length];
        int offset = 0;
        for (byte[] request : requests) {
            System.arraycopy(request, 0, all, offset, request.length);
            offset += request.length;
        }
        return all;
    }

    /**
     * A body written by a blocking writer.
     *
     * @param text The text
     */
    record BlockingBody(String text) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Threads {
        final AtomicReference<Thread> constraint = new AtomicReference<>();
        final AtomicReference<Thread> function = new AtomicReference<>();
        final AtomicReference<Thread> writer = new AtomicReference<>();
        final AtomicReference<CountDownLatch> hangingCalled = new AtomicReference<>(new CountDownLatch(1));
        final AtomicReference<CompletableFuture<HttpResponse<?>>> hanging = new AtomicReference<>();
        final AtomicInteger filtered = new AtomicInteger();
    }

    @Singleton
    @Produces("application/x-blocking")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BlockingWriter implements MessageBodyWriter<BlockingBody> {
        private final Threads threads;

        BlockingWriter(Threads threads) {
            this.threads = threads;
        }

        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public void writeTo(Argument<BlockingBody> type, MediaType mediaType, BlockingBody object, MutableHeaders outgoingHeaders,
                            OutputStream outputStream) throws CodecException {
            threads.writer.set(Thread.currentThread());
            try {
                outputStream.write(("blocking " + object.text()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Cannot write", e);
            }
        }
    }

    @ServerFilter("/async/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CountingFilter {
        private final Threads threads;

        CountingFilter(Threads threads) {
            this.threads = threads;
        }

        @RequestFilter
        void count() {
            threads.filtered.incrementAndGet();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes, HttpRoutes {
        private final Threads threads;

        Routes(Threads threads) {
            this.threads = threads;
        }

        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.path("/async", async -> {
                async.GET("/report/{id}").constrain(variables -> {
                    threads.constraint.set(Thread.currentThread());
                    return true;
                }).executeOn(TaskExecutors.BLOCKING)
                .respond(direct -> {
                    threads.function.set(Thread.currentThread());
                    return direct.responses().ok("report " + direct.pathVariables().getString("id"));
                });
                async.GET("/now", HttpResponse.ok("now"));
                async.GET("/later").respondAsync(direct -> CompletableFuture.supplyAsync(() -> direct.responses().ok("later"), LATER));

                // declined later, and on an executor
                async.POST("/upload").respondAsync(direct -> CompletableFuture.supplyAsync(() -> null, LATER));
                async.POST("/upload-on-executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> null);

                async.GET("/failed").respondAsync(direct -> CompletableFuture.supplyAsync(() -> {
                    throw new IllegalStateException("failed");
                }, LATER));
                async.GET("/throwing").respondAsync(direct -> {
                    throw new IllegalStateException("failed");
                });
                async.GET("/throwing-on-executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> {
                    throw new IllegalStateException("failed");
                });

                async.GET("/blocking-writer/sync").respond(direct -> direct.responses().ok(new BlockingBody("sync")).contentType(BLOCKING));
                async.GET("/blocking-writer/executor")
                    .executeOn(TaskExecutors.BLOCKING)
                    .respond(direct -> direct.responses().ok(new BlockingBody("executor")).contentType(BLOCKING));
                async.GET("/blocking-writer/completed").respondAsync(direct ->
                    CompletableFuture.completedFuture(direct.responses().ok(new BlockingBody("completed")).contentType(BLOCKING)));

                async.GET("/hanging").respondAsync(direct -> {
                    CompletableFuture<HttpResponse<?>> stage = new CompletableFuture<>();
                    threads.hanging.set(stage);
                    threads.hangingCalled.get().countDown();
                    return stage;
                });
            });
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/async/upload").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).handle((request, pathVariables, body) ->
                HttpResponse.ok("stored " + body.length() + " chars").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/async/upload-on-executor").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).handle((request, pathVariables, body) ->
                HttpResponse.ok("stored " + body.length() + " chars").contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
