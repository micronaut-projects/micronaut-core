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
package io.micronaut.retry.intercept;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.retry.annotation.RetryExhaustedActionable;
import io.micronaut.retry.annotation.RetryExhaustedCallback;
import io.micronaut.retry.exception.RetryExhaustedCallbackException;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class RetryExhaustedActionInterceptor implements MethodInterceptor<Object, Object> {
    
    /**
     * TODO - best placement?
     * Positioned before the {@link io.micronaut.retry.annotation.Recoverable} interceptor.
     */
    public static final int POSITION = InterceptPhase.RETRY.getPosition() - 11;

    private static final Logger LOG = LoggerFactory.getLogger(RetryExhaustedActionInterceptor.class);
    private static final String RETRY_EXHAUSTED_CALLBACK_NOT_FOUND = "RETRY_EXHAUSTED_CALLBACK_NOT_FOUND";

    private final BeanContext beanContext;
    
    public RetryExhaustedActionInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }
    
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getAttribute(RETRY_EXHAUSTED_CALLBACK_NOT_FOUND, Boolean.class).orElse(Boolean.FALSE)) {
            return context.proceed();
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, beanContext.getConversionService());
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    return interceptedMethod.handleResult(
                        callbackForReactiveType(context, interceptedMethod.interceptResultAsPublisher())
                    );
                }
                case COMPLETION_STAGE -> {
                    if (context.isSuspend()) {
                        return interceptedMethod.handleResult(
                            callbackForSuspend(context, interceptedMethod.interceptResultAsCompletionStage())
                        );
                    } else {
                        return interceptedMethod.handleResult(
                            callbackForFuture(context, interceptedMethod.interceptResultAsCompletionStage())
                        );
                    }
                }
                case SYNCHRONOUS -> {
                    try {
                        return context.proceed();
                    } catch (RuntimeException e) {
                        return resolveCallback(context, e);
                    }
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    public Optional<? extends MethodExecutionHandle<?, Object>> findCallbackMethod(MethodInvocationContext<Object, Object> context) {
        Class<?> declaringType = context.classValue(RetryExhaustedActionable.class, "api").orElseGet(context::getDeclaringType);
        BeanDefinition<?> beanDefinition = 
            beanContext.findBeanDefinition(declaringType, Qualifiers.byStereotype(RetryExhaustedCallback.class))
                .orElse(null);
        if (beanDefinition != null) {
            ExecutableMethod<?, Object> callBackMethod =
                beanDefinition.findMethod(context.getMethodName(), context.getArgumentTypes()).orElse(null);
            if (callBackMethod != null) {
                MethodExecutionHandle<?, Object> executionHandle = beanContext.createExecutionHandle(beanDefinition, (ExecutableMethod<Object, ?>) callBackMethod);
                return Optional.of(executionHandle);
            }
        }
        context.setAttribute(RETRY_EXHAUSTED_CALLBACK_NOT_FOUND, Boolean.TRUE);
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Publisher<?> callbackForReactiveType(MethodInvocationContext<Object, Object> context, Publisher<?> publisher) {
        return Flux.from(publisher).onErrorResume(throwable -> {
            Optional<? extends MethodExecutionHandle<?, Object>> callbackMethod = findCallbackMethod(context);
            if (callbackMethod.isPresent()) {
                MethodExecutionHandle<?, Object> callbackHandle = callbackMethod.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved callback: {}", context.getTarget().getClass(), callbackHandle);
                }

                Object callbackResult;
                try {
                    callbackResult = callbackHandle.invoke(context.getParameterValues());
                } catch (Exception e) {
                    return Flux.error(throwable);
                }
                if (callbackResult == null) {
                    return Flux.error(new RetryExhaustedCallbackException("Callback handler [" + callbackHandle + "] returned null value"));
                } else {
                    return beanContext.getConversionService().convert(callbackResult, Publisher.class)
                            .orElseThrow(() -> new RetryExhaustedCallbackException("Unsupported Reactive type: " + callbackResult));
                }
            }
            return Flux.error(throwable);
        });
    }
    
    @SuppressWarnings("unchecked")
    private CompletionStage<?> callbackForFuture(MethodInvocationContext<Object, Object> context, CompletionStage<?> result) {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        result.whenComplete((o, throwable) -> {
            if (throwable == null) {
                newFuture.complete(o);
            } else {
                Optional<? extends MethodExecutionHandle<?, Object>> callbackMethod = findCallbackMethod(context);
                if (callbackMethod.isPresent()) {
                    MethodExecutionHandle<?, Object> callbackHandle = callbackMethod.get();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Type [{}] resolved callback: {}", context.getTarget().getClass(), callbackHandle);
                    }

                    try {
                        CompletableFuture<Object> resultingFuture = (CompletableFuture<Object>) callbackHandle.invoke(context.getParameterValues());
                        if (resultingFuture == null) {
                            newFuture.completeExceptionally(new RetryExhaustedCallbackException("Callback handler [" + callbackHandle + "] returned null value"));
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
                            LOG.error("Error invoking Callback [{}]: {}", callbackHandle, e.getMessage(), e);
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

    private CompletionStage<?> callbackForSuspend(MethodInvocationContext<Object, Object> context, CompletionStage<?> result) {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        result.whenComplete((o, throwable) -> {
            if (throwable == null) {
                newFuture.complete(o);
            } else {
                Optional<? extends MethodExecutionHandle<?, Object>> callbackMethod = findCallbackMethod(context);
                if (callbackMethod.isPresent()) {
                    MethodExecutionHandle<?, Object> callbackHandle = callbackMethod.get();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Type [{}] resolved callback: {}", context.getTarget().getClass(), callbackHandle);
                    }
                    try {
                        newFuture.complete(callbackHandle.invoke(context.getParameterValues()));
                    } catch (Throwable t) {
                        newFuture.completeExceptionally(t);
                    }
                } else {
                    newFuture.completeExceptionally(throwable);
                }
            }
        });

        return newFuture;
    }

    /**
     * Resolves a callback for the given execution context and exception.
     *
     * @param context The context
     * @param exception The exception
     * @return Returns the callback value or throws the original exception
     */
    protected Object resolveCallback(MethodInvocationContext<Object, Object> context, RuntimeException exception) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Type [{}] executed with error: {}", context.getTarget().getClass().getName(), exception.getMessage(), exception);
        }

        Optional<? extends MethodExecutionHandle<?, Object>> callback = findCallbackMethod(context);
        if (callback.isPresent()) {
            MethodExecutionHandle<?, Object> callbackMethod = callback.get();
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved callback: {}", context.getTarget().getClass().getName(), callbackMethod);
                }
                return callbackMethod.invoke(context.getParameterValues());
            } catch (Exception e) {
                throw new RetryExhaustedCallbackException("Error invoking callback for type [" + context.getTarget().getClass().getName() + "]: " + e.getMessage(), e);
            }
        } else {
            throw exception;
        }
    }
}
