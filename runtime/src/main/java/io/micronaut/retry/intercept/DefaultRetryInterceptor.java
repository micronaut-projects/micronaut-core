/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * A {@link MethodInterceptor} that retries an operation according to the specified
 * {@link Retryable} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class DefaultRetryInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryInterceptor.class);
    private static final int DEFAULT_CIRCUIT_BREAKER_TIMEOUT_IN_MILLIS = 20;

    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledExecutorService executorService;
    private final Map<ExecutableMethod, CircuitBreakerRetry> circuitContexts = new ConcurrentHashMap<>();

    /**
     * Construct a default retry method interceptor with the event publisher.
     *
     * @param eventPublisher The event publisher to publish retry events
     * @param executorService The executor service to use for completable futures
     */
    @Inject
    public DefaultRetryInterceptor(ApplicationEventPublisher eventPublisher, @Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this.eventPublisher = eventPublisher;
        this.executorService = (ScheduledExecutorService) executorService;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<AnnotationValue<Retryable>> opt = context.findAnnotation(Retryable.class);
        if (!opt.isPresent()) {
            return context.proceed();
        }

        AnnotationValue<Retryable> retry = opt.get();
        boolean isCircuitBreaker = context.hasStereotype(CircuitBreaker.class);
        MutableRetryState retryState;
        AnnotationRetryStateBuilder retryStateBuilder = new AnnotationRetryStateBuilder(
            context
        );

        if (isCircuitBreaker) {
            long timeout = context
                .getValue(CircuitBreaker.class, "reset", Duration.class)
                .map(Duration::toMillis).orElse(Duration.ofSeconds(DEFAULT_CIRCUIT_BREAKER_TIMEOUT_IN_MILLIS).toMillis());
            retryState = circuitContexts.computeIfAbsent(
                context.getExecutableMethod(),
                method -> new CircuitBreakerRetry(timeout, retryStateBuilder, context, eventPublisher)
            );
        } else {
            retryState = (MutableRetryState) retryStateBuilder.build();
        }

        MutableConvertibleValues<Object> attrs = context.getAttributes();
        attrs.put(RetryState.class.getName(), retry);

        ReturnType<Object> returnType = context.getReturnType();
        Class<Object> javaReturnType = returnType.getType();
        if (CompletionStage.class.isAssignableFrom(javaReturnType)) {
            try {
                retryState.open();
            } catch (RuntimeException e) {
                CompletableFuture newFuture = new CompletableFuture();
                newFuture.completeExceptionally(e);
                return newFuture;
            }
            Object result = context.proceed();
            if (result == null) {
                return result;
            } else {
                CompletableFuture newFuture = new CompletableFuture();
                ((CompletableFuture<?>) result).whenComplete(retryCompletable(newFuture, context, retryState));
                return newFuture;
            }
        } else if (Publishers.isConvertibleToPublisher(javaReturnType)) {
            ConversionService<?> conversionService = ConversionService.SHARED;
            try {
                retryState.open();
            } catch (RuntimeException e) {
                Publisher result = Publishers.just(e);
                return conversionService.convert(result, returnType.asArgument())
                        .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + result));
            }
            Object result = context.proceed();
            if (result == null) {
                return result;
            } else {
                Flowable observable = conversionService
                    .convert(result, Flowable.class)
                    .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + result));
                Flowable retryObservable = observable.onErrorResumeNext(retryFlowable(context, retryState, observable))
                    .doOnNext(o ->
                        retryState.close(null)
                    );

                return conversionService
                    .convert(retryObservable, returnType.asArgument())
                    .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + result));
            }

        } else {
            retryState.open();
            while (true) {
                try {
                    Object result = context.proceed(this);
                    retryState.close(null);
                    return result;
                } catch (RuntimeException e) {
                    if (!retryState.canRetry(e)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Cannot retry anymore. Rethrowing original exception for method: {}", context);
                        }
                        retryState.close(e);
                        throw e;
                    } else {
                        long delayMillis = retryState.nextDelay();
                        try {
                            if (eventPublisher != null) {
                                try {
                                    eventPublisher.publishEvent(new RetryEvent(context, retryState, e));
                                } catch (Exception e1) {
                                    LOG.error("Error occurred publishing RetryEvent: " + e1.getMessage(), e1);
                                }
                            }
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Retrying execution for method [{}] after delay of {}ms for exception: {}", context, delayMillis, e.getMessage());
                            }
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException e1) {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Function retryFlowable(MethodInvocationContext<Object, Object> context, MutableRetryState retryState, Flowable observable) {
        return throwable -> {
            Throwable exception = (Throwable) throwable;
            if (retryState.canRetry(exception)) {
                Flowable retryObservable = observable.onErrorResumeNext(retryFlowable(context, retryState, observable));
                long delay = retryState.nextDelay();
                if (eventPublisher != null) {
                    try {
                        eventPublisher.publishEvent(new RetryEvent(context, retryState, exception));
                    } catch (Exception e1) {
                        LOG.error("Error occurred publishing RetryEvent: " + e1.getMessage(), e1);
                    }
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Retrying execution for method [{}] after delay of {}ms for exception: {}",
                        context,
                        delay,
                        (exception).getMessage());
                }
                return retryObservable.delaySubscription(delay, TimeUnit.MILLISECONDS);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot retry anymore. Rethrowing original exception for method: {}", context);
                }
                retryState.close(exception);
                return Flowable.error(exception);
            }
        };
    }

    private  BiConsumer<Object, ? super Throwable> retryCompletable(CompletableFuture<Object> newFuture, MethodInvocationContext<Object, Object> context, MutableRetryState retryState) {
        return (Object value, Throwable exception) -> {
            if (exception == null) {
                retryState.close(null);
                newFuture.complete(value);
                return;
            }
            if (retryState.canRetry(exception)) {
                long delay = retryState.nextDelay();
                if (eventPublisher != null) {
                    try {
                        eventPublisher.publishEvent(new RetryEvent(context, retryState, exception));
                    } catch (Exception e) {
                        LOG.error("Error occurred publishing RetryEvent: " + e.getMessage(), e);
                    }
                }
                executorService.schedule(() -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Retrying execution for method [{}] after delay of {}ms for exception: {}",
                                context,
                                delay,
                                (exception).getMessage());
                    }
                    Object retryResult = context.proceed(this);
                    ((CompletableFuture<?>) retryResult).whenComplete(retryCompletable(newFuture, context, retryState));

                }, delay, TimeUnit.MILLISECONDS);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot retry anymore. Rethrowing original exception for method: {}", context);
                }
                retryState.close(exception);
                newFuture.completeExceptionally(exception);
            }
        };

    }
}
