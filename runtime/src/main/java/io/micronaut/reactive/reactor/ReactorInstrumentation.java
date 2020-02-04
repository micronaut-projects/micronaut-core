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
package io.micronaut.reactive.reactor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Instruments Reactor such that the thread factory used by Micronaut is used and instrumentations can be applied to the {@link java.util.concurrent.ScheduledExecutorService}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(classes = {Flux.class, Schedulers.Factory.class})
@Context
@Internal
class ReactorInstrumentation {
    private static final Logger LOG = LoggerFactory.getLogger(ReactorInstrumentation.class);

    /**
     * Initialize instrumentation for reactor with the tracer and factory.
     *
     * @param beanContext   The bean context
     * @param threadFactory The factory to create new threads on-demand
     */
    @SuppressWarnings("unchecked")
    @PostConstruct
    void init(BeanContext beanContext, ThreadFactory threadFactory) {
        if (beanContext instanceof ApplicationContext) {
            try {
                BeanDefinition<ExecutorService> beanDefinition = beanContext.getBeanDefinition(ExecutorService.class, Qualifiers.byName(TaskExecutors.SCHEDULED));
                Collection<BeanCreatedEventListener> schedulerCreateListeners =
                        beanContext.getBeansOfType(BeanCreatedEventListener.class, Qualifiers.byTypeArguments(ScheduledExecutorService.class));

                Schedulers.addExecutorServiceDecorator(Environment.MICRONAUT, (scheduler, scheduledExecutorService) -> {
                    for (BeanCreatedEventListener schedulerCreateListener : schedulerCreateListeners) {
                        Object newBean = schedulerCreateListener.onCreated(new BeanCreatedEvent(beanContext, beanDefinition, BeanIdentifier.of("reactor-" + scheduler.getClass().getSimpleName()), scheduledExecutorService));
                        if (!(newBean instanceof ScheduledExecutorService)) {
                            throw new BeanContextException("Bean creation listener [" + schedulerCreateListener + "] should return ScheduledExecutorService, but returned " + newBean);
                        }
                        scheduledExecutorService = (ScheduledExecutorService) newBean;
                    }
                    return scheduledExecutorService;
                });
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Could not instrument Reactor for Tracing: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Removes the registered instrumentation.
     */
    @PreDestroy
    void removeInstrumentation() {
        Schedulers.removeExecutorServiceDecorator(Environment.MICRONAUT);
    }
}
