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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.scheduling.LoomSupport;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Support for the <a href="https://github.com/openjdk/loom/">OpenJDK loom branch</a>.
 *
 * @since 4.10.0
 * @author Jonas Konrad
 */
@Internal
@Experimental
public final class LoomBranchSupport {
    private static final Supplier<VirtualThreadSchedulerProxy> DEFAULT_SCHEDULER;
    private static final Thread DEFAULT_SCHEDULER_THREAD;
    private static final MethodHandle TO_JDK_SCHEDULER;
    private static final MethodHandle TO_OUR_SCHEDULER;
    private static final MethodHandle CURRENT_SCHEDULER;
    private static final MethodHandle JDK_EXECUTE;
    private static final MethodHandle SET_SCHEDULER;
    private static final Class<?> PROXY_LAMBDA_CLASS;

    static {
        MethodHandle toJdkScheduler;
        MethodHandle toOurScheduler;
        MethodHandle currentScheduler;
        MethodHandle jdkExecute;
        MethodHandle setScheduler;
        try {
            MethodType executeMethodType = MethodType.methodType(void.class, Thread.class, Runnable.class);
            Class<?> jdkVirtualThreadScheduler = Class.forName("java.lang.Thread$VirtualThreadScheduler");

            currentScheduler = MethodHandles.lookup().findStatic(jdkVirtualThreadScheduler, "current", MethodType.methodType(jdkVirtualThreadScheduler))
                .asType(MethodType.methodType(Object.class));

            jdkExecute = MethodHandles.lookup().findVirtual(jdkVirtualThreadScheduler, "execute", executeMethodType);
            MethodHandle ourExecute = MethodHandles.lookup().findStatic(LoomBranchSupport.class, "executeOrUnwrap", MethodType.methodType(void.class, VirtualThreadSchedulerProxy.class, Thread.class, Runnable.class));
            toJdkScheduler = LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "execute",
                MethodType.methodType(jdkVirtualThreadScheduler, VirtualThreadSchedulerProxy.class),
                executeMethodType,
                ourExecute,
                executeMethodType
            ).dynamicInvoker()
                .asType(MethodType.methodType(Object.class, VirtualThreadSchedulerProxy.class));
            toOurScheduler = LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "execute",
                MethodType.methodType(VirtualThreadSchedulerProxy.class, jdkVirtualThreadScheduler),
                executeMethodType,
                jdkExecute,
                executeMethodType
            ).dynamicInvoker()
                .asType(MethodType.methodType(VirtualThreadSchedulerProxy.class, Object.class));
            Class<?> ofVirtual = Class.forName("java.lang.Thread$Builder$OfVirtual");
            setScheduler = MethodHandles.lookup().findVirtual(ofVirtual, "scheduler", MethodType.methodType(ofVirtual, jdkVirtualThreadScheduler))
                .asType(MethodType.methodType(void.class, Object.class, Object.class));
        } catch (Exception e) {
            jdkExecute = null;
            toJdkScheduler = null;
            toOurScheduler = null;
            currentScheduler = null;
            setScheduler = null;
        }

        TO_JDK_SCHEDULER = toJdkScheduler;
        TO_OUR_SCHEDULER = toOurScheduler;
        CURRENT_SCHEDULER = currentScheduler;
        JDK_EXECUTE = jdkExecute == null ? null : jdkExecute.asType(MethodType.methodType(void.class, Object.class, Thread.class, Runnable.class));
        SET_SCHEDULER = setScheduler;
        PROXY_LAMBDA_CLASS = TO_JDK_SCHEDULER != null ? toJdkScheduler((thread, runnable) -> {
        }).getClass() : null;

        if (isSupported()) {
            FutureTask<Object> task = new FutureTask<>(LoomBranchSupport::currentPrivate);
            DEFAULT_SCHEDULER_THREAD = LoomSupport.newVirtualThreadFactory("default-scheduler-exposer").newThread(task);
            DEFAULT_SCHEDULER_THREAD.start();
            DEFAULT_SCHEDULER = SupplierUtil.memoized(() -> {
                try {
                    return toOurScheduler(task.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            DEFAULT_SCHEDULER_THREAD = null;
            DEFAULT_SCHEDULER = null;
        }
    }

    public static boolean isSupported() {
        return TO_OUR_SCHEDULER != null;
    }

    static void runOnDefaultScheduler(Runnable r) {
        DEFAULT_SCHEDULER.get().execute(DEFAULT_SCHEDULER_THREAD, r);
    }

    static VirtualThreadSchedulerProxy currentScheduler() {
        return toOurScheduler(currentPrivate());
    }

    private static Object currentPrivate() {
        try {
            return CURRENT_SCHEDULER.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Transform the given VirtualThreadSchedulerProxy to a JDK VirtualThreadScheduler. The
     * delegate is implemented using LambdaMetafactory.
     *
     * @param proxy The scheduler implementation
     * @return The JDK delegate
     */
    static Object toJdkScheduler(VirtualThreadSchedulerProxy proxy) {
        try {
            return TO_JDK_SCHEDULER.invokeExact(proxy);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Transform the given JDK VirtualThreadScheduler to a {@link VirtualThreadSchedulerProxy}. If
     * the given scheduler was the result of {@link #toJdkScheduler}, the original proxy is
     * returned. Otherwise, a wrapper is created.
     *
     * @param scheduler The JDK scheduler
     * @return The scheduler proxy
     */
    static VirtualThreadSchedulerProxy toOurScheduler(Object scheduler) {
        if (scheduler.getClass() == PROXY_LAMBDA_CLASS) {
            UnwrapClass unwrapClass = new UnwrapClass();
            try {
                JDK_EXECUTE.invokeExact(scheduler, (Thread) null, (Runnable) unwrapClass);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            return unwrapClass.proxy;
        } else {
            try {
                return (VirtualThreadSchedulerProxy) TO_OUR_SCHEDULER.invokeExact(scheduler);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void setScheduler(Object builder, VirtualThreadSchedulerProxy scheduler) {
        try {
            SET_SCHEDULER.invokeExact(builder, toJdkScheduler(scheduler));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void executeOrUnwrap(VirtualThreadSchedulerProxy target, Thread thread, Runnable task) {
        if (task instanceof UnwrapClass uc) {
            uc.proxy = target;
            return;
        }
        target.execute(thread, task);
    }

    private static final class UnwrapClass implements Runnable {
        VirtualThreadSchedulerProxy proxy;

        @Override
        public void run() {
            throw new UnsupportedOperationException();
        }
    }

    interface VirtualThreadSchedulerProxy {
        void execute(Thread thread, Runnable task);
    }
}
