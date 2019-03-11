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
package io.micronaut.http.server.context;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

/**
 * Instruments Micronaut such that {@link io.micronaut.http.context.ServerRequestContext} state is propagated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
final class ServerRequestContextInstrumentation implements Function<Runnable, Runnable>, RunnableInstrumenter, ReactiveInstrumenter, BeanCreatedEventListener<ThreadFactory> {

    @Override
    public Runnable apply(Runnable runnable) {
        return instrumentRunnable(runnable);
    }

    @Override
    public Runnable instrument(Runnable command) {
        return apply(command);
    }

    @Override
    public ThreadFactory onCreated(BeanCreatedEvent<ThreadFactory> event) {
        final ThreadFactory original = event.getBean();
        return r -> {
            final Optional<HttpRequest<Object>> httpRequest = ServerRequestContext.currentRequest();
            return original.newThread(
                    httpRequest.map(objectHttpRequest -> ServerRequestContext.instrument(objectHttpRequest, r))
                            .orElse(r)
            );
        };
    }

    private static Runnable instrumentRunnable(Runnable runnable) {
        final Optional<HttpRequest<Object>> httpRequest = ServerRequestContext.currentRequest();
        return httpRequest.map(objectHttpRequest -> ServerRequestContext.instrument(objectHttpRequest, runnable))
                .orElse(runnable);
    }

    @Override
    public Optional<RunnableInstrumenter> newInstrumentation() {
        final Optional<HttpRequest<Object>> httpRequest = ServerRequestContext.currentRequest();
        return httpRequest.map(request -> new RunnableInstrumenter() {
            @Override
            public Runnable instrument(Runnable command) {
                return ServerRequestContext.instrument(request, command);
            }
        });
    }

    /**
     * Instruments executor services.
     *
     * @author graemerocher
     * @since 1.0
     */
    @Singleton
    static class ExecutorServiceInstrumentation implements BeanCreatedEventListener<ExecutorService> {
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
                        return instrumentRunnable(command);
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
                        return instrumentRunnable(command);
                    }
                };
            }
        }

        private <T> Callable<T> doInstrument(Callable<T> task) {
            Optional<HttpRequest<Object>> current = ServerRequestContext.currentRequest();
            return current.<Callable<T>>map(objectHttpRequest -> () ->
                    ServerRequestContext.with(objectHttpRequest, task)
            ).orElse(task);
        }
    }
}
