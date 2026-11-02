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
import io.micronaut.core.io.buffer.ReadBufferFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Collects pieces of a body into a list. A body can arrive in very small pieces, e.g. one byte
 * per HTTP chunk, and every piece has a fixed overhead far beyond its size. Small pieces are
 * copied together, so the number of pieces kept is bounded by the number of bytes, not by the
 * number of pieces the client chose to send.
 *
 * @since 5.2.21
 */
@Internal
public final class ReadBufferCoalescing {
    /**
     * Pieces smaller than this are copied together until the copy reaches this size.
     */
    static final int SMALL_PIECE = 1024;

    private ReadBufferCoalescing() {
    }

    /**
     * Add a piece to the list, taking ownership of it.
     *
     * @param pieces  The pieces collected so far, owned by the caller
     * @param piece   The piece to add
     * @param factory The factory for the copy of small pieces
     */
    public static void add(List<ReadBuffer> pieces, ReadBuffer piece, ReadBufferFactory factory) {
        int n = piece.readable();
        if (n < SMALL_PIECE && !pieces.isEmpty()) {
            int last = pieces.size() - 1;
            ReadBuffer tail = pieces.get(last);
            int t = tail.readable();
            if (t < SMALL_PIECE) {
                // the tail leaves the list first: transferTo consumes it, even if the copy fails
                pieces.remove(last);
                try {
                    pieces.add(factory.buffer(t + n, out -> {
                        tail.transferTo(out);
                        piece.transferTo(out);
                    }));
                } catch (IOException e) {
                    // the stream is in memory and does not throw
                    throw new UncheckedIOException(e);
                } finally {
                    // no effect after transferTo, this only matters if the copy failed
                    tail.close();
                    piece.close();
                }
                return;
            }
        }
        pieces.add(piece);
    }
}
