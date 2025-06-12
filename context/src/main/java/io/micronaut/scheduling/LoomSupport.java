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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * @since 4.0.0
 */
@Internal
public final class LoomSupport {
    private static final boolean SUPPORTED;
    private static Throwable failure;

    private static final MethodHandle MH_NEW_THREAD_PER_TASK_EXECUTOR;
    private static final MethodHandle MH_OF_VIRTUAL;
    private static final MethodHandle MH_NAME;
    private static final MethodHandle MH_NAME_COUNT;
    private static final MethodHandle MH_FACTORY;
    private static final MethodHandle MH_UNSTARTED;
    private static final MethodHandle MH_IS_VIRTUAL;

    static {
        boolean sup;
        MethodHandle newThreadPerTaskExecutor;
        MethodHandle ofVirtual;
        MethodHandle name;
        MethodHandle nameCount;
        MethodHandle factory;
        MethodHandle unstarted;
        MethodHandle isVirtual;
        try {
            newThreadPerTaskExecutor = MethodHandles.lookup()
                .findStatic(Executors.class, "newThreadPerTaskExecutor", MethodType.methodType(ExecutorService.class, ThreadFactory.class));
            Class<?> builderCl = Class.forName("java.lang.Thread$Builder");
            Class<?> ofVirtualCl = Class.forName("java.lang.Thread$Builder$OfVirtual");
            ofVirtual = MethodHandles.lookup()
                .findStatic(Thread.class, "ofVirtual", MethodType.methodType(ofVirtualCl));
            name = MethodHandles.lookup()
                .findVirtual(builderCl, "name", MethodType.methodType(builderCl, String.class));
            nameCount = MethodHandles.lookup()
                .findVirtual(builderCl, "name", MethodType.methodType(builderCl, String.class, long.class));
            factory = MethodHandles.lookup()
                .findVirtual(builderCl, "factory", MethodType.methodType(ThreadFactory.class));
            unstarted = MethodHandles.lookup()
                .findVirtual(builderCl, "unstarted", MethodType.methodType(Thread.class, Runnable.class));
            isVirtual = MethodHandles.lookup()
                .findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));

            // This will throw if this Java doesn't support Loom, or if it does but only with
            // --enable-preview.
            Thread probe = (Thread) unstarted.invoke(ofVirtual.invoke(), (Runnable) () -> { });

            // This checks if the JVM actually creates real virtual threads, or if it uses
            // 'bound threads' which are just platform threads. As of June 2025 the Espresso JVM
            // falls into this category. Checking here voids casting issues later in code that
            // makes assumptions about the internals.
            sup = Class.forName("java.lang.VirtualThread").isInstance(probe);
            if (!sup) {
                failure = new Exception("This JVM doesn't fully implement virtual threads and produces regular platform threads instead.");
            }
        } catch (Throwable e) {
            newThreadPerTaskExecutor = null;
            ofVirtual = null;
            name = null;
            nameCount = null;
            factory = null;
            unstarted = null;
            isVirtual = null;
            sup = false;
            failure = e;
        }

        SUPPORTED = sup;
        MH_NEW_THREAD_PER_TASK_EXECUTOR = newThreadPerTaskExecutor;
        MH_OF_VIRTUAL = ofVirtual;
        MH_NAME = name;
        MH_NAME_COUNT = nameCount;
        MH_FACTORY = factory;
        MH_UNSTARTED = unstarted;
        MH_IS_VIRTUAL = isVirtual;
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
            Object builder = MH_OF_VIRTUAL.invoke();
            builder = MH_NAME_COUNT.invoke(builder, namePrefix, 1L);
            if (builderModifier != null) {
                builderModifier.accept(builder);
            }
            return (ThreadFactory) MH_FACTORY.invoke(builder);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Experimental
    public static Thread unstarted(String name, Consumer<Object> builderModifier, Runnable task) {
        checkSupported();
        try {
            Object builder = MH_OF_VIRTUAL.invoke();
            builder = MH_NAME.invoke(builder, name);
            if (builderModifier != null) {
                builderModifier.accept(builder);
            }
            return (Thread) MH_UNSTARTED.invoke(builder, task);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
        checkSupported();
        try {
            return (ExecutorService) MH_NEW_THREAD_PER_TASK_EXECUTOR.invokeExact(threadFactory);
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
            return (boolean) MH_IS_VIRTUAL.invokeExact(thread);
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
