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
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonValueBatchReader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Function;

/**
 * The {@link PieceReader} of JSON, without Reactive Streams: the {@link JsonChunkedProcessor}
 * splits the pieces into the bytes of the values, and a value is decoded when it is polled.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class JsonPieceReader<T> implements PieceReader<T> {

    private final JsonChunkedProcessor processor;
    private final Function<ReadBuffer, @Nullable T> valueReader;
    private final @Nullable JsonMapper batchMapper;
    private final @Nullable Argument<T> batchType;
    /**
     * Queued values not polled yet: either a {@link ReadBuffer} for a single value or a
     * {@link BatchEntry} for a contiguous batch of values in one chunk.
     */
    private final ArrayDeque<Object> values = new ArrayDeque<>(1);
    private boolean closed;

    /**
     * @param processor   The processor that splits the pieces into values, configured for the
     *                    JSON to read: a stream of values, or the elements of an array
     * @param valueReader Decodes the bytes of a value, and closes them
     */
    JsonPieceReader(JsonChunkedProcessor processor, Function<ReadBuffer, @Nullable T> valueReader) {
        this(processor, valueReader, null, null);
    }

    /**
     * @param processor   The processor that splits the pieces into values
     * @param valueReader Decodes a single value buffer
     * @param batchMapper Optional mapper supporting {@link JsonMapper#createBatchReader}
     * @param batchType   The target element type for batch reading
     */
    JsonPieceReader(JsonChunkedProcessor processor,
                    Function<ReadBuffer, @Nullable T> valueReader,
                    @Nullable JsonMapper batchMapper,
                    @Nullable Argument<T> batchType) {
        this.processor = processor;
        this.valueReader = valueReader;
        this.batchMapper = batchMapper;
        this.batchType = batchType;
    }

    @Override
    public void read(ReadBuffer piece) throws IOException {
        if (closed) {
            piece.close();
            return;
        }
        if (batchMapper != null && batchType != null) {
            processor.feed(piece, values::add, this::addBatch);
        } else {
            processor.feed(piece, values::add);
        }
    }

    private void addBatch(byte[] bytes, int offset, int length, int count, boolean arrayElements, @Nullable ReadBuffer owner) {
        values.add(new BatchEntry<>(bytes, offset, length, count, arrayElements, owner));
    }

    @Override
    public void complete() throws IOException {
        if (!closed) {
            processor.finish(values::add);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable T poll() {
        Object head = values.peek();
        if (head == null) {
            return null;
        }
        @Nullable T element;
        if (head instanceof ReadBuffer value) {
            values.poll();
            try {
                element = valueReader.apply(value);
            } finally {
                // a no-op once the reader consumed it, else what a failed reading left
                value.close();
            }
        } else {
            BatchEntry<T> batch = (BatchEntry<T>) head;
            element = pollBatch(batch);
        }
        if (element == null) {
            // null means that no element is available: a JSON null is not an element, as the
            // reactive readers refuse it
            throw new CodecException("A JSON null is not an element of a JSON array or stream");
        }
        return element;
    }

    private @Nullable T pollBatch(BatchEntry<T> batch) {
        JsonMapper mapper = Objects.requireNonNull(batchMapper);
        Argument<T> type = Objects.requireNonNull(batchType);
        JsonValueBatchReader<T> reader = batch.reader;
        if (reader == null) {
            try {
                reader = mapper.createBatchReader(batch.bytes, batch.offset, batch.length, batch.arrayElements, type);
            } catch (IOException e) {
                values.poll();
                batch.close();
                throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
            }
            if (reader == null) {
                values.poll();
                batch.close();
                throw new IllegalStateException("Batch reader unavailable");
            }
            batch.reader = reader;
        }
        @Nullable T element;
        try {
            element = reader.next();
        } catch (IOException e) {
            values.poll();
            batch.close();
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            values.poll();
            batch.close();
            throw e;
        }
        if (--batch.remaining <= 0) {
            values.poll();
            batch.close();
        }
        return element;
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        processor.discard();
        Object entry;
        while ((entry = values.poll()) != null) {
            if (entry instanceof ReadBuffer value) {
                value.close();
            } else if (entry instanceof BatchEntry<?> batch) {
                batch.close();
            }
        }
    }

    private static final class BatchEntry<T> {
        final byte[] bytes;
        final int offset;
        final int length;
        final boolean arrayElements;
        final @Nullable ReadBuffer owner;
        int remaining;
        @Nullable JsonValueBatchReader<T> reader;

        BatchEntry(byte[] bytes, int offset, int length, int remaining, boolean arrayElements, @Nullable ReadBuffer owner) {
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
            this.remaining = remaining;
            this.arrayElements = arrayElements;
            this.owner = owner;
        }

        void close() {
            try {
                if (reader != null) {
                    reader.close();
                    reader = null;
                }
            } finally {
                if (owner != null) {
                    owner.close();
                }
            }
        }
    }
}
