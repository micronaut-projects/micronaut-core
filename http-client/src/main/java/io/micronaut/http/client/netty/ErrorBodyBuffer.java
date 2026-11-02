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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Buffers a streaming error body up to a byte limit. Every piece is copied into one buffer and
 * released at once, so neither tiny nor empty pieces retain memory beyond the bytes they carry.
 *
 * @since 4.10.20
 */
@Internal
final class ErrorBodyBuffer {
    private final ByteBuf buffer;
    private final int maxContentLength;

    ErrorBodyBuffer(ByteBufAllocator allocator, int maxContentLength) {
        this.buffer = allocator.buffer(0, Math.max(maxContentLength, 0));
        this.maxContentLength = maxContentLength;
    }

    /**
     * The size the body would have with the given piece.
     *
     * @param piece The next piece
     * @return The size including the piece
     */
    long sizeWith(ByteBuf piece) {
        return (long) buffer.readableBytes() + piece.readableBytes();
    }

    /**
     * Adds a piece and releases it.
     *
     * @param piece The next piece
     * @return {@code false}, and nothing added, when the piece exceeds the limit
     */
    boolean add(ByteBuf piece) {
        try {
            if (sizeWith(piece) > maxContentLength) {
                return false;
            }
            if (piece.isReadable()) {
                buffer.writeBytes(piece);
            }
            return true;
        } finally {
            piece.release();
        }
    }

    /**
     * @return The buffered body, owned by this buffer
     */
    ByteBuf content() {
        return buffer;
    }

    void release() {
        buffer.release();
    }
}
