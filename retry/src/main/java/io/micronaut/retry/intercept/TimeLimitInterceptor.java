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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.annotation.TimeLimit;
import io.micronaut.retry.exception.TimeLimitExceededException;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link MethodInterceptor} that fails a call whose result is not there within the time its
 * {@link TimeLimit} annotation gives it.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
@Singleton
public final class TimeLimitInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * Inside the retries, outside the bulkhead.
     */
    public static final int POSITION = InterceptPhase.RETRY.getPosition() + 5;

    private final ConversionService conversionService;
    private final ScheduledExecutorService scheduler;
    private final Scheduler reactiveScheduler;

    /**
     * @param conversionService The conversion service
     * @param executorService The executor that ends a call at its limit
     */
    public TimeLimitInterceptor(ConversionService conversionService,
                                @Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this.conversionService = conversionService;
        this.scheduler = (ScheduledExecutorService) executorService;
        this.reactiveScheduler = Schedulers.fromExecutorService(executorService);
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
            plan = context.getCachedValue(Plan.class, Plan::of);
        } catch (IllegalStateException e) {
            return InterceptedMethod.of(context, conversionService).handleException(e);
        }
        if (plan.nanos() == Plan.NONE) {
            return context.proceed();
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    Publisher<?> publisher = interceptedMethod.interceptResultAsPublisher(this);
                    return interceptedMethod.handleResult(limited(publisher, plan));
                }
                case COMPLETION_STAGE -> {
                    return interceptedMethod.handleResult(
                        limited(interceptedMethod.interceptResultAsCompletionStage(this), plan)
                    );
                }
                case SYNCHRONOUS -> throw new IllegalStateException("@TimeLimit of " + plan.user()
                    + ": the method answers on the thread it is called on, which a time limit does not interrupt."
                    + " Return a CompletionStage or a Publisher, or make it a function that suspends");
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private Publisher<Object> limited(Publisher<?> publisher, Plan plan) {
        return Flux.defer(() -> {
            // the limit cancels the publisher and ends what it relayed, and the failure follows that end
            AtomicBoolean exceeded = new AtomicBoolean();
            Mono<Long> limit = Mono.delay(Duration.ofNanos(plan.nanos()), reactiveScheduler)
                .doOnNext(tick -> exceeded.set(true));
            return Flux.<Object>from(publisher)
                .takeUntilOther(limit)
                .concatWith(Mono.defer(() -> exceeded.get() ? Mono.error(exceeded(plan)) : Mono.empty()));
        });
    }

    private CompletionStage<Object> limited(CompletionStage<?> stage, Plan plan) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        ScheduledFuture<?> end = scheduler.schedule(() -> {
            if (result.completeExceptionally(exceeded(plan)) && stage instanceof CompletableFuture<?> future) {
                future.cancel(false);
            }
        }, plan.nanos(), TimeUnit.NANOSECONDS);
        stage.whenComplete((value, failure) -> {
            end.cancel(false);
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static TimeLimitExceededException exceeded(Plan plan) {
        return new TimeLimitExceededException("The call of " + plan.user() + " took longer than " + plan.limit());
    }

    /**
     * What the annotation of a method says, kept by the method.
     *
     * @param user The method
     * @param limit The limit as it was written
     * @param nanos The limit, or {@link #NONE} without the annotation
     */
    private record Plan(String user, String limit, long nanos) {
        static final long NONE = -1;

        static Plan of(ExecutableMethod<?, ?> method) {
            AnnotationValue<TimeLimit> timeLimit = method.getAnnotation(TimeLimit.class);
            if (timeLimit == null) {
                return new Plan("", "", NONE);
            }
            String user = method.getDeclaringType().getName() + "#" + method.getMethodName();
            String limit = timeLimit.stringValue().orElse("");
            Duration duration = timeLimit.get(AnnotationMetadata.VALUE_MEMBER, Duration.class).orElse(null);
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalStateException("@TimeLimit of " + user + ": [" + limit
                    + "] is not a time, which is written as 2s or 500ms");
            }
            return new Plan(user, limit, duration.toNanos());
        }
    }
}
