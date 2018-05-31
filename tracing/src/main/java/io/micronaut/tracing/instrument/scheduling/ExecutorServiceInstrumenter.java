/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import io.micronaut.tracing.instrument.util.TracingCallable;
import io.micronaut.tracing.instrument.util.TracingRunnable;
import io.opentracing.Tracer;

import javax.inject.Singleton;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Instruments runnable threads with {@link Tracer}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
public class ExecutorServiceInstrumenter implements BeanCreatedEventListener<ExecutorService> {

    private final Tracer tracer;

    /**
     * Creates a new {@link ExecutorServiceInstrumenter}.
     *
     * @param tracer The tracer
     */
    public ExecutorServiceInstrumenter(Tracer tracer) {
        this.tracer = tracer;
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
                    return new TracingCallable<>(task, tracer);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return new TracingRunnable(command, tracer);
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
                    return new TracingCallable<>(task, tracer);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return new TracingRunnable(command, tracer);
                }
            };
        }
    }
}
