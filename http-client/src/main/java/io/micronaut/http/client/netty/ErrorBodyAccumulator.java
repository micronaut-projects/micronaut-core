/*
 * Copyright 2017-2022 original authors
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
import io.netty.buffer.CompositeByteBuf;

/**
 * Accumulates the chunks of a streamed error body. Small chunks are copied into a shared tail
 * buffer and empty chunks are dropped, so the number of retained components is bounded by the
 * body size rather than by the number of chunks.
 */
@Internal
final class ErrorBodyAccumulator {
    static final int TAIL_CAPACITY = 8192;

    private final ByteBufAllocator allocator;
    // max components so the buffer is not consolidated on every add past the default of 16
    private final CompositeByteBuf buffer;
    private ByteBuf tail;

    ErrorBodyAccumulator(ByteBufAllocator allocator) {
        this.allocator = allocator;
        this.buffer = allocator.compositeBuffer(Integer.MAX_VALUE);
    }

    int readableBytes() {
        return buffer.readableBytes() + (tail == null ? 0 : tail.readableBytes());
    }

    int numComponents() {
        return buffer.numComponents() + (tail == null ? 0 : 1);
    }

    /**
     * Adds a chunk, taking ownership of it.
     *
     * @param content The chunk
     */
    void add(ByteBuf content) {
        int length = content.readableBytes();
        if (length == 0) {
            content.release();
            return;
        }
        if (length >= TAIL_CAPACITY) {
            flushTail();
            buffer.addComponent(true, content);
            return;
        }
        try {
            if (tail != null && tail.writableBytes() < length) {
                flushTail();
            }
            if (tail == null) {
                tail = allocator.buffer(TAIL_CAPACITY, TAIL_CAPACITY);
            }
            tail.writeBytes(content);
        } finally {
            content.release();
        }
    }

    /**
     * @return The accumulated body; the caller owns it
     */
    ByteBuf finish() {
        flushTail();
        return buffer;
    }

    void release() {
        if (tail != null) {
            tail.release();
            tail = null;
        }
        buffer.release();
    }

    private void flushTail() {
        if (tail != null) {
            buffer.addComponent(true, tail);
            tail = null;
        }
    }
}
