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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.CaseInsensitiveMutableHttpHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.sse.Event;
import io.micronaut.http.sse.SseEmitter;
import io.micronaut.web.router.builder.SseResponder;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The {@link SseEmitter} of a server-sent events route, on a {@link BodyStream}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultSseEmitter implements SseEmitter, SseResponder {

    /**
     * The request header with the id of the last event a reconnecting client received.
     */
    static final String LAST_EVENT_ID = "Last-Event-ID";

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Argument<Event<?>> EVENT = (Argument) Argument.of(Event.class);
    private static final byte[] HEARTBEAT = ":\n\n".getBytes(StandardCharsets.UTF_8);

    private static final int PENDING = 0;
    private static final int SENT = 1;
    private static final int REFUSED = 2;

    private final HttpRequest<?> request;
    private final ByteBodyFactory factory;
    private final SseResponderArgumentBinder support;
    private final @Nullable Executor handlerExecutor;
    private final CompletableFuture<HttpResponse<?>> response = new CompletableFuture<>();
    /**
     * Whether the response was sent ({@link #SENT}), or refused by a failure before
     * ({@link #REFUSED}).
     */
    private final AtomicInteger responseState = new AtomicInteger(PENDING);
    private @Nullable BodyStream stream;
    private PropagatedContext context = PropagatedContext.empty();
    private @Nullable MessageBodyWriter<Event<?>> writer;
    /**
     * Whether an event was sent since the last heartbeat tick.
     */
    private volatile boolean active;
    private @Nullable ScheduledFuture<?> heartbeat;

    /**
     * @param request         The request
     * @param factory         The body factory of the response
     * @param support         The beans of the emitter
     * @param handlerExecutor The executor of the route, the handler runs on, or {@code null} to
     *                        run it on the calling thread
     */
    DefaultSseEmitter(HttpRequest<?> request, ByteBodyFactory factory, SseResponderArgumentBinder support, @Nullable Executor handlerExecutor) {
        this.request = request;
        this.factory = factory;
        this.support = support;
        this.handlerExecutor = handlerExecutor;
    }

    @Override
    public CompletionStage<HttpResponse<?>> respond(Handler handler) {
        if (stream != null) {
            throw new IllegalStateException("The emitter already responded");
        }
        context = PropagatedContext.getOrEmpty();
        boolean head = request.getMethod() == HttpMethod.HEAD;
        BodyStream bodyStream = new BodyStream(factory, context, DEFAULT_HIGH_WATER_MARK, head);
        stream = bodyStream;
        bodyStream.onClose(ignored -> stopHeartbeat());
        Runnable run = () -> {
            try {
                handler.handle(this);
            } catch (Throwable e) {
                fail(e);
            }
            if (head) {
                // the response of a HEAD request has no body: no need to wait for an event
                sendResponse();
            }
        };
        Executor executor = handlerExecutor;
        if (executor == null) {
            run.run();
        } else {
            // a new task, so that the response is sent while a blocking handler still runs
            try {
                executor.execute(context.wrap(run));
            } catch (Throwable e) {
                fail(e);
            }
        }
        return response;
    }

    private BodyStream stream() {
        return Objects.requireNonNull(stream, "The emitter was not started");
    }

    @Override
    public CompletionStage<Void> send(Event<?> event) {
        Objects.requireNonNull(event, "event");
        ReadBuffer data;
        try {
            data = encode(event);
        } catch (Throwable e) {
            return CompletableFuture.failedStage(e);
        }
        active = true;
        return write(data);
    }

    @Override
    public CompletionStage<Void> comment(String comment) {
        Objects.requireNonNull(comment, "comment");
        StringBuilder text = new StringBuilder(comment.length() + 4);
        for (String line : comment.split("\r\n|\r|\n", -1)) {
            text.append(':');
            if (!line.isEmpty()) {
                text.append(' ').append(line);
            }
            text.append('\n');
        }
        text.append('\n');
        active = true;
        return write(factory.readBufferFactory().copyOf(text, StandardCharsets.UTF_8));
    }

    private CompletionStage<Void> write(ReadBuffer data) {
        CompletionStage<Void> result = stream().write(data);
        // after the write: the first event is in the body when the response goes out
        sendResponse();
        return result;
    }

    @Override
    public void sendAndAwait(Event<?> event) throws InterruptedException {
        if (stream().isEventLoopThread()) {
            throw new IllegalStateException("sendAndAwait blocks the calling thread: call it on a virtual thread or on a thread of a blocking executor, not on an event loop, where send(event) returns a stage instead");
        }
        try {
            send(event).toCompletableFuture().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Sending the event failed: " + cause, cause);
        }
    }

    @Override
    public boolean isWritable() {
        return stream().isWritable();
    }

    @Override
    public boolean isOpen() {
        return stream().isOpen();
    }

    @Override
    public Optional<String> lastEventId() {
        return Optional.ofNullable(request.getHeaders().get(LAST_EVENT_ID));
    }

    @Override
    public synchronized SseEmitter heartbeat(Duration period) {
        Objects.requireNonNull(period, "period");
        if (period.isNegative()) {
            throw new IllegalArgumentException("The heartbeat period must not be negative: " + period);
        }
        stopHeartbeat();
        if (!period.isZero() && stream().isOpen()) {
            heartbeat = support.scheduler().scheduleAtFixedRate(period, period, this::heartbeatTick);
            if (!stream().isOpen()) {
                // closed meanwhile: the close callback may have run before the heartbeat was set
                stopHeartbeat();
            }
        }
        return this;
    }

    private void heartbeatTick() {
        BodyStream bodyStream = stream();
        if (!bodyStream.isOpen()) {
            stopHeartbeat();
            return;
        }
        if (active) {
            // an event was sent since the last tick
            active = false;
            return;
        }
        if (bodyStream.isWritable() || responseState.get() == PENDING) {
            write(factory.readBufferFactory().adapt(HEARTBEAT.clone()));
        }
    }

    private synchronized void stopHeartbeat() {
        ScheduledFuture<?> future = heartbeat;
        if (future != null) {
            heartbeat = null;
            future.cancel(false);
        }
    }

    @Override
    public SseEmitter highWaterMark(int bytes) {
        stream().highWaterMark(bytes);
        return this;
    }

    @Override
    public SseEmitter onClose(Consumer<@Nullable Throwable> callback) {
        stream().onClose(Objects.requireNonNull(callback, "callback"));
        return this;
    }

    @Override
    public void complete() {
        if (stream().complete()) {
            // an empty stream
            sendResponse();
        }
    }

    @Override
    public void fail(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        BodyStream bodyStream = stream();
        if (responseState.compareAndSet(PENDING, REFUSED)) {
            // nothing was sent: the error handling of the route answers
            bodyStream.abandon(cause);
            context.propagate(() -> response.completeExceptionally(cause));
        } else {
            bodyStream.fail(cause);
        }
    }

    private void sendResponse() {
        if (!responseState.compareAndSet(PENDING, SENT)) {
            return;
        }
        MutableHttpResponse<?> headers = HttpResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM_TYPE)
            .header(HttpHeaders.CACHE_CONTROL, "no-cache");
        // the events must reach the client as they are sent: compression would buffer them
        headers.setAttribute(ResponseStreams.COMPRESSION_DISABLED, Boolean.TRUE);
        HttpResponse<?> sseResponse = MutableByteBodyHttpResponse.of(headers, stream().body());
        context.propagate(() -> response.complete(sseResponse));
    }

    private ReadBuffer encode(Event<?> event) {
        MessageBodyWriter<Event<?>> eventWriter = writer;
        if (eventWriter == null) {
            eventWriter = support.bodyHandlerRegistry().getWriter(EVENT, List.of(MediaType.TEXT_EVENT_STREAM_TYPE));
            writer = eventWriter;
        }
        MessageBodyWriter<Event<?>> w = eventWriter;
        return factory.readBufferFactory().buffer(out -> w.writeTo(
            EVENT,
            MediaType.TEXT_EVENT_STREAM_TYPE,
            event,
            new CaseInsensitiveMutableHttpHeaders(support.conversionService()),
            out
        ));
    }
}
