/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling.instrument;

import io.micronaut.core.util.CollectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * An interface for invocation instrumentation.
 *
 * @author Denis Stepanov
 * @author graemerocher
 * @since 1.3
 */
public interface InvocationInstrumenter {

    /**
     * Noop implementation if {@link InvocationInstrumenter}.
     */
    InvocationInstrumenter NOOP = new InvocationInstrumenter() {
        @Override
        public void beforeInvocation() {
        }

        @Override
        public void afterInvocation() {
        }
    };

    /**
     * Before call.
     */
    void beforeInvocation();

    /**
     * After call.
     */
    void afterInvocation();

    /**
     * Combines multiple instrumenters into one.
     *
     * @param invocationInstrumenters instrumenters to combine
     * @return new instrumenter
     */
    static @NonNull InvocationInstrumenter combine(Collection<InvocationInstrumenter> invocationInstrumenters) {
        if (CollectionUtils.isEmpty(invocationInstrumenters)) {
            return NOOP;
        }
        if (invocationInstrumenters.size() == 1) {
            return invocationInstrumenters.iterator().next();
        }
        return new MultipleInvocationInstrumenter(invocationInstrumenters);
    }

    /**
     * Wrappers {@link Runnable} with instrumentation invocations.
     *
     * @param runnable                {@link Runnable} to be wrapped
     * @param invocationInstrumenters instrumenters to be used
     * @return wrapper
     */
    static @NonNull Runnable instrument(@NonNull Runnable runnable, Collection<InvocationInstrumenter> invocationInstrumenters) {
        if (CollectionUtils.isEmpty(invocationInstrumenters)) {
            return runnable;
        }
        return instrument(runnable, combine(invocationInstrumenters));
    }


    /**
     * Wrappers {@link Callable} with instrumentation invocations.
     *
     * @param callable                {@link Callable} to be wrapped
     * @param invocationInstrumenters instrumenters to be used
     * @param <V>                     callable generic param
     * @return wrapper
     */
    static @NonNull <V> Callable<V> instrument(@NonNull Callable<V> callable, Collection<InvocationInstrumenter> invocationInstrumenters) {
        if (CollectionUtils.isEmpty(invocationInstrumenters)) {
            return callable;
        }
        return instrument(callable, combine(invocationInstrumenters));
    }

    /**
     * Wrappers {@link Runnable} with instrumentation invocations.
     *
     * @param runnable               {@link Runnable} to be wrapped
     * @param invocationInstrumenter instrumenter to be used
     * @return wrapper
     */
    static @NonNull Runnable instrument(@NonNull Runnable runnable, InvocationInstrumenter invocationInstrumenter) {
        if (runnable instanceof InvocationInstrumenterWrappedRunnable) {
            return runnable;
        }
        return new InvocationInstrumenterWrappedRunnable(invocationInstrumenter, runnable);
    }

    /**
     * Wrappers {@link Callable} with instrumentation invocations.
     *
     * @param callable               {@link Callable} to be wrapped
     * @param invocationInstrumenter instrumenter to be used
     * @param <V>                    callable generic param
     * @return wrapper
     */
    static @NonNull <V> Callable<V> instrument(@NonNull Callable<V> callable, InvocationInstrumenter invocationInstrumenter) {
        if (callable instanceof InvocationInstrumenterWrappedCallable) {
            return callable;
        }
        return new InvocationInstrumenterWrappedCallable<>(invocationInstrumenter, callable);
    }

}
