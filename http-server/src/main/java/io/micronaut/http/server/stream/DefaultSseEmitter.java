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
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.http.exceptions.StreamOverflowException;
import io.micronaut.http.server.ServerResponseAttributes;
import io.micronaut.http.sse.Event;
import io.micronaut.http.sse.SseEmitter;
import io.micronaut.web.router.builder.HandlerMethod;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The {@link SseEmitter} of a server-sent events route, on a {@link BodyStream}. Created by
 * {@link SseEmitterFactory} when the route runs, for one run of its handler.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultSseEmitter implements SseEmitter {

    /**
     * The request header with the id of the last event a reconnecting client received.
     */
    static final String LAST_EVENT_ID = "Last-Event-ID";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSseEmitter.class);
    private static final byte[] HEARTBEAT = ":\n\n".getBytes(StandardCharsets.UTF_8);

    private final HttpRequest<?> request;
    private final ByteBodyFactory bodyFactory;
    private final SseEmitterFactory factory;
    private final BodyStream stream;
    private final PropagatedContext context;
    private final boolean head;
    private final CompletableFuture<HttpResponse<?>> response = new CompletableFuture<>();
    private final AtomicReference<ResponseState> responseState = new AtomicReference<>(ResponseState.PENDING);
    private final AtomicReference<HandlerState> handlerState = new AtomicReference<>(HandlerState.RUNNING);
    /**
     * The response, sent with the first event. Guarded by this emitter until it is sent.
     */
    private final MutableHttpResponse<?> headers = HttpResponse.ok();
    /**
     * Whether an event was sent since the last heartbeat tick.
     */
    private volatile boolean active;
    /**
     * Whether the stream ended because the handler returned without {@link #keepOpen()}.
     */
    private volatile boolean endedOnReturn;
    private final AtomicBoolean lateSendWarned = new AtomicBoolean();
    /**
     * Guarded by this emitter.
     */
    private @Nullable ScheduledFuture<?> heartbeatTask;
    /**
     * Whether the handler set the heartbeat, which replaces the configured one. Guarded by this
     * emitter.
     */
    private boolean heartbeatSet;

    /**
     * @param request     The request
     * @param bodyFactory The body factory of the response
     * @param factory     The factory, which encodes the events and schedules the heartbeats
     */
    DefaultSseEmitter(HttpRequest<?> request, ByteBodyFactory bodyFactory, SseEmitterFactory factory) {
        this.request = request;
        this.bodyFactory = bodyFactory;
        this.factory = factory;
        this.context = PropagatedContext.getOrEmpty();
        this.head = request.getMethod() == HttpMethod.HEAD;
        this.stream = new BodyStream(bodyFactory, context, factory.highWaterMark(), head);
    }

    /**
     * Run the handler of the route. The stream ends when the handler returns, unless it called
     * {@link #keepOpen()}.
     *
     * @param handler  The handler
     * @param executor The executor the handler runs on, as a new task, or {@code null} to run it
     *                 on the calling thread
     * @return Completes with the response when the first event is sent or the stream ends, or
     * exceptionally when the stream fails before
     */
    CompletionStage<HttpResponse<?>> start(HandlerMethod.SseResponder.Handler handler, @Nullable Executor executor) {
        stream.onClose(ignored -> stopHeartbeat());
        // the stream may be made of the reads of the body: what the binding of the body left
        // open is released when the stream ends, not when the response is sent
        ReleasableRequestBody bodies = BasicHttpAttributes.takeRouteBodies(request);
        if (bodies != null) {
            stream.onClose(ignored -> release(bodies));
        }
        Runnable run = () -> {
            Throwable failure = null;
            try {
                handler.handle(this);
            } catch (Throwable e) {
                failure = e;
            }
            handlerReturned(failure);
        };
        if (executor == null) {
            run.run();
        } else {
            // a new task, so that the response is sent while a blocking handler still runs
            try {
                executor.execute(context.wrap(run));
            } catch (Throwable e) {
                handlerReturned(e);
            }
        }
        return response;
    }

    /**
     * The handler returned: the stream ends, unless the handler kept it open. A failure of the
     * handler fails the stream.
     *
     * @param failure The failure of the handler, or {@code null}
     */
    private void handlerReturned(@Nullable Throwable failure) {
        boolean keptOpen = handlerState.getAndSet(HandlerState.RETURNED) == HandlerState.KEPT_OPEN;
        if (failure != null) {
            if (!failStream(failure) && !(failure instanceof ConnectionClosedException)) {
                // the stream ended before, e.g. the handler completed it: the failure has
                // nowhere else to go. A sender that stopped because the client left is not one
                LOG.warn("The server-sent events handler of {} {} failed after its stream ended", request.getMethodName(), request.getPath(), failure);
            }
        } else if (!keptOpen) {
            endedOnReturn = true;
            if (stream.complete("The stream ended when the handler returned: call keepOpen() to send events after the handler returns")) {
                sendResponse();
            }
        } else if (head) {
            // the response of a HEAD request has no body: no need to wait for an event
            sendResponse();
        }
    }

    @Override
    public SseEmitter keepOpen() {
        if (handlerState.compareAndExchange(HandlerState.RUNNING, HandlerState.KEPT_OPEN) == HandlerState.RETURNED) {
            throw new IllegalStateException("keepOpen() must be called before the handler returns: the stream ended when it returned");
        }
        return this;
    }

    @Override
    public CompletionStage<Void> send(Event<?> event) {
        Objects.requireNonNull(event, "event");
        ReadBuffer data;
        try {
            data = factory.encode(bodyFactory, event);
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
        // one comment line per line of the comment, whichever line break ends it
        int start = 0;
        int length = comment.length();
        for (int i = 0; i < length; i++) {
            char c = comment.charAt(i);
            if (c == '\r' || c == '\n') {
                appendCommentLine(text, comment, start, i);
                if (c == '\r' && i + 1 < length && comment.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        appendCommentLine(text, comment, start, length);
        text.append('\n');
        active = true;
        return write(bodyFactory.readBufferFactory().copyOf(text, StandardCharsets.UTF_8));
    }

    private static void appendCommentLine(StringBuilder text, String comment, int start, int end) {
        text.append(':');
        if (end > start) {
            text.append(' ').append(comment, start, end);
        }
        text.append('\n');
    }

    private CompletionStage<Void> write(ReadBuffer data) {
        CompletionStage<Void> result = stream.write(data);
        if (endedOnReturn && lateSendWarned.compareAndSet(false, true)) {
            // most likely a handler that sends from a callback and forgot keepOpen(): the failed
            // stage of the send is usually not looked at
            LOG.warn("An event was sent to the stream of {} {} after its handler returned, but the stream ended when the handler returned: call keepOpen() to send events after the handler returns", request.getMethodName(), request.getPath());
        }
        // after the write: the first event is in the body when the response goes out
        sendResponse();
        return result;
    }

    @Override
    public void sendAndAwait(Event<?> event) throws InterruptedException {
        Objects.requireNonNull(event, "event");
        if (stream.isEventLoopThread()) {
            throw new IllegalStateException("sendAndAwait blocks the calling thread: call it on a virtual thread or on a thread of a blocking executor, not on an event loop, where send(event) returns a stage instead");
        }
        // an event that cannot be encoded fails this call only, with its own exception
        ReadBuffer data = factory.encode(bodyFactory, event);
        active = true;
        try {
            write(data).toCompletableFuture().get();
        } catch (ExecutionException e) {
            throw closed(e.getCause() == null ? e : e.getCause());
        }
    }

    /**
     * The exception of a send that failed because the stream is closed: a new one, since every
     * send after the close shares the cause.
     *
     * @param cause Why the stream closed
     * @return The exception to throw
     */
    private static RuntimeException closed(Throwable cause) {
        String message = "The stream is closed: " + cause.getMessage();
        if (cause instanceof StreamOverflowException) {
            return new StreamOverflowException(message, cause);
        }
        if (cause instanceof ConnectionClosedException) {
            return new ConnectionClosedException(message, cause);
        }
        return new IllegalStateException(message, cause);
    }

    @Override
    public boolean isWritable() {
        return stream.isWritable();
    }

    @Override
    public boolean isOpen() {
        return stream.isOpen();
    }

    @Override
    public Optional<String> lastEventId() {
        return Optional.ofNullable(request.getHeaders().get(LAST_EVENT_ID));
    }

    @Override
    public synchronized SseEmitter header(CharSequence name, CharSequence value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name.toString())) {
            throw new IllegalArgumentException("The Content-Type of an event stream is text/event-stream");
        }
        if (responseState.get() != ResponseState.PENDING) {
            throw new IllegalStateException("The response was already sent: add its headers before the first event, comment or heartbeat");
        }
        headers.header(name, value);
        return this;
    }

    @Override
    public void respond(HttpResponse<?> other) {
        Objects.requireNonNull(other, "response");
        if (!stream.replace(() -> responseState.compareAndSet(ResponseState.PENDING, ResponseState.REPLACED))) {
            throw new IllegalStateException("The response was already sent, or the stream ended: answer with another response before the first event, comment or heartbeat");
        }
        MutableHttpResponse<?> replacement = other.toMutableResponse();
        synchronized (this) {
            // the headers given before: header(...) can no longer add any, the response is replaced
            for (Map.Entry<String, List<String>> header : headers.getHeaders()) {
                if (!replacement.getHeaders().contains(header.getKey())) {
                    for (String value : header.getValue()) {
                        replacement.header(header.getKey(), value);
                    }
                }
            }
        }
        if (replacement.getContentType().isEmpty() && replacement.getBody().isPresent()) {
            // the route produces text/event-stream, which is not the type of this body
            replacement.contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        context.propagate(() -> response.complete(replacement));
    }

    @Override
    public synchronized SseEmitter heartbeat(Duration period) {
        Objects.requireNonNull(period, "period");
        if (period.isNegative()) {
            throw new IllegalArgumentException("The heartbeat period must not be negative: " + period);
        }
        heartbeatSet = true;
        scheduleHeartbeat(period);
        return this;
    }

    /**
     * Start the configured heartbeat, once the response was sent, unless the handler set its own:
     * before, it would send the response, and take away the chance to answer with an error or
     * another response.
     */
    private synchronized void startConfiguredHeartbeat() {
        Duration period = factory.heartbeat();
        if (period != null && !heartbeatSet) {
            scheduleHeartbeat(period);
        }
    }

    private synchronized void scheduleHeartbeat(Duration period) {
        stopHeartbeat();
        if (!period.isZero() && stream.isOpen()) {
            heartbeatTask = factory.scheduler().scheduleAtFixedRate(period, period, this::heartbeatTick);
            if (!stream.isOpen()) {
                // closed meanwhile: the close callback may have run before the heartbeat was set
                stopHeartbeat();
            }
        }
    }

    private void heartbeatTick() {
        if (!stream.isOpen()) {
            stopHeartbeat();
            return;
        }
        if (active) {
            // an event was sent since the last tick
            active = false;
            return;
        }
        if (stream.isWritable() || responseState.get() == ResponseState.PENDING) {
            write(bodyFactory.readBufferFactory().adapt(HEARTBEAT.clone()));
        }
    }

    private synchronized void stopHeartbeat() {
        ScheduledFuture<?> future = heartbeatTask;
        if (future != null) {
            heartbeatTask = null;
            future.cancel(false);
        }
    }

    @Override
    public SseEmitter highWaterMark(int bytes) {
        stream.highWaterMark(bytes);
        return this;
    }

    @Override
    public SseEmitter onClose(Consumer<@Nullable Throwable> callback) {
        stream.onClose(Objects.requireNonNull(callback, "callback"));
        return this;
    }

    @Override
    public void complete() {
        if (stream.complete()) {
            // an empty stream
            sendResponse();
        }
    }

    @Override
    public void fail(Throwable cause) {
        failStream(Objects.requireNonNull(cause, "cause"));
    }

    /**
     * @param cause The failure
     * @return Whether this failed the stream, which was open
     */
    private boolean failStream(Throwable cause) {
        // nothing was sent: the error handling of the route answers. Only an open stream is
        // refused: a stream that completed, e.g. a fail() from an onClose callback of complete(),
        // keeps its response
        if (stream.abandon(cause, () -> responseState.compareAndSet(ResponseState.PENDING, ResponseState.REFUSED))) {
            context.propagate(() -> response.completeExceptionally(cause));
            return true;
        }
        return stream.fail(cause);
    }

    private void sendResponse() {
        if (responseState.get() != ResponseState.PENDING) {
            return;
        }
        HttpResponse<?> sseResponse;
        synchronized (this) {
            if (!responseState.compareAndSet(ResponseState.PENDING, ResponseState.SENT)) {
                return;
            }
            headers.contentType(MediaType.TEXT_EVENT_STREAM_TYPE);
            if (!headers.getHeaders().contains(HttpHeaders.CACHE_CONTROL)) {
                headers.header(HttpHeaders.CACHE_CONTROL, "no-cache");
            }
            // the events must reach the client as they are sent: compression would buffer them
            headers.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, Boolean.TRUE);
            sseResponse = MutableByteBodyHttpResponse.of(headers, stream.body());
        }
        context.propagate(() -> response.complete(sseResponse));
        startConfiguredHeartbeat();
    }

    private void release(ReleasableRequestBody bodies) {
        CompletionStage<Void> released;
        try {
            released = bodies.releaseBody();
        } catch (Throwable e) {
            released = CompletableFuture.failedFuture(e);
        }
        released.whenComplete((ignored, error) -> {
            if (error != null) {
                LOG.warn("Failed to release the body of {} {} when its event stream ended", request.getMethodName(), request.getPath(), error);
            }
        });
    }

    /**
     * Whether the response was sent ({@link #SENT}), refused by a failure before
     * ({@link #REFUSED}), or replaced by another response ({@link #REPLACED}).
     */
    private enum ResponseState {
        PENDING,
        SENT,
        REFUSED,
        REPLACED
    }

    /**
     * Whether the handler runs, kept the stream open, or returned.
     */
    private enum HandlerState {
        RUNNING,
        KEPT_OPEN,
        RETURNED
    }
}
