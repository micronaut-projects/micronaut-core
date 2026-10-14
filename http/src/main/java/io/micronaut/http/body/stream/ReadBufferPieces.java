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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * The pieces of a body as they are received, without copying them: each piece is an element,
 * which its consumer closes. Empty pieces are skipped.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ReadBufferPieces implements PieceReader<ReadBuffer> {

    private final ArrayDeque<ReadBuffer> pieces = new ArrayDeque<>(1);

    @Override
    public void read(ReadBuffer piece) {
        if (piece.readable() > 0) {
            pieces.add(piece);
        } else {
            piece.close();
        }
    }

    @Override
    public void complete() {
        // every piece is an element
    }

    @Override
    public @Nullable ReadBuffer poll() {
        return pieces.poll();
    }

    @Override
    public void close() {
        ReadBuffer piece;
        while ((piece = pieces.poll()) != null) {
            piece.close();
        }
    }
}
