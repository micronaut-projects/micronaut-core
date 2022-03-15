/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.processor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.ScheduledExecutorTaskScheduler;
import io.micronaut.scheduling.TaskExceptionHandler;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
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
    private static final String MEMBER_BEAN = "bean";
    private static final String MEMBER_FIXED_RATE = "fixedRate";
    private static final String MEMBER_FIXED_RATE_PROPERTY = "fixedRateProperty";
    private static final String MEMBER_INITIAL_DELAY = "initialDelay";
    private static final String MEMBER_INITIAL_DELAY_PROPERTY = "initialDelayProperty";
    private static final String MEMBER_CRON = "cron";
    private static final String MEMBER_CRON_PROPERTY = "cronProperty";
    private static final String MEMBER_FIXED_DELAY = "fixedDelay";
    private static final String MEMBER_FIXED_DELAY_PROPERTY = "fixedDelayProperty";
    private static final String MEMBER_SCHEDULER = "scheduler";

    private final BeanContext beanContext;
    private final ConversionService<?> conversionService;
    private final Queue<ScheduledFuture<?>> scheduledTasks = new ConcurrentLinkedDeque<>();
    private final TaskExceptionHandler<?, ?> taskExceptionHandler;

    /**
     * @param beanContext       The bean context for DI of beans annotated with @Inject
     * @param conversionService To convert one type to another
     * @param taskExceptionHandler The default task exception handler
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ScheduledMethodProcessor(BeanContext beanContext, Optional<ConversionService<?>> conversionService, TaskExceptionHandler<?, ?> taskExceptionHandler) {
        this.beanContext = beanContext;
        this.conversionService = conversionService.orElse(ConversionService.SHARED);
        this.taskExceptionHandler = taskExceptionHandler;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        if (!(beanContext instanceof ApplicationContext)) {
            return;
        }

        List<AnnotationValue<Scheduled>> scheduledAnnotations = method.getAnnotationValuesByType(Scheduled.class);
        for (AnnotationValue<Scheduled> scheduledAnnotation : scheduledAnnotations) {

            String scheduler = scheduledAnnotation.get(MEMBER_SCHEDULER, String.class).orElse(TaskExecutors.SCHEDULED);
            Optional<TaskScheduler> optionalTaskScheduler = beanContext
                    .findBean(TaskScheduler.class, Qualifiers.byName(scheduler));

            if (!optionalTaskScheduler.isPresent()) {
                optionalTaskScheduler = beanContext.findBean(ExecutorService.class, Qualifiers.byName(scheduler))
                        .filter(ScheduledExecutorService.class::isInstance)
                        .map(ScheduledExecutorTaskScheduler::new);
            }

            TaskScheduler taskScheduler = optionalTaskScheduler.orElseThrow(() -> new SchedulerConfigurationException(method, "No scheduler of type TaskScheduler configured for name: " + scheduler));

            Runnable task = () -> {
                io.micronaut.context.Qualifier<Object> qualifer = beanDefinition
                    .getAnnotationTypeByStereotype(AnnotationUtil.QUALIFIER)
                    .map(type -> Qualifiers.byAnnotation(beanDefinition, type))
                    .orElse(null);

                Class<Object> beanType = (Class<Object>) beanDefinition.getBeanType();
                Object bean = null;
                try {
                    bean = beanContext.getBean(beanType, qualifer);
                    if (method.getArguments().length == 0) {
                        ((ExecutableMethod) method).invoke(bean);
                    }
                } catch (Throwable e) {
                    io.micronaut.context.Qualifier<TaskExceptionHandler> qualifier = Qualifiers.byTypeArguments(beanType, e.getClass());
                    Collection<BeanDefinition<TaskExceptionHandler>> definitions = beanContext.getBeanDefinitions(TaskExceptionHandler.class, qualifier);
                    Optional<BeanDefinition<TaskExceptionHandler>> mostSpecific = definitions.stream().filter(def -> {
                        List<Argument<?>> typeArguments = def.getTypeArguments(TaskExceptionHandler.class);
                        if (typeArguments.size() == 2) {
                            return typeArguments.get(0).getType() == beanType && typeArguments.get(1).getType() == e.getClass();
                        }
                        return false;
                    }).findFirst();

                    TaskExceptionHandler finalHandler = mostSpecific.map(bd -> beanContext.getBean(bd.getBeanType(), qualifier)).orElse(this.taskExceptionHandler);
                    finalHandler.handle(bean, e);
                }
            };

            Duration initialDelay = null;
            if (scheduledAnnotation.contains(MEMBER_INITIAL_DELAY) || containsProperty(scheduledAnnotation,
                MEMBER_INITIAL_DELAY_PROPERTY)) {
                initialDelay = getDuration(scheduledAnnotation,
                    method,
                    MEMBER_INITIAL_DELAY,
                    MEMBER_INITIAL_DELAY_PROPERTY,
                    "Invalid initial delay definition: ");
            }

            if (scheduledAnnotation.contains(MEMBER_CRON) || containsProperty(scheduledAnnotation, MEMBER_CRON_PROPERTY)) {
                String cronExpr = scheduledAnnotation.get(MEMBER_CRON, String.class)
                        .orElse((String) getAnnotationPropertyValue(scheduledAnnotation, MEMBER_CRON_PROPERTY)
                                .filter(propertyValue -> propertyValue instanceof String).orElse(null));

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling cron task [{}] for method: {}", cronExpr, method);
                }
                taskScheduler.schedule(cronExpr, task);
            } else if (scheduledAnnotation.contains(MEMBER_FIXED_RATE) || containsProperty(scheduledAnnotation, MEMBER_FIXED_RATE_PROPERTY)) {

                Duration fixedRateInterval = getDuration(scheduledAnnotation,
                    method,
                    MEMBER_FIXED_RATE,
                    MEMBER_FIXED_RATE_PROPERTY,
                    "Invalid fixed delay definition: ");

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling fixed rate task [{}] for method: {}", fixedRateInterval, method);
                }

                ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleAtFixedRate(initialDelay, fixedRateInterval, task);
                scheduledTasks.add(scheduledFuture);
            } else if (scheduledAnnotation.contains(MEMBER_FIXED_DELAY) || containsProperty(scheduledAnnotation, MEMBER_FIXED_DELAY_PROPERTY)) {

                Duration fixedDelay = getDuration(scheduledAnnotation,
                    method,
                    MEMBER_FIXED_DELAY,
                    MEMBER_FIXED_DELAY_PROPERTY,
                    "Invalid fixed delay definition: ");

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling fixed delay task [{}] for method: {}", fixedDelay, method);
                }

                ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleWithFixedDelay(initialDelay, fixedDelay, task);
                scheduledTasks.add(scheduledFuture);
            } else if (initialDelay != null) {
                ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(initialDelay, task);
                scheduledTasks.add(scheduledFuture);
            } else {
                throw new SchedulerConfigurationException(method, "Failed to schedule task. Invalid definition");
            }
        }
    }

    private boolean containsProperty(AnnotationValue<Scheduled> annotation, String propertyMember) {
        return annotation.contains(MEMBER_BEAN) && annotation.contains(propertyMember);
    }

    private Duration getDuration(AnnotationValue<Scheduled> annotation,
                                 ExecutableMethod<?, ?> method,
                                 String durationMember,
                                 String durationPropertyMember,
                                 String exceptionMessage) {
        Duration duration;
        String durationStr = annotation.get(durationMember, String.class).orElse(null);
        if (durationStr != null) {
            duration = conversionService.convert(durationStr, Duration.class)
                .orElseThrow(() -> new SchedulerConfigurationException(method, exceptionMessage + durationStr));
        } else {
            Optional<?> durationProperty = getAnnotationPropertyValue(annotation, durationPropertyMember);
            duration = durationProperty
                .flatMap(this::convertToDurationIfNecessary)
                .orElseThrow(() -> new SchedulerConfigurationException(method, exceptionMessage + durationProperty.orElse(null)));
        }
        return duration;
    }

    private Optional<?> getAnnotationPropertyValue(AnnotationValue<Scheduled> annotation, String member) {
        return annotation.annotationPropertyReference(member)
                .flatMap(annotationPropertyValue -> annotationPropertyValue.getPropertyOwningType().getType()
                        .flatMap(beanContext::findBean)
                        .flatMap(annotationPropertyValue::getPropertyValue));
    }

    private Optional<Duration> convertToDurationIfNecessary(Object value) {
        if (value instanceof Duration) {
            return Optional.of((Duration) value);
        }
        return conversionService.convert(value, Duration.class);
    }

    @Override
    @PreDestroy
    public void close() {
        for (ScheduledFuture<?> scheduledTask : scheduledTasks) {
            if (!scheduledTask.isCancelled()) {
                scheduledTask.cancel(false);
            }
        }
    }
}
