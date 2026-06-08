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

    private static final PropagatedContext.Scope CLEANUP = ThreadContext::remove;

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

    @NullUnmarked
    private static void remove() {
        if (useSlow()) {
            SLOW.remove();
        } else {
            ((FastThreadLocal<PropagatedContext>) FAST).remove();
        }
    }

    @Nullable
    @NullUnmarked
    static PropagatedContext get() {
        if (useSlow()) {
            return SLOW.get();
        } else {
            return ((FastThreadLocal<PropagatedContext>) FAST).getIfExists();
        }
    }

    @NullUnmarked
    private static void set(PropagatedContext value) {
        if (useSlow()) {
            SLOW.set(value);
        } else {
            ((FastThreadLocal<PropagatedContext>) FAST).set(value);
        }
    }

    static <T> T propagate(PropagatedContextImpl propagatedContext, Supplier<T> supplier) {
        PropagatedContext prevCtx = get();
        if (prevCtx == propagatedContext) {
            return supplier.get();
        }
        PropagatedContextImpl.ThreadState[] threadStates = before(propagatedContext);
        try {
            return supplier.get();
        } finally {
            after(prevCtx, threadStates);
        }
    }

    static <T> T propagate(PropagatedContextImpl propagatedContext, Callable<T> callable) throws Exception {
        PropagatedContext prevCtx = get();
        if (prevCtx == propagatedContext) {
            return callable.call();
        }
        PropagatedContextImpl.ThreadState[] threadStates = before(propagatedContext);
        try {
            return callable.call();
        } finally {
            after(prevCtx, threadStates);
        }
    }

    static void propagate(PropagatedContextImpl propagatedContext, Runnable runnable) {
        PropagatedContext prevCtx = get();
        if (prevCtx == propagatedContext) {
            runnable.run();
            return;
        }
        PropagatedContextImpl.ThreadState[] threadStates = before(propagatedContext);
        try {
            runnable.run();
        } finally {
            after(prevCtx, threadStates);
        }
    }

    static PropagatedContext.Scope propagate(@Nullable PropagatedContext prevCtx, PropagatedContextImpl currentCtx) {
        PropagatedContextImpl.ThreadState[] threadStates = before(currentCtx);
        if (prevCtx == null && threadStates == null) {
            return CLEANUP;
        }
        return new PropagatedContext.Scope() { // Don't convert to lambda for hot path execution
            @Override
            public void close() {
                after(prevCtx, threadStates);
            }
        };
    }

    static PropagatedContextImpl.ThreadState @Nullable [] before(PropagatedContextImpl currentCtx) {
        if (currentCtx.elements.length == 0) {
            ThreadContext.remove();
        } else {
            ThreadContext.set(currentCtx);
        }
        if (currentCtx.containsThreadElements) {
            return PropagatedContextImpl.updateThreadState(currentCtx);
        }
        return null;
    }

    static void after(@Nullable PropagatedContext prevCtx, PropagatedContextImpl.ThreadState @Nullable [] threadState) {
        if (threadState != null) {
            PropagatedContextImpl.restoreState(threadState);
        }
        if (prevCtx == null) {
            ThreadContext.remove();
        } else {
            ThreadContext.set(prevCtx);
        }
    }

}
