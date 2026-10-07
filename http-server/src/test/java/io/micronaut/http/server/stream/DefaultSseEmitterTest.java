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
package io.micronaut.http.server.stream;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.TypedMessageBodyWriter;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.http.exceptions.StreamOverflowException;
import io.micronaut.http.sse.Event;
import io.micronaut.http.sse.SseEmitter;
import io.micronaut.web.router.builder.HandlerMethod;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle of a {@link DefaultSseEmitter}, without a server: the response is sent with the
 * first event, or replaced before; the stream ends when the handler returns unless it kept it
 * open; a failure before the first event fails the response, after it the stream; sends after the
 * end fail with why the stream closed.
 */
class DefaultSseEmitterTest {

    private static final String SPEC_NAME = "DefaultSseEmitterTest";
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static ApplicationContext context;
    private static SseEmitterFactory emitters;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        emitters = context.getBean(SseEmitterFactory.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void everyLineOfACommentIsACommentLine() throws Exception {
        HttpResponse<?> response = response(start(HttpRequest.GET("/events"), events -> {
            events.comment("a\r\nb\rc\n\nd\n");
        }, null));
        String text = read(response).received();
        // CRLF, CR and LF end a line, as a client reads them
        assertEquals(": a\n: b\n: c\n:\n: d\n:\n\n", text);
    }

    @Test
    void theResponseCarriesTheEventsAndTheHeaders() throws Exception {
        MutableHttpRequest<?> request = HttpRequest.GET("/events").header("Last-Event-ID", "41");
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        HttpResponse<?> response = response(start(request, events -> {
            emitter.set(events);
            assertTrue(events.isOpen());
            assertTrue(events.isWritable());
            events.header("X-Stream", "s-1");
            assertThrows(IllegalArgumentException.class, () -> events.header(HttpHeaders.CONTENT_TYPE, "text/plain"));
            events.send(Event.of("one").id("42").name("count"));
            assertThrows(IllegalStateException.class, () -> events.header("X-Late", "1"));
            events.send((Object) Event.of("two"));
            events.send(events.lastEventId().orElse("none"));
            events.comment("first\nsecond");
            events.comment("");
        }, null));
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(MediaType.TEXT_EVENT_STREAM_TYPE, response.getContentType().orElseThrow());
        assertEquals("no-cache", response.getHeaders().get(HttpHeaders.CACHE_CONTROL));
        assertEquals("s-1", response.getHeaders().get("X-Stream"));
        Consumer body = read(response);
        String text = body.received();
        assertTrue(text.contains("id: 42\n"), text);
        assertTrue(text.contains("event: count\n"), text);
        assertTrue(text.contains("data: one\n"), text);
        assertTrue(text.contains("data: two\n"), text);
        assertTrue(text.contains("data: 41\n"), text);
        assertTrue(text.endsWith(": first\n: second\n\n:\n\n"), text);
        assertTrue(body.complete);
        // the stream ended when the handler returned
        assertFalse(emitter.get().isOpen());
        assertFalse(emitter.get().isWritable());
    }

    @Test
    void anEmptyStreamIsSentWhenTheHandlerReturns() throws Exception {
        HttpResponse<?> response = response(start(HttpRequest.GET("/events"), events -> { }, null));
        assertEquals(MediaType.TEXT_EVENT_STREAM_TYPE, response.getContentType().orElseThrow());
        Consumer body = read(response);
        assertEquals("", body.received());
        assertTrue(body.complete);
        // completed by the handler, with its own cache control
        response = response(start(HttpRequest.GET("/events"), events -> {
            events.header(HttpHeaders.CACHE_CONTROL, "no-store");
            events.complete();
        }, null));
        assertEquals("no-store", response.getHeaders().get(HttpHeaders.CACHE_CONTROL));
        assertTrue(read(response).complete);
    }

    @Test
    void aSendAfterTheHandlerReturnedFailsWithAHint() throws Exception {
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        HttpResponse<?> response = response(start(HttpRequest.GET("/events"), events -> {
            emitter.set(events);
            events.send("one");
        }, null));
        assertTrue(read(response).complete);
        for (int i = 0; i < 2; i++) {
            // warned once
            ExecutionException late = assertThrows(ExecutionException.class, () -> emitter.get().send("late").toCompletableFuture().get());
            assertTrue(late.getCause().getMessage().contains("keepOpen()"), late.getCause().getMessage());
        }
        SseEmitter ended = emitter.get();
        IllegalStateException keepOpen = assertThrows(IllegalStateException.class, ended::keepOpen);
        assertTrue(keepOpen.getMessage().contains("before the handler returns"), keepOpen.getMessage());
    }

    @Test
    void aStreamKeptOpenEndsWhenCompleted() throws Exception {
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        AtomicReference<String> closed = new AtomicReference<>();
        CompletionStage<HttpResponse<?>> stage = start(HttpRequest.GET("/events"), events -> {
            emitter.set(events);
            events.keepOpen().highWaterMark(1024).onClose(cause -> closed.set(String.valueOf(cause)));
        }, null);
        assertFalse(stage.toCompletableFuture().isDone(), "no event yet");
        emitter.get().send("one");
        Consumer body = read(response(stage));
        emitter.get().send("two");
        assertNull(closed.get());
        emitter.get().complete();
        assertEquals("data: one\n\ndata: two\n\n", body.received());
        assertTrue(body.complete);
        assertEquals("null", closed.get());
        // a failure after the end changes nothing
        emitter.get().fail(new IllegalStateException("too late"));
        assertEquals("null", closed.get());
    }

    @Test
    void respondReplacesTheEventStream() throws Exception {
        HttpResponse<?> response = response(start(HttpRequest.POST("/messages", ""), events -> {
            events.header("Session-Id", "s-1").header("X-Both", "events");
            events.respond(HttpResponse.ok(Map.of("result", "pong")).header("X-Both", "response"));
            HttpResponse<?> again = HttpResponse.accepted();
            assertThrows(IllegalStateException.class, () -> events.respond(again));
            assertThrows(IllegalStateException.class, () -> events.header("X-Late", "1"));
            assertTrue(events.send("never").toCompletableFuture().isCompletedExceptionally());
        }, null));
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().orElseThrow());
        assertEquals("s-1", response.getHeaders().get("Session-Id"));
        assertEquals("response", response.getHeaders().get("X-Both"));
        assertEquals(Map.of("result", "pong"), response.getBody().orElseThrow());
        // without a body, no content type
        HttpResponse<?> accepted = response(start(HttpRequest.POST("/messages", ""), events -> events.respond(HttpResponse.accepted()), null));
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatus());
        assertTrue(accepted.getContentType().isEmpty());
        // with its own content type
        HttpResponse<?> text = response(start(HttpRequest.POST("/messages", ""), events -> events.respond(HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE)), null));
        assertEquals(MediaType.TEXT_PLAIN_TYPE, text.getContentType().orElseThrow());
        // after the first event
        AtomicReference<IllegalStateException> late = new AtomicReference<>();
        assertTrue(read(response(start(HttpRequest.POST("/messages", ""), events -> {
            events.send("one");
            HttpResponse<?> tooLate = HttpResponse.accepted();
            late.set(assertThrows(IllegalStateException.class, () -> events.respond(tooLate)));
        }, null))).complete);
        assertTrue(late.get().getMessage().contains("already sent"), late.get().getMessage());
    }

    @Test
    void aFailureBeforeTheFirstEventFailsTheResponse() {
        IllegalStateException failure = new IllegalStateException("not found");
        assertSame(failure, failure(start(HttpRequest.GET("/events"), events -> events.fail(failure), null)));
        assertSame(failure, failure(start(HttpRequest.GET("/events"), events -> {
            throw failure;
        }, null)));
        // a failure of a heartbeat that has not run: the response was not sent either
        assertSame(failure, failure(start(HttpRequest.GET("/events"), events -> {
            events.keepOpen().heartbeat(Duration.ofHours(1));
            events.fail(failure);
        }, null)));
    }

    @Test
    void aFailureAfterTheFirstEventFailsTheStream() throws Exception {
        IllegalStateException failure = new IllegalStateException("broken");
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        Consumer failed = read(response(start(HttpRequest.GET("/events"), events -> {
            emitter.set(events);
            events.keepOpen().send("one");
        }, null)));
        emitter.get().fail(failure);
        assertEquals("data: one\n\n", failed.received());
        assertSame(failure, failed.error);
        Consumer thrown = read(response(start(HttpRequest.GET("/events"), events -> {
            events.send("one");
            throw failure;
        }, null)));
        assertSame(failure, thrown.error);
    }

    @Test
    void aFailureOfTheHandlerAfterTheStreamEndedIsOnlyLogged() throws Exception {
        Consumer completed = read(response(start(HttpRequest.GET("/events"), events -> {
            events.send("one");
            events.complete();
            throw new IllegalStateException("after the end");
        }, null)));
        assertTrue(completed.complete);
        // a sender that stopped because the client left
        Consumer left = read(response(start(HttpRequest.GET("/events"), events -> {
            events.complete();
            throw new ConnectionClosedException("the client left");
        }, null)));
        assertTrue(left.complete);
    }

    @Test
    void anEventThatCannotBeEncodedFailsItsSendOnly() throws Exception {
        AtomicReference<Throwable> send = new AtomicReference<>();
        AtomicReference<Throwable> sendAndAwait = new AtomicReference<>();
        Consumer body = read(response(start(HttpRequest.GET("/events"), events -> {
            events.send(new Broken()).whenComplete((ignored, error) -> send.set(error));
            Broken broken = new Broken();
            sendAndAwait.set(assertThrows(CodecException.class, () -> events.sendAndAwait(broken)));
            events.send("after");
        }, null)));
        assertInstanceOf(CodecException.class, send.get());
        assertEquals("data: after\n\n", body.received());
        assertTrue(body.complete);
    }

    @Test
    void aHandlerOnAnExecutorPacesItsSendsToTheClient() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Throwable> afterEnd = new AtomicReference<>();
            Consumer body = read(response(start(HttpRequest.GET("/events"), events -> {
                events.sendAndAwait(Event.of("one"));
                events.sendAndAwait("two");
                events.complete();
                afterEnd.set(assertThrows(IllegalStateException.class, () -> events.sendAndAwait("three")));
            }, executor)));
            assertTrue(awaitTrue(() -> body.complete));
            assertEquals("data: one\n\ndata: two\n\n", body.received());
            assertTrue(awaitTrue(() -> afterEnd.get() != null));
            assertTrue(afterEnd.get().getMessage().startsWith("The stream is closed"), afterEnd.get().getMessage());
        } finally {
            executor.shutdownNow();
        }
        // an executor that refuses the handler fails the response
        RejectedExecutionException refused = new RejectedExecutionException("full");
        assertSame(refused, failure(start(HttpRequest.GET("/events"), events -> events.send("never"), command -> {
            throw refused;
        })));
    }

    @Test
    void aSendAfterTheClientLeftFailsWithConnectionClosed() throws Exception {
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        AtomicReference<@Nullable Throwable> closed = new AtomicReference<>();
        Consumer body = read(response(start(HttpRequest.GET("/events"), events -> {
            emitter.set(events);
            events.keepOpen().onClose(closed::set).send("one");
        }, null)));
        body.upstream().allowDiscard();
        assertInstanceOf(ConnectionClosedException.class, closed.get());
        assertFalse(emitter.get().isOpen());
        SseEmitter left = emitter.get();
        ConnectionClosedException failure = assertThrows(ConnectionClosedException.class, () -> left.sendAndAwait("two"));
        assertTrue(failure.getMessage().startsWith("The stream is closed"), failure.getMessage());
        assertSame(closed.get(), failure.getCause());
    }

    @Test
    void aSendThatOverflowsTheQueueFailsWithStreamOverflow() throws Exception {
        AtomicReference<Throwable> overflow = new AtomicReference<>();
        Consumer body = new Consumer(false);
        body.subscribe(response(start(HttpRequest.GET("/events"), events -> {
            // the client takes nothing: sixteen bytes overflow a mark of one
            events.highWaterMark(1);
            events.send("one");
            events.send("two");
            events.send("three");
            overflow.set(assertThrows(StreamOverflowException.class, () -> events.sendAndAwait("four")));
        }, null)));
        assertTrue(overflow.get().getMessage().startsWith("The stream is closed"), overflow.get().getMessage());
        assertInstanceOf(StreamOverflowException.class, overflow.get().getCause());
        assertInstanceOf(StreamOverflowException.class, body.error);
    }

    @Test
    void theHeartbeatKeepsAnIdleStreamBusy() throws Exception {
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        CompletionStage<HttpResponse<?>> stage = start(HttpRequest.GET("/events"), events -> {
            emitter.set(events);
            Duration negative = Duration.ofMillis(-1);
            assertThrows(IllegalArgumentException.class, () -> events.heartbeat(negative));
            // the first beat sends the response
            events.keepOpen().heartbeat(Duration.ofMillis(10));
        }, null);
        Consumer body = read(response(stage));
        assertTrue(awaitTrue(() -> body.received().contains(":\n\n")), body.received());
        // events count as activity
        for (int i = 0; i < 5; i++) {
            emitter.get().send("event");
            pause(5);
        }
        emitter.get().heartbeat(Duration.ZERO);
        emitter.get().complete();
        assertTrue(body.complete);
        assertTrue(body.received().contains("data: event\n\n"), body.received());
    }

    @Test
    void theConfiguredHeartbeatStartsWhenTheResponseIsSent() throws Exception {
        try (ApplicationContext configured = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.responses.stream.sse-heartbeat", "10ms"))) {
            SseEmitterFactory factory = configured.getBean(SseEmitterFactory.class);
            AtomicReference<SseEmitter> emitter = new AtomicReference<>();
            Consumer body = read(response(factory.start(HttpRequest.GET("/events"), FACTORY, events -> {
                emitter.set(events);
                events.keepOpen().send("one");
            })));
            assertTrue(awaitTrue(() -> body.received().contains(":\n\n")), body.received());
            emitter.get().complete();
            assertTrue(body.complete);
            // a heartbeat of the handler replaces it
            AtomicReference<SseEmitter> silent = new AtomicReference<>();
            Consumer quiet = read(response(factory.start(HttpRequest.GET("/events"), FACTORY, events -> {
                silent.set(events);
                events.keepOpen().heartbeat(Duration.ZERO).send("one");
            })));
            pause(50);
            silent.get().complete();
            assertEquals("data: one\n\n", quiet.received());
        }
    }

    @Test
    void aHeadRequestIsAnsweredWithoutWaitingForAnEvent() throws Exception {
        AtomicReference<String> closed = new AtomicReference<>();
        HttpResponse<?> response = response(start(HttpRequest.HEAD("/events"), events -> events.keepOpen().onClose(cause -> closed.set(String.valueOf(cause))), null));
        assertEquals(MediaType.TEXT_EVENT_STREAM_TYPE, response.getContentType().orElseThrow());
        // the server closes the body of the response to a HEAD request: a normal end
        ((CloseableByteBody) ((ByteBodyHttpResponse<?>) response).byteBody()).close();
        assertEquals("null", closed.get());
    }

    @Test
    void theBodiesOfTheRouteAreReleasedWhenTheStreamEnds() throws Exception {
        AtomicInteger released = new AtomicInteger();
        MutableHttpRequest<?> request = HttpRequest.POST("/completions", "");
        BasicHttpAttributes.addRouteBody(request, () -> {
            released.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        Consumer body = read(response(start(request, events -> {
            emitter.set(events);
            events.keepOpen().send("one");
        }, null)));
        assertEquals(0, released.get(), "the stream is open");
        emitter.get().complete();
        assertTrue(body.complete);
        assertEquals(1, released.get());
        // a release that fails is logged
        MutableHttpRequest<?> throwing = HttpRequest.POST("/completions", "");
        BasicHttpAttributes.addRouteBody(throwing, () -> {
            throw new IllegalStateException("cannot release");
        });
        assertTrue(read(response(start(throwing, events -> events.send("one"), null))).complete);
        MutableHttpRequest<?> failing = HttpRequest.POST("/completions", "");
        BasicHttpAttributes.addRouteBody(failing, () -> CompletableFuture.failedFuture(new IllegalStateException("cannot release")));
        assertTrue(read(response(start(failing, events -> events.send("one"), null))).complete);
    }

    private static CompletionStage<HttpResponse<?>> start(HttpRequest<?> request, HandlerMethod.SseResponder.Handler handler, java.util.concurrent.@Nullable Executor executor) {
        return new DefaultSseEmitter(request, FACTORY, emitters).start(handler, executor);
    }

    private static HttpResponse<?> response(CompletionStage<HttpResponse<?>> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static Throwable failure(CompletionStage<HttpResponse<?>> stage) {
        ExecutionException error = assertThrows(ExecutionException.class, () -> stage.toCompletableFuture().get(10, TimeUnit.SECONDS));
        return error.getCause();
    }

    private static Consumer read(HttpResponse<?> response) {
        Consumer consumer = new Consumer(true);
        consumer.subscribe(response);
        return consumer;
    }

    private static boolean awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            pause(5);
        }
        return true;
    }

    /**
     * Let the heartbeat scheduler run for a while.
     *
     * @param millis How long
     */
    private static void pause(long millis) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    /**
     * Data that its JSON writer cannot write.
     */
    record Broken() {
    }

    @Singleton
    @Produces(MediaType.APPLICATION_JSON)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BrokenWriter implements TypedMessageBodyWriter<Broken> {
        @Override
        public Argument<Broken> getType() {
            return Argument.of(Broken.class);
        }

        @Override
        public void writeTo(Argument<Broken> type, MediaType mediaType, Broken object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new CodecException("Cannot write a broken event");
        }
    }

    /**
     * The connection: takes what it is given, and reports it as consumed unless told otherwise.
     */
    private static final class Consumer implements BufferConsumer {
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final boolean consume;
        private BufferConsumer.@Nullable Upstream upstream;
        private volatile boolean complete;
        private volatile @Nullable Throwable error;

        Consumer(boolean consume) {
            this.consume = consume;
        }

        void subscribe(HttpResponse<?> response) {
            BufferConsumer.Upstream u = ((BaseStreamingByteBody<?>) ((ByteBodyHttpResponse<?>) response).byteBody()).primary(this);
            synchronized (this) {
                upstream = u;
            }
            u.start();
            if (consume) {
                u.disregardBackpressure();
            }
        }

        synchronized BufferConsumer.Upstream upstream() {
            BufferConsumer.Upstream u = upstream;
            if (u == null) {
                throw new IllegalStateException("not subscribed");
            }
            return u;
        }

        synchronized String received() {
            return received.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void add(ReadBuffer rb) {
            try (rb) {
                synchronized (this) {
                    received.writeBytes(rb.toArray());
                }
            }
        }

        @Override
        public void complete() {
            complete = true;
        }

        @Override
        public void error(Throwable e) {
            error = e;
        }
    }
}
