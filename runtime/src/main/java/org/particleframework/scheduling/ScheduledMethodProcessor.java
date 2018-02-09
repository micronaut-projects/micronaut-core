/*
 * Copyright 2018 original authors
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
package org.particleframework.scheduling;

import org.particleframework.context.BeanContext;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.scheduling.annotation.Scheduled;
import org.particleframework.scheduling.exceptions.SchedulerConfigurationException;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * A {@link ExecutableMethodProcessor} for the {@link Scheduled} annotation
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class ScheduledMethodProcessor implements ExecutableMethodProcessor<Scheduled>, Closeable{

    private final BeanContext beanContext;
    private final ConversionService<?> conversionService;
    private final Queue<ScheduledFuture<?>> scheduledTasks = new ConcurrentLinkedDeque<>();

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
            long initialDelay = 0;
            if(StringUtils.hasText(initialDelayStr)) {
                initialDelay = conversionService.convert(initialDelayStr, Duration.class).map(Duration::toMillis).orElseThrow(()->
                        new SchedulerConfigurationException(method, "Invalid initial delay definition: " + initialDelayStr)
                );
            }


            ExecutorService executorService = beanContext.findBean(ExecutorService.class, Qualifiers.byName(scheduledAnnotation.scheduler()))
                    .orElseThrow(() -> new SchedulerConfigurationException(method, "No scheduler of type ScheduledExecutorService configured for name: " + scheduledAnnotation.scheduler()));


            Runnable task = () -> {
                org.particleframework.context.Qualifier<Object> qualifer = beanDefinition.getAnnotationTypeByStereotype(Qualifier.class)
                        .map(type -> Qualifiers.byAnnotation(beanDefinition, type)).orElse(null);
                Class<Object> beanType = (Class<Object>) beanDefinition.getBeanType();
                Object bean = beanContext.getBean(beanType, qualifer);
                if (method.getArguments().length == 0) {
                    ((ExecutableMethod) method).invoke(bean);
                }
            };

            ScheduledExecutorService scheduledExecutorService;

            if(executorService instanceof ScheduledExecutorService) {
                scheduledExecutorService = (ScheduledExecutorService) executorService;
            }
            else {
                throw new SchedulerConfigurationException(method, "No scheduler of type ScheduledExecutorService configured for name: " + scheduledAnnotation.scheduler());
            }

            if(StringUtils.isNotEmpty(fixedRate)) {
                Optional<Duration> converted = conversionService.convert(fixedRate, Duration.class);
                Duration duration = converted.orElseThrow(()->
                    new SchedulerConfigurationException(method, "Invalid fixed rate definition: " + fixedRate)
                );


                ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                        task,
                        initialDelay,
                        duration.toMillis(),
                        TimeUnit.MILLISECONDS
                );
                scheduledTasks.add(scheduledFuture);
            }
            else {
                String fixedDelay = scheduledAnnotation.fixedDelay();
                if(StringUtils.isNotEmpty(fixedDelay)) {
                    Optional<Duration> converted = conversionService.convert(fixedDelay, Duration.class);
                    Duration duration = converted.orElseThrow(()->
                            new SchedulerConfigurationException(method, "Invalid fixed delay definition: " + fixedDelay)
                    );


                    ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                            task,
                            initialDelay,
                            duration.toMillis(),
                            TimeUnit.MILLISECONDS
                    );
                    scheduledTasks.add(scheduledFuture);

                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (ScheduledFuture<?> scheduledTask : scheduledTasks) {
            if( !scheduledTask.isCancelled()) {
                scheduledTask.cancel(false);
            }
        }
    }
}
