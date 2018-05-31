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

package io.micronaut.scheduling.processor;

import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;

/**
 * A {@link ExecutableMethodProcessor} for the {@link Scheduled} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class ScheduledMethodProcessor implements ExecutableMethodProcessor<Scheduled>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TaskScheduler.class);

    private final BeanContext beanContext;
    private final ConversionService<?> conversionService;
    private final Queue<ScheduledFuture<?>> scheduledTasks = new ConcurrentLinkedDeque<>();

    /**
     * @param beanContext       The bean context for DI of beans annotated with {@link javax.inject.Inject}
     * @param conversionService To convert one type to another
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ScheduledMethodProcessor(BeanContext beanContext, Optional<ConversionService<?>> conversionService) {
        this.beanContext = beanContext;
        this.conversionService = conversionService.orElse(ConversionService.SHARED);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Scheduled[] scheduledAnnotations = method.getAnnotationsByType(Scheduled.class);
        for (Scheduled scheduledAnnotation : scheduledAnnotations) {
            String fixedRate = scheduledAnnotation.fixedRate();

            String initialDelayStr = scheduledAnnotation.initialDelay();
            Duration initialDelay = null;
            if (StringUtils.hasText(initialDelayStr)) {
                initialDelay = conversionService.convert(initialDelayStr, Duration.class).orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid initial delay definition: " + initialDelayStr)
                );
            }

            TaskScheduler taskScheduler = beanContext
                .findBean(TaskScheduler.class, Qualifiers.byName(scheduledAnnotation.scheduler()))
                .orElseThrow(() -> new SchedulerConfigurationException(method, "No scheduler of type TaskScheduler configured for name: " + scheduledAnnotation.scheduler()));

            Runnable task = () -> {
                io.micronaut.context.Qualifier<Object> qualifer = beanDefinition
                    .getAnnotationTypeByStereotype(Qualifier.class)
                    .map(type -> Qualifiers.byAnnotation(beanDefinition, type))
                    .orElse(null);

                Class<Object> beanType = (Class<Object>) beanDefinition.getBeanType();
                Object bean = beanContext.getBean(beanType, qualifer);
                if (method.getArguments().length == 0) {
                    ((ExecutableMethod) method).invoke(bean);
                }
            };

            String cronExpr = scheduledAnnotation.cron();
            if (StringUtils.isNotEmpty(cronExpr)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling cron task [{}] for method: {}", cronExpr, method);
                }
                taskScheduler.schedule(cronExpr, task);
            } else if (StringUtils.isNotEmpty(fixedRate)) {
                Optional<Duration> converted = conversionService.convert(fixedRate, Duration.class);
                Duration duration = converted.orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid fixed rate definition: " + fixedRate)
                );

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling fixed rate task [{}] for method: {}", duration, method);
                }

                ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleAtFixedRate(initialDelay, duration, task);
                scheduledTasks.add(scheduledFuture);
            } else {
                String fixedDelay = scheduledAnnotation.fixedDelay();
                if (StringUtils.isNotEmpty(fixedDelay)) {
                    Optional<Duration> converted = conversionService.convert(fixedDelay, Duration.class);
                    Duration duration = converted.orElseThrow(() ->
                        new SchedulerConfigurationException(method, "Invalid fixed delay definition: " + fixedDelay)
                    );


                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Scheduling fixed delay task [{}] for method: {}", duration, method);
                    }

                    ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleWithFixedDelay(initialDelay, duration, task);
                    scheduledTasks.add(scheduledFuture);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (ScheduledFuture<?> scheduledTask : scheduledTasks) {
            if (!scheduledTask.isCancelled()) {
                scheduledTask.cancel(false);
            }
        }
    }
}
