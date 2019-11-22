/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.scheduling.instrument;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * Wraps {@link ExecutorService} to instrument {@link Callable} and {@link Runnable}.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Singleton
@Internal
class ExecutorServiceInstrumenter implements BeanCreatedEventListener<ExecutorService> {

    private final List<InvocationInstrumenterFactory> invocationInstrumenterFactories;

    /**
     * Creates new instance.
     *
     * @param invocationInstrumenterFactories invocation instrumentation factories.
     */
    public ExecutorServiceInstrumenter(List<InvocationInstrumenterFactory> invocationInstrumenterFactories) {
        this.invocationInstrumenterFactories = invocationInstrumenterFactories;
    }

    /**
     * Wraps {@link ExecutorService}.
     *
     * @param event The bean created event
     * @return wrapped instance
     */
    @Override
    public ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
        if (invocationInstrumenterFactories.isEmpty()) {
            return event.getBean();
        }
        ExecutorService executorService = event.getBean();
        if (executorService instanceof ScheduledExecutorService) {
            return new InstrumentedScheduledExecutorService() {
                @Override
                public ScheduledExecutorService getTarget() {
                    return (ScheduledExecutorService) executorService;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> task) {
                    return instrumentInvocation(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return instrumentInvocation(command);
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
                    return instrumentInvocation(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return instrumentInvocation(command);
                }
            };
        }
    }

    private Runnable instrumentInvocation(Runnable runnable) {
        return InvocationInstrumenter.instrument(runnable, getInvocationInstrumenter());
    }

    private <V> Callable<V> instrumentInvocation(Callable<V> callable) {
        return InvocationInstrumenter.instrument(callable, getInvocationInstrumenter());
    }

    private List<InvocationInstrumenter> getInvocationInstrumenter() {
        return invocationInstrumenterFactories.stream()
                .map(InvocationInstrumenterFactory::newInvocationInstrumenter)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

}
