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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * The pieces of a response body as they were received, as heap buffers that are not reference
 * counted. Empty pieces are skipped.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class BodyPieces implements PieceReader<ByteBuffer<?>> {

    private final ArrayDeque<ByteBuffer<?>> pieces = new ArrayDeque<>(1);

    private BodyPieces() {
    }

    /**
     * @param body The body, which the elements take over
     * @return The pieces of the body
     */
    public static BodyElements<ByteBuffer<?>> elements(CloseableByteBody body) {
        return new ByteBodyElements<>(body, new BodyPieces(), BodyPieces::wrap);
    }

    /**
     * The failure of the elements of a response body, an {@link HttpClientException}.
     *
     * @param error A failure to read the body
     * @return The failure of the elements
     */
    public static Throwable wrap(Throwable error) {
        return error instanceof HttpClientException ? error : new HttpClientException("Error reading the response body: " + error.getMessage(), error);
    }

    @Override
    public void read(ReadBuffer piece) {
        try (piece) {
            if (piece.readable() > 0) {
                pieces.add(ByteArrayBufferFactory.INSTANCE.wrap(piece.toArray()));
            }
        }
    }

    @Override
    public void complete() {
        // every piece is an element
    }

    @Override
    public @Nullable ByteBuffer<?> poll() {
        return pieces.poll();
    }

    @Override
    public void close() {
        pieces.clear();
    }
}
