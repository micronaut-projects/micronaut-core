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
import io.micronaut.context.Qualifier;
import io.micronaut.context.bind.DefaultExecutableBeanContextBinder;
import io.micronaut.context.bind.ExecutableBeanContextBinder;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.event.annotation.EventListener;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link ExecutableMethodProcessor} for the {@link Scheduled} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class ScheduledMethodProcessor implements ExecutableMethodProcessor<Scheduled>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TaskScheduler.class);
    private static final String MEMBER_FIXED_RATE = "fixedRate";
    private static final String MEMBER_INITIAL_DELAY = "initialDelay";
    private static final String MEMBER_CRON = "cron";
    private static final String MEMBER_ZONE_ID = "zoneId";
    private static final String MEMBER_FIXED_DELAY = "fixedDelay";
    private static final String MEMBER_SCHEDULER = "scheduler";
    private static final String MEMBER_CONDITION = "condition";

    private final BeanContext beanContext;
    private final ConversionService conversionService;
    private final Queue<ScheduledFuture<?>> scheduledTasks = new ConcurrentLinkedDeque<>();
    private final Map<ScheduledDefinition, Runnable> scheduledMethods = new ConcurrentHashMap<>();
    private final TaskExceptionHandler<?, ?> taskExceptionHandler;
    private volatile boolean started = false;

    /**
     * @param beanContext       The bean context for DI of beans annotated with @Inject
     * @param conversionService To convert one type to another
     * @param taskExceptionHandler The default task exception handler
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ScheduledMethodProcessor(BeanContext beanContext, Optional<ConversionService> conversionService, TaskExceptionHandler<?, ?> taskExceptionHandler) {
        this.beanContext = beanContext;
        this.conversionService = conversionService.orElse(ConversionService.SHARED);
        this.taskExceptionHandler = taskExceptionHandler;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        if (beanContext instanceof ApplicationContext) {
            ScheduledDefinition scheduledDefinition = new ScheduledDefinition(beanDefinition, method);
            Runnable runnable = new ScheduleTaskRunnable(scheduledDefinition);
            // process may be called during or after scheduleTasks. we need to guard against that.
            if (scheduledMethods.putIfAbsent(scheduledDefinition, runnable) == null && started) {
                runnable.run();
            }
        }
    }

    /**
     * On startup event listener that schedules the active tasks.
     * @param startupEvent The startup event.
     */
    @EventListener
    void scheduleTasks(@SuppressWarnings("unused") StartupEvent startupEvent) {
        started = true;
        for (Runnable runnable : scheduledMethods.values()) {
            runnable.run();
        }
    }

    @SuppressWarnings("unchecked")
    private void scheduleTask(ScheduledDefinition scheduledDefinition) {
        ExecutableMethod<?, ?> method = scheduledDefinition.method();
        BeanDefinition<?> beanDefinition = scheduledDefinition.definition();
        List<AnnotationValue<Scheduled>> scheduledAnnotations = method.getAnnotationValuesByType(Scheduled.class);
        for (AnnotationValue<Scheduled> scheduledAnnotation : scheduledAnnotations) {
            String fixedRate = scheduledAnnotation.stringValue(MEMBER_FIXED_RATE).orElse(null);

            String initialDelayStr = scheduledAnnotation.stringValue(MEMBER_INITIAL_DELAY).orElse(null);
            Duration initialDelay = null;
            if (StringUtils.hasText(initialDelayStr)) {
                initialDelay = conversionService.convert(initialDelayStr, Duration.class).orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid initial delay definition: " + initialDelayStr)
                );
            }

            String scheduler = scheduledAnnotation.stringValue(MEMBER_SCHEDULER).orElse(TaskExecutors.SCHEDULED);
            Optional<TaskScheduler> optionalTaskScheduler = beanContext
                .findBean(TaskScheduler.class, Qualifiers.byName(scheduler));

            if (optionalTaskScheduler.isEmpty()) {
                optionalTaskScheduler = beanContext.findBean(ExecutorService.class, Qualifiers.byName(scheduler))
                    .filter(ScheduledExecutorService.class::isInstance)
                    .map(ScheduledExecutorTaskScheduler::new);
            }

            TaskScheduler taskScheduler = optionalTaskScheduler.orElseThrow(() -> new SchedulerConfigurationException(method, "No scheduler of type TaskScheduler configured for name: " + scheduler));
            Runnable task = () -> {
                try {
                    ExecutableBeanContextBinder binder = new DefaultExecutableBeanContextBinder();
                    BoundExecutable<?, ?> boundExecutable = binder.bind(method, beanContext);
                    Object bean = beanContext.getBean((Argument<Object>) beanDefinition.asArgument(), (Qualifier<Object>) beanDefinition.getDeclaredQualifier());
                    AnnotationValue<Scheduled> finalAnnotationValue = scheduledAnnotation;
                    if (finalAnnotationValue instanceof EvaluatedAnnotationValue<Scheduled> evaluated) {
                        finalAnnotationValue = evaluated.withArguments(bean, boundExecutable.getBoundArguments());
                    }
                    boolean shouldRun = finalAnnotationValue.booleanValue(MEMBER_CONDITION).orElse(true);
                    if (shouldRun) {
                        try {
                            ((BoundExecutable<Object, Object>) boundExecutable).invoke(bean);
                        } catch (Throwable e) {
                            handleException((Class<Object>) beanDefinition.getBeanType(), bean, e);
                        }
                    }
                } catch (NoSuchBeanException noSuchBeanException) {
                    // ignore: a timing issue can occur when the context is being shutdown. If a scheduled job runs and the context
                    // is shutdown and available beans cleared then the bean is no longer available. The best thing to do here is just ignore the failure.
                    LOG.debug("Scheduled job skipped for context shutdown: {}.{}", beanDefinition.getBeanType().getSimpleName(), method.getDescription(true));
                } catch (Exception e) {
                    TaskExceptionHandler finalHandler = findHandler(beanDefinition.getBeanType(), e);
                    finalHandler.handleCreationFailure(beanDefinition, e);
                }
            };

            String cronExpr = scheduledAnnotation.stringValue(MEMBER_CRON).orElse(null);
            String zoneIdStr = scheduledAnnotation.stringValue(MEMBER_ZONE_ID).orElse(null);
            String fixedDelay = scheduledAnnotation.stringValue(MEMBER_FIXED_DELAY).orElse(null);

            if (StringUtils.isNotEmpty(cronExpr)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling cron task [{}] for method: {}", cronExpr, method);
                }

                ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(cronExpr, zoneIdStr, task);
                scheduledTasks.add(scheduledFuture);
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
            } else if (StringUtils.isNotEmpty(fixedDelay)) {
                Optional<Duration> converted = conversionService.convert(fixedDelay, Duration.class);
                Duration duration = converted.orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid fixed delay definition: " + fixedDelay)
                );


                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling fixed delay task [{}] for method: {}", duration, method);
                }

                ScheduledFuture<?> scheduledFuture = taskScheduler.scheduleWithFixedDelay(initialDelay, duration, task);
                scheduledTasks.add(scheduledFuture);
            } else if (initialDelay != null) {
                ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(initialDelay, task);

                scheduledTasks.add(scheduledFuture);
            } else {
                throw new SchedulerConfigurationException(method, "Failed to schedule task. Invalid definition");
            }
        }
    }

    private void handleException(Class<Object> beanType, Object bean, Throwable e) {
        TaskExceptionHandler<Object, Throwable> finalHandler = findHandler(beanType, e);
        finalHandler.handle(bean, e);
    }

    @SuppressWarnings("unchecked")
    private TaskExceptionHandler<Object, Throwable> findHandler(Class<?> beanType, Throwable e) {
        return beanContext.findBean(Argument.of(TaskExceptionHandler.class, beanType, e.getClass()))
                          .orElse(this.taskExceptionHandler);
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            for (ScheduledFuture<?> scheduledTask : scheduledTasks) {
                if (!scheduledTask.isCancelled()) {
                    scheduledTask.cancel(false);
                }
            }
        } finally {
            this.scheduledTasks.clear();
            this.scheduledMethods.clear();
        }
    }

    private record ScheduledDefinition(
        BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method) { }

    /**
     * This Runnable calls {@link #scheduleTask(ScheduledDefinition)} exactly once, even if invoked
     * multiple times from multiple threads.
     */
    private class ScheduleTaskRunnable extends AtomicBoolean implements Runnable {
        private final ScheduledDefinition definition;

        ScheduleTaskRunnable(ScheduledDefinition definition) {
            this.definition = definition;
        }

        @Override
        public void run() {
            if (compareAndSet(false, true)) {
                scheduleTask(definition);
            }
        }
    }
}
