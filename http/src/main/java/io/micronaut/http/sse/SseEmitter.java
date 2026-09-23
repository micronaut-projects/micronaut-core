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
package io.micronaut.http.sse;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Pushes server-sent events to one client: the {@code text/event-stream} response of an SSE
 * route. The events are encoded like the events of a {@code Publisher<Event>} body: the data of
 * an event is written as is if it is a {@link CharSequence}, and as JSON otherwise.
 *
 * <h2>The response</h2>
 * <p>The response ({@code 200}, {@code text/event-stream}, {@code Cache-Control: no-cache}, never
 * compressed) is sent when the first event, comment or heartbeat is sent, or when the stream is
 * completed. Until then the stream can still be refused: {@link #fail(Throwable)} (or an exception
 * thrown by the handler) is answered by the error handling of the route, exactly like an exception
 * of any other route. After that, a failure ends the response abruptly: it is logged, and the
 * connection is closed. To send the response before the first event, send a
 * {@link #comment(String) comment} or configure a {@link #heartbeat(Duration) heartbeat}.</p>
 *
 * <h2>Backpressure</h2>
 * <p>The stream counts the bytes the connection has not taken yet: bytes of events that are queued
 * because the client reads slower than the events are sent. The stage returned by
 * {@link #send(Event)} completes once the bytes of the event, together with the bytes queued
 * before it, are below the {@link #highWaterMark(int) high-water mark}: immediately if the client
 * keeps up, otherwise once the connection took enough of the queue. Waiting for the stage (or
 * using {@link #sendAndAwait(Event)}) paces the sender to the client. {@link #isWritable()} tells
 * whether a send would complete immediately, for senders that would rather drop events than wait,
 * such as a broadcast to many clients.</p>
 * <p>The queue is bounded: a send while sixteen times the high-water mark are already queued fails
 * with a {@link io.micronaut.http.exceptions.ConnectionClosedException}, and ends the stream, since
 * the client would otherwise miss events. A sender that awaits its sends never reaches the
 * bound.</p>
 *
 * <h2>Threads and the end of the stream</h2>
 * <p>All methods can be called from any thread. The events are written in the order their sends
 * were called. The stages complete, and the {@link #onClose} callbacks run, on a thread chosen by
 * the server, usually the event loop of the connection, with the propagated context of the route
 * handler: a continuation must not block. When the client disconnects, the pending and the later
 * sends fail with a {@link io.micronaut.http.exceptions.ConnectionClosedException}, and the
 * {@link #onClose} callbacks run with that exception.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface SseEmitter extends AutoCloseable {

    /**
     * The default {@link #highWaterMark(int) high-water mark}, in bytes.
     */
    int DEFAULT_HIGH_WATER_MARK = 64 * 1024;

    /**
     * Send an event.
     *
     * @param event The event
     * @return Completes when the event was queued below the high-water mark (see the class
     * documentation), or exceptionally if the stream is closed, the client disconnected, the queue
     * overflowed, or the event could not be encoded (the stream stays open)
     */
    CompletionStage<Void> send(Event<?> event);

    /**
     * Send an event with the given data.
     *
     * @param data The data of the event
     * @return Completes like {@link #send(Event)}
     */
    default CompletionStage<Void> send(Object data) {
        return send(data instanceof Event<?> event ? event : Event.of(data));
    }

    /**
     * Send a comment, which clients ignore: lines that start with a colon.
     *
     * @param comment The comment, may span several lines
     * @return Completes like {@link #send(Event)}
     */
    CompletionStage<Void> comment(String comment);

    /**
     * Send an event, and wait until its stage completes: pacing a sender on a virtual thread or on
     * a thread of a blocking executor to the client.
     *
     * @param event The event
     * @throws InterruptedException           if the thread was interrupted while waiting: the event
     *                                        may still be sent
     * @throws IllegalStateException          if called on an event loop thread, which must not
     *                                        block, or if the stream is closed
     * @throws io.micronaut.http.exceptions.ConnectionClosedException if the client disconnected
     */
    void sendAndAwait(Event<?> event) throws InterruptedException;

    /**
     * Whether a send would complete immediately: the stream is open, and the bytes the
     * connection has not taken yet are below the high-water mark.
     *
     * @return {@code true} if the stream can take more events without waiting
     */
    boolean isWritable();

    /**
     * Whether the stream is open: neither completed nor failed, and the client did not disconnect.
     *
     * @return {@code true} if events can be sent
     */
    boolean isOpen();

    /**
     * The id of the last event the client received before it reconnected: the
     * {@code Last-Event-ID} request header of the reconnection.
     *
     * @return The id, or empty on a first connection
     */
    Optional<String> lastEventId();

    /**
     * Send a comment line when no event was sent for the given period, to keep the connection
     * and the proxies on the way from timing it out as idle. The first heartbeat sends the
     * response if no event was sent yet. Heartbeats are skipped while the stream is not writable.
     *
     * @param period The period, {@link Duration#ZERO} to stop the heartbeat
     * @return This emitter
     */
    SseEmitter heartbeat(Duration period);

    /**
     * Change the high-water mark: the number of queued bytes the connection has not taken yet
     * above which sends wait. The default is {@link #DEFAULT_HIGH_WATER_MARK}.
     *
     * @param bytes The high-water mark, positive
     * @return This emitter
     */
    SseEmitter highWaterMark(int bytes);

    /**
     * Run a callback when the stream closes: with {@code null} when it was completed (or when the
     * response of a {@code HEAD} request, which has no body, was sent), and with the cause when it
     * failed, overflowed, or the client disconnected. A callback added after the stream closed
     * runs at once.
     *
     * @param callback The callback
     * @return This emitter
     */
    SseEmitter onClose(Consumer<@Nullable Throwable> callback);

    /**
     * End the stream: the queued events are still written, then the response ends. Pending send
     * stages complete. Does nothing if the stream is already closed.
     */
    void complete();

    /**
     * Fail the stream. Before the response was sent, the failure is answered by the error handling
     * of the route; after, the response ends abruptly and the failure is logged. Pending send
     * stages fail with the cause. Does nothing if the stream is already closed.
     *
     * @param cause The failure
     */
    void fail(Throwable cause);

    /**
     * {@link #complete() Complete} the stream.
     */
    @Override
    default void close() {
        complete();
    }
}
