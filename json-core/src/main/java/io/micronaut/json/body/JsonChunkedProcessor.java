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
package io.micronaut.json.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Splits the pieces of a JSON input into the bytes of its values, as the {@link #counter} finds
 * their boundaries, for any {@link ReadBuffer}.
 *
 * <p>A piece that is backed by an array is scanned in place, and the values are split off it
 * without copying. A piece that is not, e.g. a direct buffer, is copied to the heap, where the
 * mappers read the values fastest, and scanned there: the values it holds whole are views of the
 * copy, which is reused for the next piece if it holds none. A value that spans pieces is
 * copied into one array when it completes. Every byte of a value is copied at most once.</p>
 *
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class JsonChunkedProcessor {
    final JsonCounter counter = new JsonCounter();
    /**
     * The maximum number of bytes of a JSON value that is buffered to be emitted.
     */
    private final long maxElementSize;
    /**
     * The parts of the value in progress, which the pieces read so far do not complete. Guarded
     * by this: the processing may be discarded on another thread than the one that feeds it.
     */
    @Nullable
    private ReadBuffer singlePart;
    @Nullable
    private List<ReadBuffer> parts;
    /**
     * The parts were released: what the processing still buffers is released at once.
     */
    private boolean released;

    /**
     * The regions of the values the piece being scanned completes, as pairs of offsets relative
     * to the start of the piece.
     */
    private int[] regions = new int[16];
    private int regionCount;
    /**
     * The offset in the piece being scanned of the value it starts but does not complete, or
     * {@code -1}.
     */
    private int bufferingFrom;
    /**
     * Whether the first region the scan found completes a value that earlier pieces started.
     */
    private boolean firstRegionContinues;
    /**
     * The copy of a piece that is not backed by an array, to scan it, unless values are views
     * of it.
     */
    private byte @Nullable [] scratch;
    /**
     * The failure of the scan of a piece, thrown once the values before it were handed over.
     */
    @Nullable
    private Throwable failure;

    JsonChunkedProcessor() {
        this(Long.MAX_VALUE);
    }

    /**
     * @param maxElementSize The maximum number of bytes of a JSON value: a larger one fails the
     *                       processing with a {@link ContentLengthExceededException}, once that
     *                       many bytes of it were buffered
     */
    JsonChunkedProcessor(long maxElementSize) {
        this.maxElementSize = maxElementSize;
    }

    /**
     * Feed a piece of the input: the values it completes are handed to the consumer, which takes
     * them over. The processor takes over the piece: it is closed, also when the processing
     * fails.
     *
     * @param piece The piece
     * @param out   Takes the values the piece completes
     * @throws IOException If the input is malformed
     */
    void feed(ReadBuffer piece, Consumer<? super ReadBuffer> out) throws IOException {
        try {
            if (piece.readable() == 0) {
                return;
            }
            regionCount = 0;
            bufferingFrom = -1;
            firstRegionContinues = false;
            failure = null;
            ReadBuffer view = piece.duplicate();
            if (view.useFastHeapBuffer(this::scan) != null) {
                emit(piece, out);
            } else {
                // no array to scan in place
                view.close();
                int length = piece.readable();
                byte[] bytes = scratch;
                if (bytes == null || bytes.length < length) {
                    bytes = new byte[length];
                    scratch = bytes;
                }
                piece.duplicate().toArray(bytes, 0);
                scan(bytes, 0, length);
                emitCopying(piece, bytes, out);
            }
        } finally {
            piece.close();
        }
        Throwable f = failure;
        if (f != null) {
            failure = null;
            if (f instanceof IOException e) {
                throw e;
            }
            throw (RuntimeException) f;
        }
    }

    /**
     * The end of the input: the value that only the end completes is handed to the consumer.
     *
     * @param out Takes the value the end completes
     * @throws IOException If the input ends inside a value
     */
    void finish(Consumer<? super ReadBuffer> out) throws IOException {
        counter.noMoreInput();
        flush(out);
    }

    /**
     * Release what is buffered of a value that is not complete. What the processing buffers
     * after this is released at once.
     */
    synchronized void discard() {
        released = true;
        if (singlePart != null) {
            singlePart.close();
            singlePart = null;
        }
        if (parts != null) {
            closeAll(parts);
            parts = null;
        }
    }

    private Boolean scan(java.nio.ByteBuffer heap) {
        scan(heap.array(), heap.arrayOffset() + heap.position(), heap.remaining());
        return Boolean.TRUE;
    }

    /**
     * Find the regions of the values in the bytes of a piece. A failure is recorded, not thrown:
     * the values before it are handed over first.
     */
    private void scan(byte[] bytes, int start, int length) {
        long initialPosition = counter.position();
        int end = start + length;
        int i = start;
        try {
            while (i < end) {
                i = counter.feed(bytes, i, end);
                JsonCounter.BufferRegion bufferRegion = counter.pollFlushedRegion();
                if (bufferRegion != null) {
                    checkSize(bufferRegion.end() - bufferRegion.start());
                    if (bufferRegion.start() < initialPosition) {
                        firstRegionContinues = true;
                    }
                    long regionStart = Math.max(initialPosition, bufferRegion.start());
                    addRegion(Math.toIntExact(regionStart - initialPosition), Math.toIntExact(bufferRegion.end() - initialPosition));
                }
            }
            if (counter.isBuffering()) {
                // what is buffered of a value that is not complete yet: a value that is too large
                // fails before it is buffered whole
                checkSize(counter.position() - counter.bufferStart());
                bufferingFrom = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - initialPosition);
            }
        } catch (IOException | RuntimeException e) {
            failure = e;
        }
    }

    private void addRegion(int start, int end) {
        int[] r = regions;
        if (regionCount + 2 > r.length) {
            r = Arrays.copyOf(r, r.length * 2);
            regions = r;
        }
        r[regionCount++] = start;
        r[regionCount++] = end;
    }

    /**
     * Split the values the scan found off the piece.
     */
    private void emit(ReadBuffer piece, Consumer<? super ReadBuffer> out) {
        int consumed = 0;
        int[] r = regions;
        for (int k = 0; k < regionCount; k += 2) {
            int start = r[k];
            int end = r[k + 1];
            skip(piece, start - consumed);
            buffer(piece.split(end - start));
            consumed = end;
            flush(out);
        }
        if (bufferingFrom != -1 && failure == null) {
            skip(piece, bufferingFrom - consumed);
            buffer(piece.move());
        }
    }

    /**
     * Hand over the values the scan found in a copy of a piece: the values the piece holds whole
     * are views of the copy, only the parts of the values that span pieces are split off the
     * piece.
     */
    private void emitCopying(ReadBuffer piece, byte[] bytes, Consumer<? super ReadBuffer> out) {
        int consumed = 0;
        int[] r = regions;
        for (int k = 0; k < regionCount; k += 2) {
            int start = r[k];
            int end = r[k + 1];
            if (k == 0 && firstRegionContinues) {
                buffer(piece.split(end));
                consumed = end;
            } else {
                if (bytes == scratch) {
                    // the values are views of it: the next piece is copied to a new array
                    scratch = null;
                }
                buffer(ReadBufferFactory.getJdkFactory().adapt(java.nio.ByteBuffer.wrap(bytes, start, end - start)));
            }
            flush(out);
        }
        if (bufferingFrom != -1 && failure == null) {
            skip(piece, bufferingFrom - consumed);
            buffer(piece.move());
        }
    }

    private static void skip(ReadBuffer piece, int n) {
        if (n > 0) {
            piece.split(n).close();
        }
    }

    private void checkSize(long size) {
        if (size > maxElementSize) {
            throw new ContentLengthExceededException("The size of a JSON value [" + size + "] exceeds the maximum allowed content length [" + maxElementSize + "]");
        }
    }

    private synchronized void buffer(ReadBuffer part) {
        if (released) {
            part.close();
            return;
        }
        if (singlePart == null && parts == null) {
            singlePart = part;
        } else {
            if (parts == null) {
                parts = new ArrayList<>(4);
                parts.add(singlePart);
                singlePart = null;
            }
            parts.add(part);
        }
    }

    private void flush(Consumer<? super ReadBuffer> out) {
        ReadBuffer completed = take();
        if (completed != null) {
            // emitted without the lock: the processing may be discarded meanwhile
            out.accept(completed);
        }
    }

    /**
     * @return The buffered value, taken from this, or {@code null} if nothing is buffered, or
     * the parts were released
     */
    private synchronized @Nullable ReadBuffer take() {
        ReadBuffer single = singlePart;
        if (single != null) {
            singlePart = null;
            return single;
        }
        List<ReadBuffer> p = parts;
        if (p == null) {
            return null;
        }
        parts = null;
        return join(p);
    }

    /**
     * Copy the parts of a value that spans pieces into one array: the mappers read an array
     * fastest.
     */
    private static ReadBuffer join(List<ReadBuffer> parts) {
        int length = 0;
        for (ReadBuffer part : parts) {
            length = Math.addExact(length, part.readable());
        }
        byte[] joined = new byte[length];
        int offset = 0;
        for (int i = 0; i < parts.size(); i++) {
            ReadBuffer part = parts.get(i);
            int n = part.readable();
            try {
                part.toArray(joined, offset);
            } catch (RuntimeException e) {
                closeAll(parts.subList(i + 1, parts.size()));
                throw e;
            }
            offset += n;
        }
        return ReadBufferFactory.getJdkFactory().adapt(joined);
    }

    private static void closeAll(List<ReadBuffer> buffers) {
        for (ReadBuffer buffer : buffers) {
            buffer.close();
        }
    }
}
