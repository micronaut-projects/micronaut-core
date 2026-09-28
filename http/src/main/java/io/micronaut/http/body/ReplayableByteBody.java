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
 * be read again, see {@link #isReplayable()}. A body whose known length is over the limit keeps
 * nothing and is read once. A reader through {@link ByteBody#toReadBufferPublisher()} holds at
 * most as many bytes it has not consumed yet as the limit, apart from the bytes the body keeps.
 * The bytes kept for a body from {@link #next()} that is not read yet count against the limit
 * of the body.
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
    /**
     * The known length of a body that is read once, as it is over the limit, or {@code -1}.
     */
    private final long readOnceLength;
    private boolean closed;
    private boolean readOnce;

    ReplayableByteBody(CloseableByteBody root, @Nullable BaseSharedBuffer sharedBuffer, long limit) {
        this.root = root;
        this.sharedBuffer = sharedBuffer;
        this.limit = limit;
        this.readOnceLength = -1;
    }

    ReplayableByteBody(CloseableByteBody root, long readOnceLength, long limit) {
        this.root = root;
        this.sharedBuffer = null;
        this.limit = limit;
        this.readOnceLength = readOnceLength;
    }

    /**
     * @return Whether the body can still be read from the start: no more bytes than the limit
     * arrived, as far as known, and the body did not fail
     */
    public synchronized boolean isReplayable() {
        if (closed || readOnceLength >= 0) {
            return false;
        }
        if (sharedBuffer == null) {
            return true;
        }
        return !sharedBuffer.isBufferLimitExceeded() && !sharedBuffer.isFailed();
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
        if (readOnceLength >= 0) {
            if (readOnce) {
                throw new BufferLengthExceededException(limit, readOnceLength);
            }
            readOnce = true;
            return root.move();
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
