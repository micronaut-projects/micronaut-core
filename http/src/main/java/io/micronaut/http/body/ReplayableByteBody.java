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
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;

/**
 * A body that can be read more than once, e.g. to send a request again after a failed attempt,
 * see {@link ByteBodyFactory#replayable(CloseableByteBody, long)}. Each {@link #next()} is a body
 * with all the bytes from the start, while the bytes still arrive: the first reader streams them,
 * and they are kept, up to a limit, for the next readers. Once more bytes than the limit arrived,
 * the kept bytes are dropped: the readers that started still get every byte, and the body cannot
 * be read again, see {@link #isReplayable()}. A reader through
 * {@link ByteBody#toReadBufferPublisher()} counts the bytes it has not consumed yet against the
 * same limit.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ReplayableByteBody implements Closeable {
    private final CloseableByteBody root;
    @Nullable
    private final BaseSharedBuffer sharedBuffer;
    private final long limit;
    private boolean closed;

    ReplayableByteBody(CloseableByteBody root, @Nullable BaseSharedBuffer sharedBuffer, long limit) {
        this.root = root;
        this.sharedBuffer = sharedBuffer;
        this.limit = limit;
    }

    /**
     * @return Whether the body can still be read from the start: no more bytes than the limit
     * arrived, as far as known
     */
    public synchronized boolean isReplayable() {
        if (closed) {
            return false;
        }
        if (sharedBuffer == null) {
            return true;
        }
        long expected = root.expectedLength().orElse(-1);
        return (expected < 0 || expected <= limit) && !sharedBuffer.isBufferLimitExceeded();
    }

    /**
     * A body with all the bytes from the start. The caller closes it.
     *
     * @return The body
     * @throws BufferLengthExceededException if more bytes than the limit arrived, so that the
     * bytes from the start are gone
     * @throws IllegalStateException if this body is closed
     */
    public synchronized CloseableByteBody next() {
        if (closed) {
            throw new IllegalStateException("The replayable body is closed");
        }
        if (sharedBuffer != null && sharedBuffer.isBufferLimitExceeded()) {
            throw new BufferLengthExceededException(limit, limit + 1);
        }
        // the root is never read: its reservation keeps the bytes for the next readers, and the
        // fastest reader drives the upstream
        return root.split(ByteBody.SplitBackpressureMode.FASTEST);
    }

    /**
     * Drop the kept bytes. The readers that started are not affected.
     */
    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            root.close();
        }
    }
}
