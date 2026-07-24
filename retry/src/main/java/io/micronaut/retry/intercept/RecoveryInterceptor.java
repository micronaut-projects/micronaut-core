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
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.Fallback;
import io.micronaut.retry.annotation.Recoverable;
import io.micronaut.retry.exception.FallbackException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A {@link MethodInterceptor} that will attempt to execute a {@link Fallback}
 * when the target method is in an error state.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class RecoveryInterceptor implements MethodInterceptor<Object, Object> {

    private record FallbackResult(MethodExecutionHandle<?, Object> handle,
                                  AnnotationValue<Fallback> beanAnnotation,
                                  AnnotationValue<Fallback> methodAnnotation) {
    }

    /**
     * Positioned before the {@link io.micronaut.retry.annotation.Retryable} interceptor.
     */
    public static final int POSITION = InterceptPhase.RETRY.getPosition() - 10;

    private static final Logger LOG = LoggerFactory.getLogger(RecoveryInterceptor.class);
    private static final String FALLBACK_NOT_FOUND = "FALLBACK_NOT_FOUND";

    private final BeanContext beanContext;

    /**
     * Creates a recovery interceptor.
     *
     * @param beanContext The bean context to allow for DI of class annotated with {@link jakarta.inject.Inject}.
     */
    public RecoveryInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    @Nullable
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getAttribute(FALLBACK_NOT_FOUND, Boolean.class).orElse(Boolean.FALSE)) {
            return context.proceed();
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, beanContext.getConversionService());
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    return interceptedMethod.handleResult(
                        fallbackForReactiveType(context, interceptedMethod.interceptResultAsPublisher())
                    );
                }
                case COMPLETION_STAGE -> {
                    if (context.isSuspend()) {
                        return interceptedMethod.handleResult(
                            fallbackForSuspend(context, interceptedMethod.interceptResultAsCompletionStage())
                        );
                    } else {
                        return interceptedMethod.handleResult(
                            fallbackForFuture(context, interceptedMethod.interceptResultAsCompletionStage())
                        );
                    }
                }
                case SYNCHRONOUS -> {
                    try {
                        return context.proceed();
                    } catch (RuntimeException e) {
                        return resolveFallback(context, e);
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

    @SuppressWarnings("unchecked")
    private Publisher<?> fallbackForReactiveType(MethodInvocationContext<Object, Object> context, Publisher<?> publisher) {
        return Flux.from(publisher).onErrorResume(throwable -> {
            Optional<FallbackResult> fallbackMethod = findFallbackMethod(context);
            if (fallbackMethod.isPresent()) {
                FallbackResult fallback = fallbackMethod.get();
                MethodExecutionHandle<?, Object> fallbackHandle = fallback.handle();
                if (!canFallback(fallback.beanAnnotation(), fallback.methodAnnotation(), throwable)) {
                    return Flux.error(throwable);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass(), fallbackHandle);
                }

                Object fallbackResult;
                try {
                    fallbackResult = fallbackHandle.invoke(context.getParameterValues());
                } catch (Exception e) {
                    return Flux.error(throwable);
                }
                if (fallbackResult == null) {
                    return Flux.error(new FallbackException("Fallback handler [" + fallbackHandle + "] returned null value"));
                } else {
                    return beanContext.getConversionService().convert(fallbackResult, Publisher.class)
                        .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + fallbackResult));
                }
            }
            return Flux.error(throwable);
        });
    }

    /**
     * Finds a fallback method for the given context.
     *
     * @param context The context
     * @return The fallback method if it is present
     */
    public Optional<FallbackResult> findFallbackMethod(MethodInvocationContext<Object, Object> context) {
        Class<?> declaringType = context.classValue(Recoverable.class, "api").orElseGet(context::getDeclaringType);
        BeanDefinition<?> beanDefinition = beanContext.findBeanDefinition(declaringType, Qualifiers.byStereotype(Fallback.class)).orElse(null);
        if (beanDefinition != null) {
            ExecutableMethod<?, Object> fallBackMethod =
                beanDefinition.findMethod(context.getMethodName(), context.getArgumentTypes()).orElse(null);
            if (fallBackMethod != null) {
                MethodExecutionHandle<?, Object> executionHandle = beanContext.createExecutionHandle(beanDefinition, (ExecutableMethod<Object, ?>) fallBackMethod);
                AnnotationValue<Fallback> beanAnnotation = beanDefinition.findAnnotation(Fallback.class)
                    .orElse(AnnotationValue.builder(Fallback.class).build());
                AnnotationValue<Fallback> methodAnnotation = fallBackMethod.findAnnotation(Fallback.class)
                    .orElse(AnnotationValue.builder(Fallback.class).build());
                return Optional.of(new FallbackResult(executionHandle, beanAnnotation, methodAnnotation));
            }
        }
        context.setAttribute(FALLBACK_NOT_FOUND, true);
        return Optional.empty();
    }

    private boolean canFallback(AnnotationValue<Fallback> beanFallback,
                                AnnotationValue<Fallback> methodFallback,
                                Throwable throwable) {
        List<Class<? extends Throwable>> includes = mergeIncludes(beanFallback, methodFallback);
        List<Class<? extends Throwable>> excludes = mergeExcludes(beanFallback, methodFallback);
        return new DefaultRetryPredicate(includes, excludes).test(throwable);
    }

    private static List<Class<? extends Throwable>> mergeIncludes(AnnotationValue<Fallback> beanFallback,
                                                                  AnnotationValue<Fallback> methodFallback) {
        List<Class<? extends Throwable>> methodIncludes = resolveThrowableClasses(methodFallback, "includes");
        return methodIncludes.isEmpty() ? resolveThrowableClasses(beanFallback, "includes") : methodIncludes;
    }

    private static List<Class<? extends Throwable>> mergeExcludes(AnnotationValue<Fallback> beanFallback,
                                                                  AnnotationValue<Fallback> methodFallback) {
        List<Class<? extends Throwable>> methodExcludes = resolveThrowableClasses(methodFallback, "excludes");
        return methodExcludes.isEmpty() ? resolveThrowableClasses(beanFallback, "excludes") : methodExcludes;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Class<? extends Throwable>> resolveThrowableClasses(AnnotationValue<Fallback> fallback, String member) {
        return (List) List.of(fallback.classValues(member));
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<?> fallbackForFuture(MethodInvocationContext<Object, Object> context, CompletionStage<?> result) {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        result.whenComplete((o, throwable) -> {
            if (throwable == null) {
                newFuture.complete(o);
            } else {
                Optional<FallbackResult> fallbackMethod = findFallbackMethod(context);
                if (fallbackMethod.isPresent()) {
                    FallbackResult fallbackResult = fallbackMethod.get();
                    MethodExecutionHandle<?, Object> fallbackHandle = fallbackResult.handle();
                    if (!canFallback(fallbackResult.beanAnnotation(), fallbackResult.methodAnnotation(), throwable)) {
                        newFuture.completeExceptionally(throwable);
                        return;
                    }
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
                            LOG.error("Error invoking Fallback [{}]: {}", fallbackHandle, e.getMessage(), e);
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

    private CompletionStage<?> fallbackForSuspend(MethodInvocationContext<Object, Object> context, CompletionStage<?> result) {
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        result.whenComplete((o, throwable) -> {
            if (throwable == null) {
                newFuture.complete(o);
            } else {
                Optional<FallbackResult> fallbackMethod = findFallbackMethod(context);
                if (fallbackMethod.isPresent()) {
                    FallbackResult fallbackResult = fallbackMethod.get();
                    MethodExecutionHandle<?, Object> fallbackHandle = fallbackResult.handle();
                    if (!canFallback(fallbackResult.beanAnnotation(), fallbackResult.methodAnnotation(), throwable)) {
                        newFuture.completeExceptionally(throwable);
                        return;
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass(), fallbackHandle);
                    }
                    try {
                        newFuture.complete(fallbackHandle.invoke(context.getParameterValues()));
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
     * Resolves a fallback for the given execution context and exception.
     *
     * @param context The context
     * @param exception The exception
     * @return Returns the fallback value or throws the original exception
     */
    @Nullable
    protected Object resolveFallback(MethodInvocationContext<Object, Object> context, RuntimeException exception) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Type [{}] executed with error: {}", context.getTarget().getClass().getName(), exception.getMessage(), exception);
        }

        Optional<FallbackResult> fallback = findFallbackMethod(context);
        if (fallback.isPresent()) {
            FallbackResult fallbackResult = fallback.get();
            MethodExecutionHandle<?, Object> fallbackMethod = fallbackResult.handle();
            if (!canFallback(fallbackResult.beanAnnotation(), fallbackResult.methodAnnotation(), exception)) {
                throw exception;
            }
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
