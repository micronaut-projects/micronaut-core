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
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@Internal
@Experimental
public final class PrivateLoomSupport {
    private static final MethodHandle DEFAULT_SCHEDULER;
    private static final MethodHandle SCHEDULER;
    private static final MethodHandle CARRIER_THREAD;

    private static final Throwable FAILURE;

    static {
        MethodHandle defaultScheduler;
        MethodHandle scheduler;
        MethodHandle carrierThread;
        Throwable failure;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Field defaultSchedulerField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("DEFAULT_SCHEDULER");
            defaultSchedulerField.setAccessible(true);
            defaultScheduler = lookup.unreflectGetter(defaultSchedulerField);

            Field schedulerField = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder")
                .getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            scheduler = lookup.unreflectSetter(schedulerField);

            Field carrierThreadField = Class.forName("java.lang.VirtualThread")
                .getDeclaredField("carrierThread");
            carrierThreadField.setAccessible(true);
            carrierThread = lookup.unreflectGetter(carrierThreadField);

            failure = null;
        } catch (ReflectiveOperationException | InaccessibleObjectException roe) {
            defaultScheduler = null;
            scheduler = null;
            carrierThread = null;
            failure = roe;
        }
        DEFAULT_SCHEDULER = defaultScheduler;
        SCHEDULER = scheduler;
        CARRIER_THREAD = carrierThread;
        FAILURE = failure;
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

    static void setScheduler(Object builder, Executor executor) {
        try {
            SCHEDULER.invoke(builder, executor);
        } catch (Throwable e) {
            PlatformDependent.throwException(e);
        }
    }

    public static Thread getCarrierThread() {
        Thread t = Thread.currentThread();
        try {
            return (Thread) CARRIER_THREAD.invoke(t);
        } catch (Throwable e) {
            PlatformDependent.throwException(e);
            throw new AssertionError();
        }
    }

    static final class PrivateLoomCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            if (SCHEDULER == null) {
                context.fail("Failed to access loom internals: " + FAILURE);
                return false;
            } else {
                return true;
            }
        }
    }
}
