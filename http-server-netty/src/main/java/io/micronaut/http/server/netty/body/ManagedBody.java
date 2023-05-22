/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Standard implementation of {@link HttpBody} that contains a single value that can be claimed
 * once.
 *
 * @param <T> The value type
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
abstract class ManagedBody<T> implements HttpBody {
    private final T value;
    private boolean claimed;
    private HttpBody next;

    ManagedBody(T value) {
        this.value = value;
    }

    /**
     * Get the value without claiming it.<br>
     * Note: When the value has been claimed already, this method may be unsafe, because the value
     * may be modified concurrently or may be released (for ref counted objects). Handle with care.
     *
     * @return The value
     */
    final T value() {
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
