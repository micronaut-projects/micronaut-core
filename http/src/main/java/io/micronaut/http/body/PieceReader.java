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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.codec.CodecException;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Reader of the elements of one body, fed the pieces of the body as they arrive, without
 * Reactive Streams: the reading counterpart of {@link PieceWriter}. Opened by
 * {@link ChunkedMessageBodyReader#openPieceReader}, it keeps the state across the pieces, e.g. a
 * JSON value split between two pieces.
 *
 * <p>An element is decoded when it is {@link #poll() polled}, not when the piece that completes
 * it is read: a reader that pulls one element at a time decodes nothing ahead of its caller, and
 * holds the bytes of the elements it has not polled yet.</p>
 *
 * <p>An element that {@link #poll()} returns is handed over to the caller: an element that holds
 * resources, e.g. a reference counted buffer, is released by the caller. The reader releases the
 * pieces it was fed, and, when it is {@link #close() closed}, the bytes of the elements it holds
 * that were not polled.</p>
 *
 * <p>The methods are called one at a time, never concurrently, but successive calls may
 * come from different threads. Input pieces are byte buffers; decoded elements are values
 * returned by {@link #poll()} and must never be {@code null}. Elements completed before
 * {@link #read} or {@link #complete} fails remain available to {@link #poll()}.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface PieceReader<T> extends Closeable {

    /**
     * Read the next piece of the body. The reader takes over the piece: it closes it, also when
     * reading fails.
     *
     * @param piece The piece
     * @throws IOException    If the input is malformed, e.g. a
     *                        {@code io.micronaut.json.JsonSyntaxException}
     * @throws CodecException If the piece cannot be read
     * @throws io.micronaut.http.exceptions.ContentLengthExceededException If an element exceeds
     *                        the limit supplied when opening the reader
     */
    void read(ReadBuffer piece) throws IOException;

    /**
     * The end of the body: the elements that only the end completes, e.g. a number at the end of
     * a JSON stream, become available.
     *
     * @throws IOException If the body ends inside an element
     */
    void complete() throws IOException;

    /**
     * Decode the next element that the pieces read so far complete.
     *
     * @return The element, or {@code null} if the pieces read so far complete no other element:
     * more pieces are needed, or, after {@link #complete()}, there are no more elements
     * @throws IOException    If the input is malformed
     * @throws CodecException If the element cannot be decoded
     */
    @Nullable
    T poll() throws IOException;

    /**
     * Release what the reader holds, e.g. the bytes of a partial element, or of the elements that
     * were not polled. May be called more than once.
     */
    @Override
    void close();
}
