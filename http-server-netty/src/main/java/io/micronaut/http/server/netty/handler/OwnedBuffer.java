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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

/**
 * The single owner of a buffer within a scope. The buffer is either handed off once with
 * {@link #take()}, after which the receiver releases it, or released by {@link #close()}. Used
 * with try-with-resources, every path out of the scope releases the buffer exactly once unless it
 * was handed off.
 *
 * <p>Not thread-safe: the owner is confined to the thread of its scope.
 *
 * @since 5.3.0
 */
@Internal
final class OwnedBuffer implements AutoCloseable {
    @Nullable
    private ByteBuf buffer;

    /**
     * @param buffer The buffer, whose ownership transfers to this owner
     */
    OwnedBuffer(ByteBuf buffer) {
        this.buffer = buffer;
    }

    /**
     * Hand the buffer off. The caller owns it afterwards, and {@link #close()} does not release
     * it.
     *
     * @return The buffer
     * @throws IllegalStateException if the buffer was already taken or released
     */
    ByteBuf take() {
        ByteBuf b = buffer;
        if (b == null) {
            throw new IllegalStateException("Buffer already taken or released");
        }
        buffer = null;
        return b;
    }

    /**
     * Release the buffer, unless it was taken. Further calls do nothing.
     */
    @Override
    public void close() {
        ByteBuf b = buffer;
        if (b != null) {
            buffer = null;
            b.release();
        }
    }
}
