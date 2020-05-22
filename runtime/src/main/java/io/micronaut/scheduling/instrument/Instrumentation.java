/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Instrumentation represents an ongoing instrumentation with a given {@link InvocationInstrumenter} if any. When the
 * instance is returned to the caller it should already have executed {@link InvocationInstrumenter#beforeInvocation()}.
 * When assigning a newly instantiated instance in a try-with-resources block it will automatically execute
 * {@link InvocationInstrumenter#afterInvocation()} upon leaving the protected block.
 * <p/>
 * To force cleanup after the invocation, use the instance returned by {@link #forceCleanup()} instead, such as:
 * <p/>
 * <pre>
 * try (Instrumentation ignored = instrumenter.newInstrumentation().forceCleanup()) {
 *     ...
 * }
 * </pre>
 *
 * @author lgathy
 * @since 2.0
 */
public interface Instrumentation extends AutoCloseable {

    /**
     * @return {@code true} if and only if the given instrumentation is in progress, meaning that {@link
     * InvocationInstrumenter#beforeInvocation()} has already been invoked but {@link
     * InvocationInstrumenter#afterInvocation()} or {@link InvocationInstrumenter#afterInvocation(boolean)} has not yet
     * been called.
     */
    boolean isActive();

    /**
     * Closes the active instrumentation according to {@link InvocationInstrumenter#afterInvocation(boolean)}.
     *
     * @param cleanup Whether to enforce cleanup
     */
    void close(boolean cleanup);

    /**
     * Closes the active instrumentation as defined in {@link InvocationInstrumenter#afterInvocation()}.
     */
    @Override
    void close();

    /**
     * Return an instance which guarantees that cleanup will be forced to the instrumenter upon closing. The returned
     * instance will always invoke {@link InvocationInstrumenter#afterInvocation(boolean)} with {@code cleanup=true}
     * even if {@link #close(boolean)} gets called with {@code cleanup=false}
     *
     * @return a new instance which forces cleanup upon leaving the protected block.
     */
    default @NonNull Instrumentation forceCleanup() {
        return new Instrumentation() {

            @Override
            public boolean isActive() {
                return Instrumentation.this.isActive();
            }

            @Override
            public void close(boolean cleanup) {
                Instrumentation.this.close(true);
            }

            @Override
            public void close() {
                Instrumentation.this.close(true);
            }

            @Override
            public @NonNull Instrumentation forceCleanup() {
                return this;
            }
        };
    }

    /**
     * @return an instance which does no instrumentation
     */
    static @NonNull Instrumentation noop() {
        return NoopInstrumentation.INSTANCE;
    }
}
