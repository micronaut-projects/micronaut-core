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

/**
 * Lazy reader over a contiguous batch of complete JSON values backed by a byte array, allowing a
 * {@link JsonMapper} to reuse parser and deserialization context state across values in a chunk.
 *
 * @param <T> The element type
 * @since 5.3.0
 */
@Experimental
public interface JsonValueBatchReader<T> extends Closeable {

    /**
     * Read and return the next value in the batch, or {@code null} if the JSON value is {@code null}.
     *
     * @return The decoded value, or {@code null}
     * @throws IOException If decoding fails
     */
    @Nullable
    T next() throws IOException;

    @Override
    void close();
}
