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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The resources of a request that are released when its lifecycle ends.
 *
 * <p>The state is immutable: an open scope holds a persistent list of its resources, and
 * registration and release swap it atomically, so a resource is either detached by the release or
 * sees the scope released, never both or neither.</p>
 *
 * <ul>
 *     <li>{@link #release()} detaches all resources at once and releases them in registration
 *     order. Every cleanup runs even when one throws: the first failure is rethrown, with the later
 *     ones added to it as suppressed.</li>
 *     <li>A resource {@link #add(Runnable) added} after the release is released immediately, on
 *     the calling thread, and its failure propagates to the caller.</li>
 *     <li>Releasing again does nothing.</li>
 * </ul>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("java:S2055") // the reference is the state, extended to save an allocation per request
final class RequestResourceScope extends AtomicReference<RequestResourceScope.State> {

    RequestResourceScope() {
        super(Open.EMPTY);
    }

    /**
     * Add a resource, or release it immediately if this scope was already released.
     *
     * @param resource The cleanup task
     */
    void add(Runnable resource) {
        while (true) {
            State current = get();
            if (current instanceof Open open) {
                if (compareAndSet(open, new Open(resource, open))) {
                    return;
                }
            } else {
                resource.run();
                return;
            }
        }
    }

    /**
     * Release all resources. Does nothing if this scope was already released.
     */
    void release() {
        release(null);
    }

    /**
     * Release all resources, then rethrow the earlier failure, if any, with the failures of the
     * cleanups added to it as suppressed. If this scope was already released only the earlier
     * failure is rethrown.
     *
     * @param failure A failure of an earlier cleanup step, or {@code null}
     */
    void release(@Nullable Throwable failure) {
        if (getAndSet(Released.INSTANCE) instanceof Open open && open.size > 0) {
            Runnable[] resources = new Runnable[open.size];
            for (Open node = open; node.size > 0; node = node.previous) {
                resources[node.size - 1] = node.resource;
            }
            for (Runnable resource : resources) {
                try {
                    resource.run();
                } catch (Throwable t) {
                    failure = addFailure(failure, t);
                }
            }
        }
        rethrow(failure);
    }

    /**
     * Combine a cleanup failure with the earlier one.
     *
     * @param failure The earlier failure, or {@code null}
     * @param next    The new failure
     * @return The first failure, with any later one suppressed
     */
    @SuppressWarnings("ReferenceEquality")
    static Throwable addFailure(@Nullable Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private static void rethrow(@Nullable Throwable failure) {
        if (failure != null) {
            // rethrown as is, like the cleanup threw it, even a sneaky checked exception
            RequestResourceScope.<RuntimeException>sneakyThrow(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
    }

    sealed interface State permits Open, Released {
    }

    /**
     * An open scope: the last registered resource and the scope before it. The empty scope
     * holds a no-op and points to itself.
     */
    static final class Open implements State {
        static final Open EMPTY = new Open();

        private final int size;
        private final Runnable resource;
        private final Open previous;

        private Open() {
            this.size = 0;
            this.resource = () -> {
            };
            this.previous = this;
        }

        private Open(Runnable resource, Open previous) {
            this.size = previous.size + 1;
            this.resource = resource;
            this.previous = previous;
        }
    }

    enum Released implements State {
        INSTANCE
    }
}
