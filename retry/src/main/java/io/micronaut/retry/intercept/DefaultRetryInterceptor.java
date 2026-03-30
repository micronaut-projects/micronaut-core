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
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.AnnotationValue;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

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
    private final ConversionService conversionService;
    @Nullable
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledExecutorService executorService;
    private final Map<ExecutableMethod, CircuitBreakerRetry> circuitContexts = new ConcurrentHashMap<>();
    private final DefaultRetryRunner retryRunner;

    /**
     * Construct a default retry method interceptor with the event publisher.
     *
     * @param conversionService The conversion service
     * @param eventPublisher The event publisher to publish retry events
     * @param executorService The executor service to use for completable futures
     */
    public DefaultRetryInterceptor(ConversionService conversionService,
                                   @Nullable
                                   ApplicationEventPublisher eventPublisher,
                                   @Named(TaskExecutors.SCHEDULED) ExecutorService executorService) {
        this.conversionService = conversionService;
        this.eventPublisher = eventPublisher;
        this.executorService = (ScheduledExecutorService) executorService;
        this.retryRunner = new DefaultRetryRunner(this.executorService, this::sleep);
    }

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition();
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<AnnotationValue<Retryable>> opt = context.findAnnotation(Retryable.class);
        if (opt.isEmpty()) {
            return context.proceed();
        }

        AnnotationValue<Retryable> retry = opt.get();
        boolean isCircuitBreaker = context.hasStereotype(CircuitBreaker.class);
        MutableRetryState retryState;
        AnnotationRetryStateBuilder annotationRetryStateBuilder = new AnnotationRetryStateBuilder(
            context
        );

        if (isCircuitBreaker) {
            CircuitBreakerPolicy circuitBreakerPolicy = annotationRetryStateBuilder.circuitBreakerPolicy();
            long timeout = circuitBreakerPolicy.getResetTimeout().toMillis();
            boolean wrapException = circuitBreakerPolicy.isThrowWrappedException();
            PolicyRetryStateBuilder retryStateBuilder = new PolicyRetryStateBuilder(circuitBreakerPolicy.asRetryPolicy());
            retryState = circuitContexts.computeIfAbsent(
                context.getExecutableMethod(),
                method -> new CircuitBreakerRetry(timeout, retryStateBuilder, context, eventPublisher, wrapException)
            );
        } else {
            retryState = (MutableRetryState) annotationRetryStateBuilder.build();
        }

        MutableConvertibleValues<Object> attrs = context.getAttributes();
        attrs.put(RetryState.class.getName(), retry);

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            retryState.open();
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    Publisher<Object> publisher = retryRunner.executePublisher(
                        () -> (Publisher<Object>) interceptedMethod.interceptResult(this),
                        retryState,
                        context.toString(),
                        (state, exception) -> publishRetryEvent(context, state, exception)
                    );
                    return interceptedMethod.handleResult(publisher);
                }
                case COMPLETION_STAGE -> {
                    return interceptedMethod.handleResult(
                        retryRunner.executeCompletionStage(
                            () -> interceptedMethod.interceptResultAsCompletionStage(this),
                            retryState,
                            context.toString(),
                            (state, exception) -> publishRetryEvent(context, state, exception)
                        )
                    );
                }
                case SYNCHRONOUS -> {
                    return retryRunner.executeSync(
                        () -> (Object) interceptedMethod.interceptResult(this),
                        retryState,
                        context.toString(),
                        (state, exception) -> publishRetryEvent(context, state, exception)
                    );
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    private void publishRetryEvent(MethodInvocationContext<Object, Object> context,
                                   MutableRetryState retryState,
                                   Throwable exception) {
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new RetryEvent(context, retryState, exception));
            } catch (Exception eventException) {
                LOG.error("Error occurred publishing RetryEvent: {}", eventException.getMessage(), eventException);
            }
        }
    }

    /**
     * Performs the sleep between retries and can be overridden to customize sleep behavior.
     *
     * @param delayMillis The delay in milliseconds
     * @throws InterruptedException If the thread is interrupted during sleep
     */
    protected void sleep(long delayMillis) throws InterruptedException {
        Thread.sleep(delayMillis);
    }
}
