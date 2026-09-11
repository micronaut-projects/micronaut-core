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
 * @since 4.0.0
 */
@Internal
@NullUnmarked
public final class LoomSupport {
    private static final boolean SUPPORTED;
    private static Throwable failure;

    static {
        boolean sup;
        try {
            // This will throw if this JVM cannot create virtual threads.
            Thread probe = Thread.ofVirtual().unstarted(() -> { });

            // This checks if the JVM actually creates real virtual threads, or if it uses
            // 'bound threads' which are just platform threads. As of June 2025 the Espresso JVM
            // falls into this category. Checking here voids casting issues later in code that
            // makes assumptions about the internals.
            sup = Class.forName("java.lang.VirtualThread").isInstance(probe);
            if (!sup) {
                failure = new Exception("This JVM doesn't fully implement virtual threads and produces regular platform threads instead.");
            }
        } catch (Throwable e) {
            sup = false;
            failure = e;
        }

        SUPPORTED = sup;
    }

    private LoomSupport() {
    }

    public static boolean isSupported() {
        return SUPPORTED;
    }

    public static void checkSupported() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Virtual threads are not supported on this JVM, you may have to pass --enable-preview", failure);
        }
    }

    @Experimental
    public static ThreadFactory newVirtualThreadFactory(String namePrefix, Consumer<Object> builderModifier) {
        checkSupported();
        try {
            Thread.Builder.OfVirtual builder = Thread.ofVirtual().name(namePrefix, 1L);
            if (builderModifier != null) {
                builderModifier.accept(builder);
            }
            return builder.factory();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Experimental
    public static Thread unstarted(String name, Consumer<Object> builderModifier, Runnable task) {
        checkSupported();
        try {
            Thread.Builder.OfVirtual builder = Thread.ofVirtual().name(name);
            if (builderModifier != null) {
                builderModifier.accept(builder);
            }
            return builder.unstarted(task);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
        checkSupported();
        try {
            return Executors.newThreadPerTaskExecutor(threadFactory);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static ThreadFactory newVirtualThreadFactory(String namePrefix) {
        return newVirtualThreadFactory(namePrefix, null);
    }

    public static boolean isVirtual(Thread thread) {
        if (!isSupported()) {
            // reasonable default.
            return false;
        }
        try {
            return thread.isVirtual();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Condition that only matches if virtual threads are supported on this platform.
     */
    @Internal
    public static class LoomCondition implements Condition {
        @SuppressWarnings("rawtypes")
        @Override
        public boolean matches(ConditionContext context) {
            if (isSupported()) {
                return true;
            } else {
                context.fail("Virtual threads support not available: " + failure.getMessage());
                return false;
            }
        }
    }
}
