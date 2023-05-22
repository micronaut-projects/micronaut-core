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
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wraps {@link ExecutorService} to instrument {@link Callable} and {@link Runnable} to be aware of {@link PropagatedContext}.
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
        Class<ExecutorService> beanType = event.getBeanDefinition().getBeanType();
        if (beanType == ExecutorService.class) {
            ExecutorService executorService = event.getBean();
            if (executorService instanceof ScheduledExecutorService) {
                return new InstrumentedScheduledExecutorService() {
                    @Override
                    public ScheduledExecutorService getTarget() {
                        return (ScheduledExecutorService) executorService;
                    }

                    @Override
                    public <T> Callable<T> instrument(Callable<T> task) {
                        return PropagatedContext.wrapCurrent(task);
                    }

                    @Override
                    public Runnable instrument(Runnable command) {
                        return PropagatedContext.wrapCurrent(command);
                    }
                };
            } else {
                return new InstrumentedExecutorService() {
                    @Override
                    public ExecutorService getTarget() {
                        return executorService;
                    }

                    @Override
                    public <T> Callable<T> instrument(Callable<T> task) {
                        return PropagatedContext.wrapCurrent(task);
                    }

                    @Override
                    public Runnable instrument(Runnable command) {
                        return PropagatedContext.wrapCurrent(command);
                    }
                };
            }
        } else {
            return event.getBean();
        }
    }

}
