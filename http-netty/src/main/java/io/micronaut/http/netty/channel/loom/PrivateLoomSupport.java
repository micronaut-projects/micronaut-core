/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty.channel.loom;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.netty.util.internal.PlatformDependent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Access helpers to private virtual thread APIs.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
@Experimental
public final class PrivateLoomSupport {
    private static final MethodHandle DEFAULT_SCHEDULER;
    private static final MethodHandle BUILDER_SCHEDULER;
    private static final MethodHandle CARRIER_THREAD;
    private static final MethodHandle THREAD_SCHEDULER;

    private static final Throwable FAILURE;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle defaultScheduler;
        MethodHandle builderScheduler;
        MethodHandle threadScheduler;
        Throwable failure;
        try {
            Field defaultSchedulerField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("DEFAULT_SCHEDULER");
            defaultSchedulerField.setAccessible(true);
            defaultScheduler = lookup.unreflectGetter(defaultSchedulerField);

            Field schedulerField = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder")
                .getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            builderScheduler = lookup.unreflectSetter(schedulerField)
                    .asType(MethodType.methodType(void.class, Object.class, Executor.class));

            Field threadSchedulerField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("scheduler");
            threadSchedulerField.setAccessible(true);
            threadScheduler = lookup.unreflectGetter(threadSchedulerField)
                    .asType(MethodType.methodType(Executor.class, Thread.class));

            failure = null;
        } catch (ReflectiveOperationException | InaccessibleObjectException roe) {
            defaultScheduler = null;
            builderScheduler = null;
            threadScheduler = null;
            failure = roe;
        }
        DEFAULT_SCHEDULER = defaultScheduler;
        BUILDER_SCHEDULER = builderScheduler;
        THREAD_SCHEDULER = threadScheduler;
        FAILURE = failure;

        // carrierThread is also used for some LoomBranchSupport debugging, so let's initialize it separately
        MethodHandle carrierThread;
        try {
            Field carrierThreadField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("carrierThread");
            carrierThreadField.setAccessible(true);
            carrierThread = lookup.unreflectGetter(carrierThreadField)
                .asType(MethodType.methodType(Thread.class, Thread.class));
        } catch (Exception e) {
            carrierThread = null;
        }
        CARRIER_THREAD = carrierThread;
    }

    @NonNull
    static ForkJoinPool getDefaultScheduler() {
        try {
            return (ForkJoinPool) DEFAULT_SCHEDULER.invokeExact();
        } catch (Throwable e) {
            PlatformDependent.throwException(e);
            throw new AssertionError();
        }
    }

    public static void setScheduler(Object builder, Executor executor) {
        try {
            BUILDER_SCHEDULER.invokeExact(builder, executor);
        } catch (Throwable e) {
            PlatformDependent.throwException(e);
        }
    }

    public static Thread getCarrierThread(Thread t) {
        try {
            return (Thread) CARRIER_THREAD.invokeExact(t);
        } catch (Throwable e) {
            PlatformDependent.throwException(e);
            throw new AssertionError();
        }
    }

    public static Executor getScheduler(Thread t) {
        try {
            return (Executor) THREAD_SCHEDULER.invokeExact(t);
        } catch (Throwable e) {
            PlatformDependent.throwException(e);
            throw new AssertionError();
        }
    }

    public static boolean isSupported() {
        return THREAD_SCHEDULER != null;
    }

    static final class PrivateLoomCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            if (!isSupported() && !LoomBranchSupport.isSupported()) {
                context.fail("Failed to access loom internals. Please make sure to add the `--add-opens=java.base/java.lang=ALL-UNNAMED` JVM argument. (" + FAILURE + ")");
                return false;
            } else {
                return true;
            }
        }
    }
}
