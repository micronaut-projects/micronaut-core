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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Internal;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * This class holds the {@link ThreadLocal} for the propagated context, or the
 * {@link FastThreadLocal netty alternative} for better performance on netty event loops, if
 * available.
 *
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Internal
@SuppressWarnings("unchecked")
final class ThreadContext {
    @Nullable
    private static final Object FAST;
    private static final ThreadLocal<PropagatedContext> SLOW = new ThreadLocal<>() {
        @Override
        public String toString() {
            return "Micronaut Propagation Context";
        }
    };

    private static final PropagatedContext.Scope CLEANUP = ThreadContext::clear;

    static {
        Object fast;
        try {
            fast = new FastThreadLocal<PropagatedContextImpl>();
        } catch (NoClassDefFoundError e) {
            fast = null;
        }
        FAST = fast;
    }

    private static boolean useSlow() {
        return FAST == null || !(Thread.currentThread() instanceof FastThreadLocalThread);
    }

    /**
     * Clears the context of the current thread. The slot is set to {@code null} rather than
     * removed: removing a {@link ThreadLocal} drops its map entry, so the next set allocates a
     * new one, and removing a {@link FastThreadLocal} unregisters it from the thread's
     * variables-to-remove set, so the next set registers it again. A request on an event loop
     * binds and clears the context several times.
     */
    private static void clear() {
        set(useSlow(), null);
    }

    @Nullable
    static PropagatedContext get() {
        return get(useSlow());
    }

    @Nullable
    @NullUnmarked
    private static PropagatedContext get(boolean slow) {
        if (slow) {
            return SLOW.get();
        } else {
            return ((FastThreadLocal<PropagatedContext>) FAST).getIfExists();
        }
    }

    @NullUnmarked
    private static void set(boolean slow, @Nullable PropagatedContext value) {
        if (slow) {
            SLOW.set(value);
        } else {
            ((FastThreadLocal<PropagatedContext>) FAST).set(value);
        }
    }

    static <T> T propagate(PropagatedContextImpl propagatedContext, Supplier<T> supplier) {
        boolean slow = useSlow();
        PropagatedContext prevCtx = get(slow);
        if (prevCtx == propagatedContext) {
            return supplier.get();
        }
        Object threadState = before(slow, prevCtx, propagatedContext);
        try {
            return supplier.get();
        } finally {
            after(slow, prevCtx, propagatedContext, threadState);
        }
    }

    static <T> T propagate(PropagatedContextImpl propagatedContext, Callable<T> callable) throws Exception {
        boolean slow = useSlow();
        PropagatedContext prevCtx = get(slow);
        if (prevCtx == propagatedContext) {
            return callable.call();
        }
        Object threadState = before(slow, prevCtx, propagatedContext);
        try {
            return callable.call();
        } finally {
            after(slow, prevCtx, propagatedContext, threadState);
        }
    }

    static void propagate(PropagatedContextImpl propagatedContext, Runnable runnable) {
        boolean slow = useSlow();
        PropagatedContext prevCtx = get(slow);
        if (prevCtx == propagatedContext) {
            runnable.run();
            return;
        }
        Object threadState = before(slow, prevCtx, propagatedContext);
        try {
            runnable.run();
        } finally {
            after(slow, prevCtx, propagatedContext, threadState);
        }
    }

    static PropagatedContext.Scope propagate(@Nullable PropagatedContext prevCtx, PropagatedContextImpl currentCtx) {
        boolean slow = useSlow();
        Object threadState = before(slow, prevCtx, currentCtx);
        if (prevCtx == null && !currentCtx.containsThreadElements) {
            return CLEANUP;
        }
        return new PropagatedContext.Scope() { // Don't convert to lambda for hot path execution
            @Override
            public void close() {
                after(useSlow(), prevCtx, currentCtx, threadState);
            }
        };
    }

    @Nullable
    private static Object before(boolean slow, @Nullable PropagatedContext prevCtx, PropagatedContextImpl currentCtx) {
        set(slow, currentCtx.elements.length == 0 ? null : currentCtx);
        if (currentCtx.containsThreadElements) {
            try {
                return PropagatedContextImpl.updateThreadState(currentCtx);
            } catch (RuntimeException | Error e) {
                // the caller only restores once the context is entered
                set(slow, prevCtx);
                throw e;
            }
        }
        return null;
    }

    private static void after(boolean slow,
                              @Nullable PropagatedContext prevCtx,
                              PropagatedContextImpl currentCtx,
                              @Nullable Object threadState) {
        if (currentCtx.containsThreadElements) {
            try {
                PropagatedContextImpl.restoreState(currentCtx, threadState);
            } finally {
                set(slow, prevCtx);
            }
        } else {
            set(slow, prevCtx);
        }
    }

}
