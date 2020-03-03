/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry.intercept;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.retry.annotation.Fallback;
import io.micronaut.retry.exception.FallbackException;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link MethodInterceptor} that will attempt to execute a {@link Fallback}
 * when the target method is in an error state.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class RecoveryInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * Positioned before the {@link io.micronaut.retry.annotation.Retryable} interceptor.
     */
    public static final int POSITION = InterceptPhase.RETRY.getPosition() - 10;

    private static final Logger LOG = LoggerFactory.getLogger(RecoveryInterceptor.class);
    private final BeanContext beanContext;

    /**
     * @param beanContext The bean context to allow for DI of class annotated with {@link javax.inject.Inject}.
     */
    public RecoveryInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        try {
            Object result = context.proceed();
            if (result != null) {
                if (result instanceof CompletableFuture) {
                    return fallbackForFuture(context, (CompletableFuture) result);
                } else if (Publishers.isConvertibleToPublisher(result.getClass())) {
                    return fallbackForReactiveType(context, result);
                }
            }
            return result;
        } catch (RuntimeException e) {
            return resolveFallback(context, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T fallbackForReactiveType(MethodInvocationContext<Object, Object> context, T result) {
        Flowable<Object> recoveryFlowable = ConversionService.SHARED
            .convert(result, Flowable.class)
            .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + result));

        recoveryFlowable = recoveryFlowable.onErrorResumeNext(throwable -> {
            Optional<? extends MethodExecutionHandle<?, Object>> fallbackMethod = findFallbackMethod(context);
            if (fallbackMethod.isPresent()) {
                MethodExecutionHandle<?, Object> fallbackHandle = fallbackMethod.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass(), fallbackHandle);
                }

                Object fallbackResult;
                try {
                    fallbackResult = fallbackHandle.invoke(context.getParameterValues());
                } catch (Exception e) {
                    return Flowable.error(throwable);
                }
                if (fallbackResult == null) {
                    return Flowable.error(new FallbackException("Fallback handler [" + fallbackHandle + "] returned null value"));
                } else {
                    return ConversionService.SHARED.convert(fallbackResult, Publisher.class)
                        .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + fallbackResult));
                }
            }
            return Flowable.error(throwable);
        });

        return (T) ConversionService.SHARED
            .convert(recoveryFlowable, context.getReturnType().asArgument())
            .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + result));
    }

    /**
     * Finds a fallback method for the given context.
     *
     * @param context The context
     * @return The fallback method if it is present
     */
    public Optional<? extends MethodExecutionHandle<?, Object>> findFallbackMethod(MethodInvocationContext<Object, Object> context) {
        Class<?> declaringType = context.getDeclaringType();
        Optional<? extends MethodExecutionHandle<?, Object>> result = beanContext
                .findExecutionHandle(declaringType, Qualifiers.byStereotype(Fallback.class), context.getMethodName(), context.getArgumentTypes());
        if (!result.isPresent()) {
            Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(declaringType);
            for (Class i : allInterfaces) {
                result = beanContext
                    .findExecutionHandle(i, Qualifiers.byStereotype(Fallback.class), context.getMethodName(), context.getArgumentTypes());
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object fallbackForFuture(MethodInvocationContext<Object, Object> context, CompletableFuture result) {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        ((CompletableFuture<Object>) result).whenComplete((o, throwable) -> {
            if (throwable == null) {
                newFuture.complete(o);
            } else {
                Optional<? extends MethodExecutionHandle<?, Object>> fallbackMethod = findFallbackMethod(context);
                if (fallbackMethod.isPresent()) {
                    MethodExecutionHandle<?, Object> fallbackHandle = fallbackMethod.get();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass(), fallbackHandle);
                    }

                    try {
                        CompletableFuture<Object> resultingFuture = (CompletableFuture<Object>) fallbackHandle.invoke(context.getParameterValues());
                        if (resultingFuture == null) {
                            newFuture.completeExceptionally(new FallbackException("Fallback handler [" + fallbackHandle + "] returned null value"));
                        } else {
                            resultingFuture.whenComplete((o1, throwable1) -> {
                                if (throwable1 == null) {
                                    newFuture.complete(o1);
                                } else {
                                    newFuture.completeExceptionally(throwable1);
                                }
                            });
                        }

                    } catch (Exception e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error invoking Fallback [" + fallbackHandle + "]: " + e.getMessage(), e);
                        }
                        newFuture.completeExceptionally(throwable);
                    }

                } else {
                    newFuture.completeExceptionally(throwable);
                }
            }
        });

        return newFuture;
    }

    /**
     * Resolves a fallback for the given execution context and exception.
     *
     * @param context   The context
     * @param exception The exception
     * @return Returns the fallback value or throws the original exception
     */
    protected Object resolveFallback(MethodInvocationContext<Object, Object> context, RuntimeException exception) {
        if (exception instanceof NoAvailableServiceException) {
            NoAvailableServiceException nase = (NoAvailableServiceException) exception;
            if (LOG.isErrorEnabled()) {
                LOG.debug(nase.getMessage(), nase);
                LOG.error("Type [{}] attempting to resolve fallback for unavailable service [{}]", context.getTarget().getClass().getName(), nase.getServiceID());
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Type [" + context.getTarget().getClass().getName() + "] executed with error: " + exception.getMessage(), exception);
            }
        }

        Optional<? extends MethodExecutionHandle<?, Object>> fallback = findFallbackMethod(context);
        if (fallback.isPresent()) {
            MethodExecutionHandle<?, Object> fallbackMethod = fallback.get();
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass().getName(), fallbackMethod);
                }
                return fallbackMethod.invoke(context.getParameterValues());
            } catch (Exception e) {
                throw new FallbackException("Error invoking fallback for type [" + context.getTarget().getClass().getName() + "]: " + e.getMessage(), e);
            }
        } else {
            throw exception;
        }
    }
}
