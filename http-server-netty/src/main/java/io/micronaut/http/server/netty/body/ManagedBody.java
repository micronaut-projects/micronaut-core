package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Standard implementation of {@link HttpBody} that contains a single value that can be claimed
 * once.
 *
 * @param <T> The value type
 */
@Internal
abstract class ManagedBody<T> implements HttpBody {
    private final T value;
    private boolean claimed;
    private HttpBody next;

    public ManagedBody(T value) {
        this.value = value;
    }

    /**
     * Get the value without claiming it.
     *
     * @return The value
     * @throws IllegalStateException if the value has already been claimed
     */
    final T value() {
        if (claimed) {
            throw new IllegalStateException("Already claimed");
        }
        return value;
    }

    /**
     * Get and claim the value.
     *
     * @return The value
     * @throws IllegalStateException if the value has already been claimed
     */
    final T claim() {
        if (claimed) {
            throw new IllegalStateException("Already claimed");
        }
        claimed = true;
        return value;
    }

    /**
     * Prepare to claim this value. The actual claim is done by {@link #next(HttpBody)}. This makes
     * sure that if there is an exception between prepareClaim and {@link #next(HttpBody)}, this
     * {@link HttpBody} actually retains ownership and releases the value on {@link #release()}.
     *
     * @return The value that will be claimed
     */
    final T prepareClaim() {
        if (claimed) {
            throw new IllegalStateException("Already claimed");
        }
        return value;
    }

    /**
     * Claim this value and set the next {@link HttpBody}. This operation must be preceded by a
     * call to {@link #prepareClaim()}.
     *
     * @param next The next body that takes responsibility of our data
     * @return The {@code next} parameter, for easy chaining
     * @param <B> The body type
     */
    final <B extends HttpBody> B next(B next) {
        if (claimed) {
            throw new AssertionError("Should have called prepareClaim");
        }
        this.next = next;
        claim();
        return next;
    }

    @Override
    public final void release() {
        if (!claimed) {
            release(value);
        } else if (next != null) {
            next.release();
        }
    }

    /**
     * Release the given value. Only called by {@link #release()} if the value is still unclaimed.
     *
     * @param value The value to release
     */
    abstract void release(T value);

    @Nullable
    @Override
    public HttpBody next() {
        return next;
    }
}
