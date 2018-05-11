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

package io.micronaut.tracing.instrument.reactor;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.instrument.util.TracingRunnable;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * Instrumentation for Reactor.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = Flux.class)
@Requires(beans = Tracer.class)
@Singleton
@Context
public class ReactorTracingInstrumentation {
    private static final Logger LOG = LoggerFactory.getLogger(ReactorTracingInstrumentation.class);

    /**
     * Initialize instrumentation for reactor with the tracer and factory.
     *
     * @param tracer For Span creation and propagation across arbitrary transports
     * @param threadFactory The factory to create new threads on-demand
     */
    @PostConstruct
    void init(Tracer tracer, ThreadFactory threadFactory) {
        try {
            Schedulers.setFactory(
                    new Schedulers.Factory() {
                        @Override
                        public ScheduledExecutorService decorateExecutorService(String schedulerType, Supplier<? extends ScheduledExecutorService> actual) {
                            return actual.get();
                        }

                        @Override
                        public Scheduler newElastic(int ttlSeconds, ThreadFactory threadFactory) {
                            return Schedulers.Factory.super.newElastic(
                                    ttlSeconds,
                                    (ThreadFactory) r -> {
                                        return threadFactory.newThread(new TracingRunnable(r, tracer));
                                    }
                            );
                        }

                        @Override
                        public Scheduler newParallel(int parallelism, ThreadFactory threadFactory) {
                            return Schedulers.Factory.super.newParallel(
                                    parallelism,
                                    (ThreadFactory) r -> {
                                        return threadFactory.newThread(new TracingRunnable(r, tracer));
                                    }
                            );
                        }

                        @Override
                        public Scheduler newSingle(ThreadFactory threadFactory) {
                            return Schedulers.Factory.super.newSingle(
                                    (ThreadFactory) r -> {
                                        return threadFactory.newThread(new TracingRunnable(r, tracer));
                                    }
                            );
                        }
                    }
            );
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Could not instrument Reactor for Tracing: " + e.getMessage(), e);
            }
        }
    }
}
