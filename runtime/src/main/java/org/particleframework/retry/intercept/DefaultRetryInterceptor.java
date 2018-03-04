/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.retry.intercept;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.particleframework.aop.InterceptPhase;
import org.particleframework.aop.MethodInterceptor;
import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.context.event.ApplicationEventPublisher;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.type.ReturnType;
import org.particleframework.retry.RetryState;
import org.particleframework.retry.annotation.CircuitBreaker;
import org.particleframework.retry.annotation.Retryable;
import org.particleframework.retry.event.RetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A {@link MethodInterceptor} that retries an operation according to the specified
 * {@link Retryable} annotation
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class DefaultRetryInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryInterceptor.class);


    private final ApplicationEventPublisher eventPublisher;
    private final Map<Method, CircuitBreakerRetry> circuitContexts = new ConcurrentHashMap<>();

    public DefaultRetryInterceptor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        ConvertibleValues<?> retry = context.getValues(Retryable.class);
        boolean isCircuitBreaker = context.hasStereotype(CircuitBreaker.class);
        if (retry != null) {
            MutableRetryState retryState;
            AnnotationRetryStateBuilder retryStateBuilder = new AnnotationRetryStateBuilder(
                    context
            );

            if (isCircuitBreaker) {
                long timeout = context.getValue(CircuitBreaker.class, "reset", Duration.class)
                        .map(Duration::toMillis).orElse(Duration.ofSeconds(20).toMillis());
                retryState = circuitContexts.computeIfAbsent(
                        context.getTargetMethod(),
                        method -> new CircuitBreakerRetry(timeout, retryStateBuilder, context, eventPublisher)
                );

            } else {
                retryState = (MutableRetryState) retryStateBuilder.build();
            }

            retryState.open();

            MutableConvertibleValues<Object> attrs = context.getAttributes();
            attrs.put(RetryState.class.getName(), retry);

            ReturnType<Object> returnType = context.getReturnType();
            Class<Object> javaReturnType = returnType.getType();
            if (Publishers.isPublisher(javaReturnType)) {
                ConversionService<?> conversionService = ConversionService.SHARED;
                Object result = context.proceed();
                if (result == null) {
                    return result;
                } else {
                    Flowable observable = conversionService.convert(result, Flowable.class)
                            .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + result));
                    Flowable retryObservable = observable.onErrorResumeNext(retryFlowable(context, retryState, observable))
                                                        .map(o -> {
                                                            retryState.close(null);
                                                            return o;
                                                        });


                    return conversionService.convert(retryObservable, returnType.asArgument())
                            .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + result));
                }

            } else {
                boolean first = true;
                while (true) {
                    try {
                        Object result = first ? context.proceed() : context.repeat();
                        retryState.close(null);
                        return result;
                    } catch (RuntimeException e) {
                        first = false;
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
        } else {
            return context.proceed();
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
                return retryObservable.delay(delay, TimeUnit.MILLISECONDS);
            }
            else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot retry anymore. Rethrowing original exception for method: {}", context);
                }
                retryState.close(exception);
                return Flowable.error(exception);
            }
        };
    }
}
