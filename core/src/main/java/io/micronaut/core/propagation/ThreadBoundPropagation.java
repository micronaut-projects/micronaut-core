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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Binds a {@link PropagatedContext} to the current thread with one call and unbinds it with a later, separate call,
 * in both propagation modes.
 *
 * <p>This is meant for integrations that are handed a set/restore pair of callbacks and cannot wrap the code in
 * between, such as a Kotlin {@code ThreadContextElement} that is updated every time a coroutine is resumed on a
 * thread and restored when it suspends.</p>
 *
 * <p>With thread-local propagation it is equivalent to {@link PropagatedContext#propagate()}. With scoped-value
 * propagation a {@link ScopedValue} can't be bound across separate calls, so the context is kept in a thread-local
 * that {@link PropagatedContext#get()} looks up before the scoped value, and the
 * {@link ThreadPropagatedContextElement thread elements} are updated. A context propagated with a callback such as
 * {@link PropagatedContext#propagate(java.util.function.Supplier)} while one is bound to the thread takes precedence
 * for the extent of the callback. The {@link ScopedValuePropagatedContextElement scoped value elements} of a context
 * bound to the thread are not bound, they are only bound when the context is propagated with a callback.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class ThreadBoundPropagation {

    /**
     * Set by the first thread that binds a context with scoped-value propagation, before it binds it. A thread that
     * hasn't bound a context doesn't need to see the write, so this doesn't need to be volatile.
     */
    private static boolean used;

    private ThreadBoundPropagation() {
    }

    /**
     * Brings the given context into scope on the current thread, temporarily replacing the previous context
     * (if any). The returned scope must be closed on the same thread.
     *
     * @param propagatedContext The context
     * @return The scope that restores the previous context
     */
    @SuppressWarnings("deprecation")
    public static PropagatedContext.Scope bind(PropagatedContext propagatedContext) {
        return switch (PropagatedContextConfiguration.get()) {
            case THREAD_LOCAL -> propagatedContext.propagate();
            case SCOPED_VALUE -> bindScopedValueMode(propagatedContext);
        };
    }

    private static PropagatedContext.Scope bindScopedValueMode(PropagatedContext propagatedContext) {
        used = true;
        PropagatedContext previous = ThreadContext.get();
        ThreadContext.set(propagatedContext);
        PropagatedContextImpl.ThreadState[] threadStates = null;
        if (propagatedContext instanceof PropagatedContextImpl impl && impl.containsThreadElements) {
            threadStates = PropagatedContextImpl.updateThreadState(impl);
        }
        PropagatedContextImpl.ThreadState[] finalThreadStates = threadStates;
        return new PropagatedContext.Scope() { // Don't convert to lambda for hot path execution
            @Override
            public void close() {
                if (finalThreadStates != null) {
                    PropagatedContextImpl.restoreState(finalThreadStates);
                }
                if (previous == null) {
                    ThreadContext.remove();
                } else {
                    ThreadContext.set(previous);
                }
            }
        };
    }

    /**
     * @return The context bound to the current thread with scoped-value propagation, if any
     */
    static @Nullable PropagatedContext get() {
        return used ? ThreadContext.get() : null;
    }

    /**
     * Unbinds the context bound to the current thread with scoped-value propagation, so that a scoped value binding
     * takes precedence.
     *
     * @return The context to pass to {@link #resume(PropagatedContext)}
     */
    static @Nullable PropagatedContext suspend() {
        PropagatedContext threadBound = get();
        if (threadBound != null) {
            ThreadContext.remove();
        }
        return threadBound;
    }

    /**
     * Binds again a context returned by {@link #suspend()}.
     *
     * @param threadBound The context, if any
     */
    static void resume(@Nullable PropagatedContext threadBound) {
        if (threadBound != null) {
            ThreadContext.set(threadBound);
        }
    }
}
