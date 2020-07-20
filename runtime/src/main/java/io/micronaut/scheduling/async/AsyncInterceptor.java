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
package io.micronaut.scheduling.async;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.Async;
import io.micronaut.scheduling.exceptions.TaskExecutionException;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * Interceptor implementation for the {@link Async} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
public class AsyncInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutors.class);
    private final BeanLocator beanLocator;
    private final Optional<Provider<ExecutorService>> scheduledExecutorService;
    private final Map<String, ExecutorService> scheduledExecutorServices = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     *
     * @param beanLocator The bean constructor
     * @param scheduledExecutorService The scheduled executor service
     */
    AsyncInterceptor(BeanLocator beanLocator, @Named(TaskExecutors.SCHEDULED) Optional<Provider<ExecutorService>> scheduledExecutorService) {
        this.beanLocator = beanLocator;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.ASYNC.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String executorServiceName = context.stringValue(Async.class).orElse(TaskExecutors.SCHEDULED);
        ExecutorService executorService;
        if (TaskExecutors.SCHEDULED.equals(executorServiceName) && scheduledExecutorService.isPresent()) {
            executorService = scheduledExecutorService.get().get();
        } else {
            executorService = scheduledExecutorServices.computeIfAbsent(executorServiceName, name ->
                    beanLocator.findBean(ExecutorService.class, Qualifiers.byName(name))
                            .orElseThrow(() -> new TaskExecutionException("No ExecutorService named [" + name + "] configured in application context")));
        }

        ReturnType<Object> rt = context.getReturnType();
        Class<?> returnType = rt.getType();
        if (CompletionStage.class.isAssignableFrom(returnType) || Future.class.isAssignableFrom(returnType)) {
            CompletableFuture newFuture = new CompletableFuture();

            executorService.submit(() -> {
                CompletionStage<?> completionStage = (CompletionStage) context.proceed();
                if (completionStage == null) {
                    newFuture.complete(null);
                } else {
                    completionStage.whenComplete((BiConsumer<Object, Throwable>) (o, throwable) -> {
                        if (throwable != null) {
                            newFuture.completeExceptionally(throwable);
                        } else {
                            newFuture.complete(o);
                        }
                    });
                }
            });
            return newFuture;
        } else if (void.class == returnType) {
            executorService.submit(() -> {
                try {
                    context.proceed();
                } catch (Throwable e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error occurred executing @Async method [" + context.getExecutableMethod() + "]: " + e.getMessage(), e);
                    }
                }
            });
            return null;
        } else if (Publishers.isConvertibleToPublisher(returnType)) {
            Object result = context.proceed();
            Flowable<?> flowable = Publishers.convertPublisher(result, Flowable.class);
            flowable = flowable.subscribeOn(Schedulers.from(executorService));
            return Publishers.convertPublisher(flowable, returnType);
        } else {
            throw new TaskExecutionException("Method [" + context.getExecutableMethod() + "] must return either void, or an instance of Publisher or CompletionStage");
        }
    }
}
