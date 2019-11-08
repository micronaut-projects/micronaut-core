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
package io.micronaut.tracing.instrument.scheduling;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import io.micronaut.tracing.instrument.TracingWrapper;

import javax.inject.Singleton;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Instruments runnable threads with {@link TracingWrapper}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = TracingWrapper.class)
@Requires(property = ExecutorServiceInstrumenter.PROPERTY_INSTRUMENT_THREADS,
          value = StringUtils.TRUE,
          defaultValue = StringUtils.FALSE)
public class ExecutorServiceInstrumenter implements BeanCreatedEventListener<ExecutorService> {

    /**
     * Whether to instrument threads.
     */
    public static final String PROPERTY_INSTRUMENT_THREADS = "tracing.instrument-threads";

    private final TracingWrapper tracingWrapper;

    /**
     * Creates a new {@link ExecutorServiceInstrumenter}.
     *
     * @param tracingWrapper The tracingWrapper
     */
    public ExecutorServiceInstrumenter(TracingWrapper tracingWrapper) {
        this.tracingWrapper = tracingWrapper;
    }

    @Override
    public ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
        ExecutorService executorService = event.getBean();
        if (executorService instanceof ScheduledExecutorService) {
            return new InstrumentedScheduledExecutorService() {
                @Override
                public ScheduledExecutorService getTarget() {
                    return (ScheduledExecutorService) executorService;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> task) {
                    return tracingWrapper.wrap(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return tracingWrapper.wrap(command);
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
                    return tracingWrapper.wrap(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return tracingWrapper.wrap(command);
                }
            };
        }
    }
}
