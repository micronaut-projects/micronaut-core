/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.scheduling;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullUnmarked;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Virtual thread support from before virtual threads were part of every Java version Micronaut runs on.
 *
 * @since 4.0.0
 * @deprecated Virtual threads are always available, use {@link Thread#ofVirtual()},
 * {@link Executors#newThreadPerTaskExecutor(ThreadFactory)} and {@link Thread#isVirtual()} directly.
 * To be removed, see <a href="https://github.com/micronaut-projects/micronaut-core/issues/13119">#13119</a>.
 */
@Internal
@NullUnmarked
@Deprecated(since = "5.2.1", forRemoval = true)
public final class LoomSupport {

    private LoomSupport() {
    }

    /**
     * @return Always true
     */
    public static boolean isSupported() {
        return true;
    }

    /**
     * Does nothing, virtual threads are always supported.
     */
    public static void checkSupported() {
        // virtual threads are always supported
    }

    /**
     * @param namePrefix      The thread name prefix, followed by a counter starting at 1
     * @param builderModifier Modifies the {@link Thread.Builder.OfVirtual}, may be null
     * @return A factory of virtual threads
     */
    @Experimental
    public static ThreadFactory newVirtualThreadFactory(String namePrefix, Consumer<Object> builderModifier) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual().name(namePrefix, 1L);
        if (builderModifier != null) {
            builderModifier.accept(builder);
        }
        return builder.factory();
    }

    /**
     * @param name            The thread name
     * @param builderModifier Modifies the {@link Thread.Builder.OfVirtual}, may be null
     * @param task            The task
     * @return An unstarted virtual thread
     */
    @Experimental
    public static Thread unstarted(String name, Consumer<Object> builderModifier, Runnable task) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual().name(name);
        if (builderModifier != null) {
            builderModifier.accept(builder);
        }
        return builder.unstarted(task);
    }

    /**
     * @param threadFactory The thread factory
     * @return An executor starting a thread per task
     */
    public static ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }

    /**
     * @param namePrefix The thread name prefix, followed by a counter starting at 1
     * @return A factory of virtual threads
     */
    public static ThreadFactory newVirtualThreadFactory(String namePrefix) {
        return newVirtualThreadFactory(namePrefix, null);
    }

    /**
     * @param thread The thread
     * @return Whether the thread is virtual
     */
    public static boolean isVirtual(Thread thread) {
        return thread.isVirtual();
    }

    /**
     * Condition that always matches, virtual threads are always supported.
     *
     * @deprecated Virtual threads are always supported, to be removed, see
     * <a href="https://github.com/micronaut-projects/micronaut-core/issues/13119">#13119</a>.
     */
    @Internal
    @Deprecated(since = "5.2.1", forRemoval = true)
    public static class LoomCondition implements Condition {
        @SuppressWarnings("rawtypes")
        @Override
        public boolean matches(ConditionContext context) {
            return true;
        }
    }
}
