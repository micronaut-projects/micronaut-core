/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context.propagation.instrument.execution;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.scheduling.executor.ExecutorConfiguration;
import io.micronaut.scheduling.executor.ExecutorFactory;
import io.micronaut.scheduling.executor.IOExecutorServiceConfig;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * Wraps {@link ExecutorService} to instrument {@link Callable} and {@link Runnable} to be aware of {@link PropagatedContext}.
 *
 * <p>Executors created by {@link ExecutorFactory} are left as they are, because the factory instruments them itself
 * unless {@link ExecutorConfiguration#isPropagateContext()} is {@code false}.</p>
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Prototype
@Internal
final class ExecutorServiceInstrumenter implements BeanCreatedEventListener<ExecutorService> {

    /**
     * Wraps {@link ExecutorService}.
     *
     * @param event The bean created event
     * @return wrapped instance
     */
    @Override
    public ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
        BeanDefinition<ExecutorService> beanDefinition = event.getBeanDefinition();
        Class<? extends ExecutorService> beanType = beanDefinition.getBeanType();
        if (beanType != ExecutorService.class) {
            return event.getBean();
        }
        ExecutorService executorService = event.getBean();
        if (ContextPropagatingExecutorService.isInstrumented(executorService)) {
            return executorService;
        }
        Class<?> declaringType = beanDefinition.getDeclaringType().orElse(null);
        if (declaringType != null
            && (ExecutorFactory.class.isAssignableFrom(declaringType) || declaringType == IOExecutorServiceConfig.class)) {
            // ExecutorFactory instruments its executors according to ExecutorConfiguration#isPropagateContext(),
            // and the blocking executor is the io or the virtual executor
            return executorService;
        }
        return ContextPropagatingExecutorService.instrument(executorService);
    }
}
