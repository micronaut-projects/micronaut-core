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

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

/**
 * Combines adjacent small pieces of a streamed response body into one bounded buffer before
 * they go to the {@link Output}. Pieces of up to {@value #SMALL_PIECE_LIMIT} bytes that arrive in
 * the same event loop turn are combined, up to {@value #AGGREGATION_LIMIT} bytes. The held
 * buffer is handed over when it is full, before a piece that does not fit or a large piece, and
 * at the end of the turn, so a piece never waits for the next one. Large pieces pass through
 * without a copy.
 * <p>
 * The accumulator owns every buffer it holds: a lone piece as it arrived, or, once a second
 * piece joins it, an {@value #AGGREGATION_LIMIT} byte buffer of the channel allocator that the
 * pieces are copied into and released. {@link #release()} frees what is held.
 * <p>
 * <b>Credit.</b> Next to the held buffer the accumulator keeps how many of its bytes have
 * already been reported to the upstream as consumed ({@link #acknowledge()}). That count leaves
 * the accumulator together with the buffer ({@link Output#writePiece}), so the bytes are never
 * reported a second time once the buffer is written.
 * <p>
 * Not thread-safe: all methods must be called on the event loop.
 *
 * @since 5.3.0
 */
@Internal
final class PieceAccumulator implements Runnable {
    /**
     * Pieces larger than this pass through without being combined.
     */
    static final int SMALL_PIECE_LIMIT = 1024;
    /**
     * The size of the combined buffer.
     */
    static final int AGGREGATION_LIMIT = 8192;

    private final Executor loop;
    private final ByteBufAllocator allocator;
    private final Output output;
    /**
     * The bytes held back in this turn, or {@code null}.
     */
    @Nullable
    private ByteBuf held;
    /**
     * {@code true} iff {@link #held} is the accumulator's own {@value #AGGREGATION_LIMIT} byte
     * buffer, which further pieces are appended to. Otherwise it is a lone piece as it arrived.
     */
    private boolean combined;
    /**
     * The bytes of {@link #held} that have already been reported as consumed.
     */
    private int acknowledged;
    /**
     * {@code true} iff the end of turn drain ({@link #run()}) has been submitted and not run.
     */
    private boolean drainScheduled;
    /**
     * {@code true} once {@link #release()} or {@link #addLast} was called: pieces are released.
     */
    private boolean closed;

    /**
     * @param loop      The event loop, for the drain at the end of the turn
     * @param allocator The allocator of the channel, for the combined buffer
     * @param output    The receiver of the pieces
     */
    PieceAccumulator(Executor loop, ByteBufAllocator allocator, Output output) {
        this.loop = loop;
        this.allocator = allocator;
        this.output = output;
    }

    /**
     * @return The bytes held that have not been reported as consumed yet
     */
    int unacknowledged() {
        ByteBuf current = held;
        return current == null ? 0 : current.readableBytes() - acknowledged;
    }

    /**
     * Report the bytes held as accepted: the caller reports them to the upstream as consumed now,
     * before they are written. The writer does this only while its sink is writable, so the
     * bytes accepted ahead of a write stay bounded by {@value #AGGREGATION_LIMIT}.
     *
     * @return The bytes held that had not been acknowledged before, possibly {@code 0}
     */
    int acknowledge() {
        ByteBuf current = held;
        if (current == null) {
            return 0;
        }
        int size = current.readableBytes();
        int n = size - acknowledged;
        acknowledged = size;
        return n;
    }

    /**
     * Take a piece of the body. Ownership of the buffer transfers to the accumulator. A small
     * piece is held, a large one is written right after what is held. If an output write closes
     * the accumulator (e.g. the response is disposed from within the write), the piece is
     * released.
     *
     * @param piece The piece
     */
    void add(ByteBuf piece) {
        if (closed) {
            piece.release();
            return;
        }
        int size = piece.readableBytes();
        if (size > SMALL_PIECE_LIMIT) {
            drain();
            if (closed) {
                piece.release();
            } else {
                output.writePiece(piece, false, 0);
            }
            return;
        }
        ByteBuf current = held;
        if (current != null && current.readableBytes() + size > AGGREGATION_LIMIT) {
            drain();
            if (closed) {
                piece.release();
                return;
            }
            current = null;
        }
        current = current == null ? piece : combine(current, piece);
        held = current;
        if (current.readableBytes() == AGGREGATION_LIMIT) {
            drain();
        } else if (!drainScheduled) {
            drainScheduled = true;
            loop.execute(this);
        }
    }

    /**
     * Take the final piece of the body and close the accumulator. The final piece goes out as
     * the {@code last} write, combined with what is held when the output allows it and the
     * result stays within the limit. Ownership of the buffer transfers to the accumulator.
     *
     * @param last      The final piece, possibly empty
     * @param mergeLast Whether what is held may be combined with the final piece
     */
    void addLast(ByteBuf last, boolean mergeLast) {
        if (closed) {
            last.release();
            return;
        }
        closed = true;
        ByteBuf current = held;
        int ack = acknowledged;
        held = null;
        acknowledged = 0;
        if (current != null) {
            if (!mergeLast) {
                output.writePiece(current, false, ack);
                ack = 0;
            } else if (!last.isReadable()) {
                last.release();
                last = current;
            } else if (last.readableBytes() <= SMALL_PIECE_LIMIT
                && current.readableBytes() + last.readableBytes() <= AGGREGATION_LIMIT) {
                last = combine(current, last);
            } else {
                output.writePiece(current, false, ack);
                ack = 0;
            }
        }
        combined = false;
        output.writePiece(last, true, ack);
    }

    /**
     * Hand what is held to the output now, with the bytes of it already acknowledged.
     */
    void drain() {
        ByteBuf current = held;
        if (current == null) {
            return;
        }
        int ack = acknowledged;
        held = null;
        combined = false;
        acknowledged = 0;
        output.writePiece(current, false, ack);
    }

    /**
     * Release what is held and close the accumulator: further pieces are released.
     */
    void release() {
        closed = true;
        ByteBuf current = held;
        held = null;
        combined = false;
        acknowledged = 0;
        if (current != null) {
            current.release();
        }
    }

    /**
     * The end of the event loop turn in which the first held piece arrived.
     */
    @Override
    public void run() {
        drainScheduled = false;
        if (!closed) {
            drain();
            output.turnEnded();
        }
    }

    private ByteBuf combine(ByteBuf current, ByteBuf next) {
        if (!combined) {
            // the allocator of the channel: a piece may come from another one, e.g. a wrapped array
            ByteBuf copy = allocator.buffer(AGGREGATION_LIMIT, AGGREGATION_LIMIT);
            append(copy, current);
            current = copy;
            combined = true;
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

    /**
     * The receiver of the pieces.
     */
    interface Output {
        /**
         * Write a piece. Ownership of the buffer transfers to the output. The output may close
         * the accumulator from within this call.
         *
         * @param data         The bytes, possibly empty
         * @param last         {@code true} iff this is the final piece
         * @param acknowledged The bytes of {@code data} that were already reported as consumed
         *                     through {@link #acknowledge()}
         */
        void writePiece(ByteBuf data, boolean last, int acknowledged);

        /**
         * The pieces of the current event loop turn have all been written. Not called once the
         * accumulator is closed.
         */
        void turnEnded();
    }
}
