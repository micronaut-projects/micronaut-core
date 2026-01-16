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
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A counter for bytes that ensures we don't exceed a configured buffer limit. May be thread safe
 * or not thread safe depending on implementation.
 *
 * @since 5.0.0
 */
@Internal
public sealed interface SizeLimitTracker {
    /**
     * Add some bytes, with a check that there is enough room. If the limit is exceeded, this
     * method returns an exception and the counter remains unchanged.
     *
     * @param bytes The number of bytes
     * @return {@code null} iff the bytes were added to the counter
     */
    @Nullable
    Exception add(long bytes);

    /**
     * Subtract a number of bytes.
     *
     * @param bytes The number of bytes
     */
    void subtract(long bytes);

    /**
     * Return a tracker that has the same properties and value, but is atomic. If this tracker is
     * already atomic, return the same tracker.
     *
     * @return The atomic tracker
     */
    @NonNull
    SizeLimitTracker makeAtomic();

    /**
     * Create a new tracker pair that is not thread safe.
     *
     * @param limits The size limits
     * @return The tracker
     */
    @NonNull
    static TrackerPair notThreadSafe(@NonNull BodySizeLimits limits) {
        return new TrackerPair(
            NotThreadSafe.create(limits.maxBodySize(), false),
            NotThreadSafe.create(limits.maxBufferSize(), true));
    }

    /**
     * Combine two trackers.
     *
     * @param a The first tracker
     * @param b The second tracker
     * @return The combined tracker
     */
    @NonNull
    static TrackerPair combine(@NonNull TrackerPair a, @NonNull TrackerPair b) {
        return new TrackerPair(
            Composite.compose(a.totalSize, b.totalSize),
            Composite.compose(a.bufferedSize, b.bufferedSize)
        );
    }

    /**
     * A pair of trackers, one for the total size and one for the buffered size. This matches
     * {@link BodySizeLimits}.
     *
     * @param totalSize    The tracker for total size
     * @param bufferedSize The tracker for buffered size
     */
    record TrackerPair(
        SizeLimitTracker totalSize,
        SizeLimitTracker bufferedSize
    ) {
        public TrackerPair makeBothAtomic() {
            return new TrackerPair(totalSize.makeAtomic(), bufferedSize.makeAtomic());
        }

        @Nullable
        public Exception add(long value) {
            return Composite.addAtomic(totalSize, bufferedSize, value);
        }

        public void subtract(long value) {
            totalSize.subtract(value);
            bufferedSize.subtract(value);
        }
    }
}

final class Unlimited implements SizeLimitTracker {
    static final Unlimited INSTANCE = new Unlimited();

    private Unlimited() {
    }

    @Override
    public @Nullable Exception add(long bytes) {
        return null;
    }

    @Override
    public void subtract(long bytes) {
    }

    @Override
    public SizeLimitTracker makeAtomic() {
        return this;
    }

    @Override
    public String toString() {
        return "SizeLimitTracker[Unlimited]";
    }
}

final class NotThreadSafe implements SizeLimitTracker {
    private final long limit;
    private long value;
    private final boolean buffer;

    private NotThreadSafe(long limit, boolean buffer) {
        this.limit = limit;
        this.buffer = buffer;
    }

    static SizeLimitTracker create(long limit, boolean buffer) {
        if (limit == Long.MAX_VALUE) {
            return Unlimited.INSTANCE;
        }
        return new NotThreadSafe(limit, buffer);
    }

    @Contract(pure = true)
    @Nullable
    static Exception checkAdd(long value, long bytes, long limit, boolean buffer) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes < 0");
        }
        long newValue = value + bytes;
        if (newValue < value || newValue > limit) {
            return buffer ? new BufferLengthExceededException(limit, newValue) : new ContentLengthExceededException(limit, newValue);
        }
        return null;
    }

    @Override
    @Nullable
    public Exception add(long bytes) {
        long oldValue = value;
        Exception exc = checkAdd(value, bytes, limit, buffer);
        if (exc == null) {
            value = oldValue + bytes;
        }
        return exc;
    }

    @Contract(pure = true)
    static void checkSubtract(long value, long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes < 0");
        }
        if (bytes > value) {
            throw new IllegalArgumentException("bytes > value");
        }
    }

    @Override
    public void subtract(long bytes) {
        checkSubtract(value, bytes);
        value -= bytes;
    }

    @Override
    public SizeLimitTracker makeAtomic() {
        Atomic atomic = new Atomic(limit, buffer);
        atomic.set(value);
        return atomic;
    }

    @Override
    public String toString() {
        return "SizeLimitTracker[NotThreadSafe: " + value + "/" + limit + "]";
    }
}

final class Atomic extends AtomicLong implements SizeLimitTracker {
    private final long limit;
    private final boolean buffer;

    Atomic(long limit, boolean buffer) {
        this.limit = limit;
        this.buffer = buffer;
    }

    @Override
    public @Nullable Exception add(long bytes) {
        long oldValue2 = getAndUpdate(oldValue -> {
            Exception exc = NotThreadSafe.checkAdd(oldValue, bytes, limit, buffer);
            return exc == null ? oldValue + bytes : oldValue;
        });
        return NotThreadSafe.checkAdd(oldValue2, bytes, limit, buffer);
    }

    @Override
    public void subtract(long bytes) {
        getAndUpdate(oldValue -> {
            NotThreadSafe.checkSubtract(oldValue, bytes);
            return oldValue - bytes;
        });
    }

    @Override
    public SizeLimitTracker makeAtomic() {
        return this;
    }

    @Override
    public String toString() {
        return "SizeLimitTracker[Atomic: " + get() + "/" + limit + "]";
    }
}

final class Composite implements SizeLimitTracker {
    private final SizeLimitTracker a;
    private final SizeLimitTracker b;

    private Composite(SizeLimitTracker a, SizeLimitTracker b) {
        this.a = a;
        this.b = b;
    }

    static SizeLimitTracker compose(SizeLimitTracker a, SizeLimitTracker b) {
        if (a == Unlimited.INSTANCE) {
            return b;
        } else if (b == Unlimited.INSTANCE) {
            return a;
        } else {
            return new Composite(a, b);
        }
    }

    @Override
    public @Nullable Exception add(long bytes) {
        return addAtomic(a, b, bytes);
    }

    static @Nullable Exception addAtomic(SizeLimitTracker a, SizeLimitTracker b, long bytes) {
        Exception exc = a.add(bytes);
        if (exc != null) {
            return exc;
        }
        exc = b.add(bytes);
        if (exc != null) {
            a.subtract(bytes);
        }
        return exc;
    }

    @Override
    public void subtract(long bytes) {
        a.subtract(bytes);
        b.subtract(bytes);
    }

    @Override
    public SizeLimitTracker makeAtomic() {
        SizeLimitTracker a = this.a.makeAtomic();
        SizeLimitTracker b = this.b.makeAtomic();
        if (a == this.a && b == this.b) {
            return this;
        } else {
            return compose(a, b);
        }
    }

    @Override
    public String toString() {
        return "SizeLimitTracker[" + a + ", " + b + "]";
    }
}
