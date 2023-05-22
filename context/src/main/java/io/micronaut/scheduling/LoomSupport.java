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
import io.micronaut.core.annotation.Internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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
    private static final MethodHandle MH_FACTORY;

    static {
        boolean sup;
        MethodHandle newThreadPerTaskExecutor;
        MethodHandle ofVirtual;
        MethodHandle name;
        MethodHandle factory;
        try {
            newThreadPerTaskExecutor = MethodHandles.lookup()
                .findStatic(Executors.class, "newThreadPerTaskExecutor", MethodType.methodType(ExecutorService.class, ThreadFactory.class));
            Class<?> builderCl = Class.forName("java.lang.Thread$Builder");
            Class<?> ofVirtualCl = Class.forName("java.lang.Thread$Builder$OfVirtual");
            ofVirtual = MethodHandles.lookup()
                .findStatic(Thread.class, "ofVirtual", MethodType.methodType(ofVirtualCl));
            name = MethodHandles.lookup()
                .findVirtual(builderCl, "name", MethodType.methodType(builderCl, String.class, long.class));
            factory = MethodHandles.lookup()
                .findVirtual(builderCl, "factory", MethodType.methodType(ThreadFactory.class));

            // invoke, this will throw an UnsupportedOperationException if we don't have --enable-preview
            ofVirtual.invoke();

            sup = true;
        } catch (Throwable e) {
            newThreadPerTaskExecutor = null;
            ofVirtual = null;
            name = null;
            factory = null;
            sup = false;
            failure = e;
        }

        SUPPORTED = sup;
        MH_NEW_THREAD_PER_TASK_EXECUTOR = newThreadPerTaskExecutor;
        MH_OF_VIRTUAL = ofVirtual;
        MH_NAME = name;
        MH_FACTORY = factory;
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

    public static ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
        checkSupported();
        try {
            return (ExecutorService) MH_NEW_THREAD_PER_TASK_EXECUTOR.invokeExact(threadFactory);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static ThreadFactory newVirtualThreadFactory(String namePrefix) {
        checkSupported();
        try {
            Object builder = MH_OF_VIRTUAL.invoke();
            builder = MH_NAME.invoke(builder, namePrefix, 1L);
            return (ThreadFactory) MH_FACTORY.invoke(builder);
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
