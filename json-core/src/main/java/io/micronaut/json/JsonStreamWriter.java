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
package io.micronaut.json;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a sequence of values of one type to one {@link OutputStream}, one value at a time. Created
 * by {@link JsonMapper#createStreamWriter(OutputStream, Argument)}.
 *
 * <p>The writer emits nothing between the values, so the caller decides how the sequence is
 * framed, e.g. as the elements of a JSON array with brackets and commas, or as a JSON stream. A
 * writer is not thread-safe.
 *
 * @param <T> The type of the values
 * @author Jonas Konrad
 * @since 5.3.0
 */
@Experimental
public interface JsonStreamWriter<T> extends Closeable {
    /**
     * Write one value. When this method returns, the whole value has been written to the output
     * stream: the writer does not hold back any of its bytes in a buffer of its own.
     *
     * @param value The value to write
     * @throws IOException If the value cannot be serialized or the stream cannot be written to
     */
    void write(@Nullable T value) throws IOException;

    /**
     * Close this writer and the output stream it writes to.
     *
     * @throws IOException If the stream cannot be closed
     */
    @Override
    void close() throws IOException;
}
