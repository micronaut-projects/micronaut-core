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

package io.micronaut.http.server.context;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Instruments any {@link ExecutorService} beans to ensure the request context is propagated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
class ServerRequestContextExecutorServiceInstrumentation implements BeanCreatedEventListener<ExecutorService>, RunnableInstrumenter {
    @Override
    public final ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
        ExecutorService executorService = event.getBean();
        if (executorService instanceof ScheduledExecutorService) {
            return new InstrumentedScheduledExecutorService() {
                @Override
                public ScheduledExecutorService getTarget() {
                    return (ScheduledExecutorService) executorService;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> task) {
                    return doInstrument(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return doInstrument(command);
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
                    return doInstrument(task);
                }

                @Override
                public Runnable instrument(Runnable command) {
                    return doInstrument(command);
                }
            };
        }
    }

    @Override
    public Runnable instrument(Runnable command) {
        return doInstrument(command);
    }

    private Runnable doInstrument(Runnable command) {
        Optional<HttpRequest<Object>> current = ServerRequestContext.currentRequest();
        return current.<Runnable>map(objectHttpRequest -> () ->
                ServerRequestContext.with(objectHttpRequest, command)
        ).orElse(command);
    }

    private <T> Callable<T> doInstrument(Callable<T> task) {
        Optional<HttpRequest<Object>> current = ServerRequestContext.currentRequest();
        return current.<Callable<T>>map(objectHttpRequest -> () ->
                ServerRequestContext.with(objectHttpRequest, task)
        ).orElse(task);
    }
}
