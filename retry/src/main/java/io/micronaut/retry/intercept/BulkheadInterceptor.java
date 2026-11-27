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
package io.micronaut.retry.intercept;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.watch.BeanExecutableMethod;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.annotation.Bulkhead;
import io.micronaut.retry.exception.BulkheadFullException;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A {@link MethodInterceptor} that limits how many calls of a method run at the same time, as its
 * {@link Bulkhead} annotation says.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
@Singleton
public final class BulkheadInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * Inside the retries and the time limit, outside whatever hands the call to another thread.
     */
    public static final int POSITION = InterceptPhase.RETRY.getPosition() + 10;

    private final ConversionService conversionService;
    private final ScheduledExecutorService scheduler;
    private final NamedBulkheads named;
    /**
     * The bulkheads of the methods without a name, by the class of the bean and the method: methods of
     * different beans can share an {@link ExecutableMethod} that is equal, e.g. an inherited one.
     */
    private final Map<Key, BulkheadPermits> unnamed = new ConcurrentHashMap<>();
    private final Function<ExecutableMethod<?, ?>, Plan> planOf;

    /**
     * @param conversionService The conversion service
     * @param executorService The executor that ends the wait of a call that waits without a thread
     * @param named The bulkheads that have a name
     * @param beanContext The bean context
     */
    public BulkheadInterceptor(ConversionService conversionService,
                               @Named(TaskExecutors.SCHEDULED) ExecutorService executorService,
                               NamedBulkheads named,
                               @Nullable BeanContext beanContext) {
        this.conversionService = conversionService;
        this.scheduler = (ScheduledExecutorService) executorService;
        this.named = named;
        this.planOf = method -> Plan.of(method, conversionService);
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.methods(Bulkhead.class).watch(change -> {
                // a bulkhead without a name belongs to its method, and goes with it
                for (BeanExecutableMethod<Bulkhead> gone : change.removed()) {
                    unnamed.keySet().removeIf(key -> key.method().equals(gone.method()));
                }
            });
        }
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Plan plan;
        try {
            plan = context.getCachedValue(Plan.class, planOf);
        } catch (IllegalStateException e) {
            return InterceptedMethod.of(context, conversionService).handleException(e);
        }
        if (plan.maxConcurrent() == Plan.NONE) {
            return context.proceed();
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            BulkheadPermits permits = permits(plan, context);
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    return interceptedMethod.handleResult(publisher(permits, plan, interceptedMethod));
                }
                case COMPLETION_STAGE -> {
                    return interceptedMethod.handleResult(completionStage(permits, plan, interceptedMethod));
                }
                case SYNCHRONOUS -> {
                    return synchronous(permits, plan, interceptedMethod);
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private BulkheadPermits permits(Plan plan, MethodInvocationContext<Object, Object> context) {
        if (plan.name().isEmpty()) {
            return unnamed.computeIfAbsent(
                new Key(context.getTarget().getClass(), context.getExecutableMethod()),
                key -> new BulkheadPermits(user(context), plan.maxConcurrent(), user(context))
            );
        }
        return named.join(plan.name(), plan.maxConcurrent(), "@Bulkhead of " + user(context));
    }

    private static String user(MethodInvocationContext<Object, Object> context) {
        return context.getDeclaringType().getName() + "#" + context.getMethodName();
    }

    @Nullable
    private Object synchronous(BulkheadPermits permits, Plan plan, InterceptedMethod interceptedMethod) {
        CompletableFuture<Boolean> place = permits.enter();
        if (!place.isDone() && !waited(permits, place, plan.maxWaitNanos())) {
            throw full(permits);
        }
        try {
            return interceptedMethod.interceptResult(this);
        } finally {
            permits.leave();
        }
    }

    /**
     * Waits for a place on the thread of the call.
     */
    private static boolean waited(BulkheadPermits permits, CompletableFuture<Boolean> place, long maxWaitNanos) {
        if (maxWaitNanos > 0) {
            try {
                return place.get(maxWaitNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException | ExecutionException e) {
                // gives up below
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return !permits.giveUp(place);
    }

    /**
     * Asks for a place without waiting on a thread.
     *
     * @return A future that says whether the call has a place, at the latest when its wait is over
     */
    private CompletableFuture<Boolean> place(BulkheadPermits permits, Plan plan) {
        CompletableFuture<Boolean> place = permits.enter();
        if (!place.isDone()) {
            if (plan.maxWaitNanos() <= 0) {
                permits.giveUp(place);
            } else {
                ScheduledFuture<?> end = scheduler.schedule(() -> {
                    permits.giveUp(place);
                }, plan.maxWaitNanos(), TimeUnit.NANOSECONDS);
                place.whenComplete((admitted, error) -> end.cancel(false));
            }
        }
        return place;
    }

    private CompletionStage<Object> completionStage(BulkheadPermits permits, Plan plan, InterceptedMethod interceptedMethod) {
        CompletableFuture<Boolean> place = place(permits, plan);
        CompletableFuture<Object> result = new CompletableFuture<>();
        // a call that waited goes on when another leaves, on the thread of that one, with the context of its own
        PropagatedContext propagated = place.isDone() ? null : PropagatedContext.getOrEmpty();
        if (propagated != null) {
            // a caller that no longer waits for the result, e.g. one whose time limit ended, no longer waits for a place
            result.whenComplete((value, failure) -> permits.giveUp(place));
        }
        place.whenComplete((admitted, error) -> {
            if (!Boolean.TRUE.equals(admitted)) {
                result.completeExceptionally(full(permits));
                return;
            }
            if (result.isDone()) {
                permits.leave();
                return;
            }
            CompletionStage<?> stage;
            try {
                if (propagated == null) {
                    stage = interceptedMethod.interceptResultAsCompletionStage(this);
                } else {
                    stage = propagated.propagate(() -> interceptedMethod.interceptResultAsCompletionStage(this));
                }
            } catch (Throwable e) {
                permits.leave();
                result.completeExceptionally(e);
                return;
            }
            stage.whenComplete((value, failure) -> {
                permits.leave();
                if (failure == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(failure);
                }
            });
        });
        return result;
    }

    private Publisher<Object> publisher(BulkheadPermits permits, Plan plan, InterceptedMethod interceptedMethod) {
        return Flux.defer(() -> {
            CompletableFuture<Boolean> place = place(permits, plan);
            PropagatedContext propagated = place.isDone() ? null : PropagatedContext.getOrEmpty();
            // a place handed to a subscriber that cancelled meanwhile is used by nobody: one of the two takes it
            AtomicBoolean taken = new AtomicBoolean();
            return Mono.fromFuture(place, true)
                .doOnCancel(() -> {
                    if (!permits.giveUp(place) && taken.compareAndSet(false, true)) {
                        permits.leave();
                    }
                })
                .flatMapMany(admitted -> {
                    if (!admitted) {
                        return Flux.error(full(permits));
                    }
                    if (!taken.compareAndSet(false, true)) {
                        return Flux.empty();
                    }
                    Publisher<?> publisher;
                    try {
                        if (propagated == null) {
                            publisher = interceptedMethod.interceptResultAsPublisher(this);
                        } else {
                            publisher = propagated.propagate(() -> interceptedMethod.interceptResultAsPublisher(this));
                        }
                    } catch (Throwable e) {
                        permits.leave();
                        return Flux.error(e);
                    }
                    return Flux.from(publisher).doFinally(signal -> permits.leave());
                });
        });
    }

    private static BulkheadFullException full(BulkheadPermits permits) {
        return new BulkheadFullException("The bulkhead [" + permits.name() + "] is full: "
            + permits.maxConcurrent() + " calls are running");
    }

    /**
     * What the annotation of a method says, kept by the method.
     *
     * @param name The name of the bulkhead, empty where the method has its own
     * @param maxConcurrent How many calls run at the same time, or {@link #NONE} without the annotation
     * @param maxWaitNanos How long a call waits for a place
     */
    private record Plan(String name, int maxConcurrent, long maxWaitNanos) {
        static final int NONE = -1;
        private static final String MAX_CONCURRENT = "maxConcurrent";
        private static final String MAX_WAIT = "maxWait";

        static Plan of(ExecutableMethod<?, ?> method, ConversionService conversionService) {
            AnnotationValue<Bulkhead> bulkhead = method.getAnnotation(Bulkhead.class);
            if (bulkhead == null) {
                return new Plan("", NONE, 0);
            }
            String user = "@Bulkhead of " + method.getDeclaringType().getName() + "#" + method.getMethodName();
            // each value is read as it is written: one that cannot be read is an error, and never the default
            String written = bulkhead.stringValue(MAX_CONCURRENT).orElse("10");
            int maxConcurrent = conversionService.convert(written, Integer.class).orElse(0);
            if (maxConcurrent < 1) {
                throw new IllegalStateException(user + ": maxConcurrent is [" + written + "] and must be a number of at least 1");
            }
            written = bulkhead.stringValue(MAX_WAIT).orElse("0s");
            Duration maxWait = conversionService.convert(written, Duration.class).orElse(null);
            if (maxWait == null || maxWait.isNegative()) {
                throw new IllegalStateException(user + ": maxWait is [" + written
                    + "] and must be a time, which is written as 2s or 500ms");
            }
            return new Plan(bulkhead.stringValue("name").orElse(""), maxConcurrent, maxWait.toNanos());
        }
    }

    /**
     * @param targetType The class of the intercepted bean
     * @param method The method
     */
    private record Key(Class<?> targetType, ExecutableMethod<?, ?> method) {
    }
}
