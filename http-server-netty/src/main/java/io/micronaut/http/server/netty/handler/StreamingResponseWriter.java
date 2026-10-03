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
package io.micronaut.http.server.netty.handler;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.EventLoopFlow;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.OrderedEventExecutor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The protocol-independent part of writing a streaming response body: the {@link BufferConsumer}
 * that the body is subscribed to. It serializes the body signals onto the event loop through an
 * {@link EventLoopFlow}, buffers the signals that arrive before the response is
 * {@linkplain #open() up for writing}, hands the pieces to a protocol-specific {@link Sink} in
 * order, keeps track of the bytes that were written but not yet reported to the
 * {@link Upstream} as consumed, and reports {@link Sink#responseWritten()} exactly once.
 * <p>
 * Lifecycle: the writer starts out <i>pending</i>. Data, completion and errors that arrive now
 * are held back: HTTP/1 queues responses behind each other and may not write this one yet, and
 * a body that already buffered some bytes (e.g. the response of an HTTP client relayed by a
 * route) hands them over as soon as it is subscribed to, before the protocol handler has even
 * attached the upstream. {@link #open()} makes the writer <i>open</i>: the sink writes the head
 * of the response, the buffered pieces follow, the upstream is started and a buffered completion
 * is applied. From then on adjacent small pieces are combined within one event loop turn, up to 8 KiB. The terminating piece, a failure or
 * {@link #dispose()} make the writer <i>done</i>: further pieces are released, further signals
 * are ignored.
 * <p>
 * All methods other than the {@link BufferConsumer} methods (and {@link #attach}, which must
 * happen-before the first event loop task that touches the writer) must be called on the event
 * loop.
 *
 * @since 5.3.0
 */
@Internal
final class StreamingResponseWriter implements BufferConsumer {
    private enum State {
        PENDING,
        OPEN,
        DONE
    }

    private static final int SMALL_PIECE_LIMIT = 1024;
    private static final int AGGREGATION_LIMIT = 8192;

    private final OrderedEventExecutor loop;
    private final ByteBufAllocator allocator;
    private final EventLoopFlow flow;
    @Nullable
    private ByteBuf pending;
    private boolean pendingCopied;
    private int pendingAcknowledged;
    private boolean drainScheduled;
    private final Sink sink;
    private State state = State.PENDING;
    private BufferConsumer.@Nullable Upstream upstream;
    private boolean started;
    /**
     * Bytes handed to the sink that have not been reported to the upstream as consumed yet.
     */
    private long unconsumedBytes;
    /**
     * {@code true} iff {@link Sink#responseWritten()} has been called.
     */
    private boolean responseWritten;
    /**
     * Pieces that arrived while pending. They are written after the response head.
     */
    @Nullable
    private List<ReadBuffer> earlyData;
    private boolean earlyComplete;
    @Nullable
    private Throwable earlyError;

    /**
     * @param loop The event loop of the channel
     * @param sink The protocol-specific sink
     */
    StreamingResponseWriter(OrderedEventExecutor loop, Sink sink) {
        this(loop, sink, ByteBufAllocator.DEFAULT);
    }

    /**
     * @param loop The event loop of the channel
     * @param sink The protocol-specific sink
     * @param allocator The allocator of the channel, for combined pieces
     */
    StreamingResponseWriter(OrderedEventExecutor loop, Sink sink, ByteBufAllocator allocator) {
        this.allocator = allocator;
        this.loop = loop;
        this.flow = new EventLoopFlow(loop);
        this.sink = sink;
    }

    /**
     * Attach the upstream of the body. Must be called exactly once, before {@link #open()}.
     *
     * @param upstream The upstream
     */
    void attach(Upstream upstream) {
        if (this.upstream != null) {
            throw new IllegalStateException("Upstream already attached");
        }
        this.upstream = Objects.requireNonNull(upstream, "upstream");
    }

    /**
     * Run a task on the event loop, in order with the body signals that were submitted before
     * this call. Runs the task immediately if that is possible.
     *
     * @param task The task
     */
    void execute(Runnable task) {
        if (flow.executeNow(task)) {
            task.run();
        }
    }

    private Upstream requiredUpstream() {
        return Objects.requireNonNull(upstream, "upstream");
    }

    /**
     * @return {@code true} iff the response is done: it has been terminated, has failed or was
     * discarded
     */
    boolean isDone() {
        return state == State.DONE;
    }

    /**
     * The response is up for writing. If the body failed in the meantime, the failure is handled
     * now instead ({@link Sink#fail}), and nothing is written. Otherwise the sink
     * {@linkplain Sink#open() writes the response head}, the pieces that arrived so far follow,
     * the upstream is started, and a completion that arrived so far terminates the response.
     * Only the first call does anything.
     */
    void open() {
        if (state != State.PENDING) {
            return;
        }
        Throwable t = earlyError;
        if (t != null) {
            earlyError = null;
            fail(t);
            return;
        }
        state = State.OPEN;
        sink.open();
        List<ReadBuffer> data = earlyData;
        if (data != null) {
            earlyData = null;
            for (ReadBuffer buf : data) {
                write(buf);
            }
        }
        if (!started) {
            started = true;
            requiredUpstream().start();
        }
        if (earlyComplete) {
            earlyComplete = false;
            finish(Unpooled.EMPTY_BUFFER);
        } else {
            reportIfWritable();
        }
    }

    /**
     * The sink can take more data now. The bytes written so far are reported to the upstream as
     * consumed, if the response is still open.
     */
    void onWritable() {
        if (state == State.OPEN) {
            report();
        }
    }

    /**
     * Take the bytes that were written but not yet reported as consumed, for a sink that
     * reports consumption itself once the written data is confirmed (see
     * {@link Sink#isWritable()}). The caller reports them through {@link #bytesConsumed(long)}.
     *
     * @return The number of bytes, possibly {@code 0}
     */
    long takeUnconsumedBytes() {
        long n = unconsumedBytes;
        unconsumedBytes = 0;
        return n;
    }

    /**
     * Report bytes as consumed to the upstream.
     *
     * @param n The number of bytes
     */
    void bytesConsumed(long n) {
        if (n > 0) {
            requiredUpstream().onBytesConsumed(n);
        }
    }

    /**
     * Allow the upstream to discard the remaining data of the body.
     */
    void allowDiscard() {
        requiredUpstream().allowDiscard();
    }

    /**
     * The response will not be written (any further): release the pieces that are held, ignore
     * further signals, and report {@link Sink#responseWritten()} if that has not happened yet.
     * Safe to call in any state.
     */
    void dispose() {
        state = State.DONE;
        releaseEarlyData();
        releasePending();
        earlyComplete = false;
        earlyError = null;
        markResponseWritten();
    }

    /**
     * Report {@link Sink#responseWritten()}. The sink sees exactly one call, no matter how often
     * this method is called.
     */
    void markResponseWritten() {
        if (!responseWritten) {
            responseWritten = true;
            sink.responseWritten();
        }
    }

    private void reportIfWritable() {
        if ((unconsumedBytes > 0 || pending != null) && sink.isWritable()) {
            report();
        }
    }

    private void report() {
        long n = takeUnconsumedBytes();
        ByteBuf current = pending;
        if (current != null) {
            // Writable HTTP/1 sinks may consume into this bounded buffer. Otherwise a publisher
            // that waits for each acknowledgement cannot produce adjacent pieces. Stop granting
            // credit as soon as a write makes the channel unwritable. HTTP/2 never uses report:
            // its acknowledgements still cover only bytes handed to the protocol sink.
            int size = current.readableBytes();
            n += size - pendingAcknowledged;
            pendingAcknowledged = size;
        }
        bytesConsumed(n);
    }

    private void write(ReadBuffer buf) {
        ByteBuf next = NettyReadBufferFactory.toByteBuf(buf);
        if (state != State.OPEN) {
            next.release();
            return;
        }
        int size = next.readableBytes();
        if (size > SMALL_PIECE_LIMIT) {
            drainPending();
            if (state == State.OPEN) {
                writeToSink(next, false);
            } else {
                next.release();
            }
            return;
        }
        ByteBuf current = pending;
        if (current != null && current.readableBytes() + size > AGGREGATION_LIMIT) {
            drainPending();
            if (state != State.OPEN) {
                next.release();
                return;
            }
            current = null;
        }
        pending = current == null ? next : combine(current, next);
        if (pending.readableBytes() == AGGREGATION_LIMIT) {
            drainPending();
        } else if (!drainScheduled) {
            drainScheduled = true;
            loop.execute(this::drainTurn);
        }
    }

    private void finish(ByteBuf last) {
        state = State.DONE;
        ByteBuf current = pending;
        pending = null;
        int acknowledged = pendingAcknowledged;
        pendingAcknowledged = 0;
        if (current != null) {
            if (!sink.canMergeLast()) {
                writeToSink(current, false, acknowledged);
                acknowledged = 0;
            } else if (!last.isReadable()) {
                last.release();
                last = current;
            } else if (last.readableBytes() <= SMALL_PIECE_LIMIT
                && current.readableBytes() + last.readableBytes() <= AGGREGATION_LIMIT) {
                last = combine(current, last);
            } else {
                writeToSink(current, false, acknowledged);
                acknowledged = 0;
            }
        }
        pendingCopied = false;
        writeToSink(last, true, acknowledged);
        markResponseWritten();
    }

    private void fail(Throwable t) {
        state = State.DONE;
        releaseEarlyData();
        // Preserve the protocol's failure behavior for bytes already received: HTTP/1 flushes
        // them before closing, while HTTP/2 can release its held frame when resetting.
        drainPending();
        sink.fail(t);
        markResponseWritten();
    }

    private ByteBuf combine(ByteBuf current, ByteBuf next) {
        if (!pendingCopied) {
            // the allocator of the channel: a piece may come from another one, e.g. a wrapped array
            ByteBuf copy = allocator.buffer(AGGREGATION_LIMIT, AGGREGATION_LIMIT);
            append(copy, current);
            current = copy;
            pendingCopied = true;
        }
        append(current, next);
        return current;
    }

    private static void append(ByteBuf target, ByteBuf piece) {
        int n = piece.readableBytes();
        if (piece.hasArray()) {
            // straight from the array, without the NIO view a heap-to-direct copy goes through
            target.writeBytes(piece.array(), piece.arrayOffset() + piece.readerIndex(), n);
        } else {
            target.writeBytes(piece, piece.readerIndex(), n);
        }
        piece.release();
    }

    private void writeToSink(ByteBuf data, boolean last) {
        writeToSink(data, last, 0);
    }

    private void writeToSink(ByteBuf data, boolean last, int acknowledged) {
        // Exclude bytes already accepted into the bounded HTTP/1 aggregation buffer.
        unconsumedBytes += data.readableBytes() - acknowledged;
        sink.write(data, last);
    }

    private void drainPending() {
        ByteBuf current = pending;
        pending = null;
        pendingCopied = false;
        int acknowledged = pendingAcknowledged;
        pendingAcknowledged = 0;
        if (current != null) {
            writeToSink(current, false, acknowledged);
        }
    }

    private void drainTurn() {
        drainScheduled = false;
        if (state == State.OPEN) {
            drainPending();
            reportIfWritable();
        }
    }

    private void releasePending() {
        ByteBuf current = pending;
        pending = null;
        pendingCopied = false;
        pendingAcknowledged = 0;
        if (current != null) {
            current.release();
        }
    }

    private void releaseEarlyData() {
        List<ReadBuffer> data = earlyData;
        if (data != null) {
            earlyData = null;
            for (ReadBuffer buf : data) {
                buf.close();
            }
        }
    }

    @Override
    public void add(ReadBuffer buf) {
        if (flow.executeNow(() -> add0(buf))) {
            add0(buf);
        }
    }

    private void add0(ReadBuffer buf) {
        if (state == State.PENDING) {
            if (earlyData == null) {
                earlyData = new ArrayList<>(1);
            }
            earlyData.add(buf);
        } else if (state == State.OPEN) {
            write(buf);
            reportIfWritable();
        } else {
            buf.close();
        }
    }

    @Override
    public void addAndComplete(ReadBuffer buf) {
        if (flow.executeNow(() -> addAndComplete0(buf))) {
            addAndComplete0(buf);
        }
    }

    private void addAndComplete0(ReadBuffer buf) {
        if (state == State.OPEN) {
            // the final bytes go out as the message that terminates the response, instead of a
            // message of their own followed by an empty terminator
            finish(NettyReadBufferFactory.toByteBuf(buf));
        } else {
            add0(buf);
            complete0();
        }
    }

    @Override
    public void complete() {
        if (flow.executeNow(this::complete0)) {
            complete0();
        }
    }

    private void complete0() {
        if (state == State.PENDING) {
            earlyComplete = true;
        } else if (state == State.OPEN) {
            finish(Unpooled.EMPTY_BUFFER);
        }
        // else: already terminated, failed or disposed
    }

    @Override
    public void error(Throwable e) {
        if (flow.executeNow(() -> error0(e))) {
            error0(e);
        }
    }

    private void error0(Throwable e) {
        if (state == State.PENDING) {
            earlyError = e;
        } else if (state == State.OPEN) {
            fail(e);
        }
        // else: already terminated, failed or disposed
    }

    /**
     * The protocol-specific side of a streaming response. All methods are called on the event
     * loop, in order: {@link #open()} once (unless the body failed before), then any number of
     * {@link #write} calls of which the one with {@code last} set is the final call, or
     * {@link #fail}. {@link #responseWritten()} is called exactly once, after the last write, the
     * failure, or from {@link StreamingResponseWriter#dispose()}.
     */
    interface Sink {
        /**
         * Write the head of the response. The first pieces of the body follow in the same
         * call to {@link StreamingResponseWriter#open()} if they arrived early.
         */
        void open();

        /**
         * Write a piece of the body. Ownership of the buffer transfers to the sink.
         *
         * @param data The bytes, possibly empty
         * @param last {@code true} iff this is the terminating message of the response. No
         *             further write follows
         */
        void write(ByteBuf data, boolean last);

        /**
         * Whether bytes written now count as consumed right away, so that the writer reports
         * them to the upstream after each piece. A sink that confirms its writes itself (e.g.
         * once a batch of frames is on the wire) returns {@code false} and reports through
         * {@link StreamingResponseWriter#takeUnconsumedBytes()} and
         * {@link StreamingResponseWriter#bytesConsumed(long)}.
         *
         * @return {@code true} iff written bytes are consumed immediately
         */
        boolean isWritable();

        /**
         * @return Whether pending data may be combined with the terminating message
         */
        default boolean canMergeLast() {
            return true;
        }

        /**
         * The body failed. This is called at most once, in place of the last write, and never
         * after it.
         *
         * @param t The failure
         */
        void fail(Throwable t);

        /**
         * The response is done: it has been written, has failed or was discarded. Called
         * exactly once.
         */
        void responseWritten();
    }
}
