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
 * is applied. From then on pieces go straight to the sink. The terminating piece, a failure or
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

    private final EventLoopFlow flow;
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
        if (unconsumedBytes > 0 && sink.isWritable()) {
            report();
        }
    }

    private void report() {
        long n = takeUnconsumedBytes();
        bytesConsumed(n);
    }

    private void write(ReadBuffer buf) {
        unconsumedBytes += buf.readable();
        sink.write(NettyReadBufferFactory.toByteBuf(buf), false);
    }

    private void finish(ByteBuf last) {
        state = State.DONE;
        sink.write(last, true);
        markResponseWritten();
    }

    private void fail(Throwable t) {
        state = State.DONE;
        releaseEarlyData();
        sink.fail(t);
        markResponseWritten();
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
            unconsumedBytes += buf.readable();
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
