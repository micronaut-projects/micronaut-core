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

import io.micronaut.context.BeanContext;
import io.micronaut.context.bind.DefaultExecutableBeanContextBinder;
import io.micronaut.context.bind.ExecutableBeanContextBinder;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.context.watch.BeanExecutableMethod;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.ScheduledExecution;
import io.micronaut.scheduling.ScheduledExecutorTaskScheduler;
import io.micronaut.scheduling.TaskExceptionHandler;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A {@link ExecutableMethodProcessor} for the {@link Scheduled} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Singleton
public class ScheduledMethodProcessor implements ExecutableMethodProcessor<Scheduled>, ExecutableMethodWatcher<Scheduled>, Closeable {

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
    /**
     * The futures of each scheduled method, so that a method that comes back in a new generation with the
     * same schedule keeps its running timers, and one that goes has its timers cancelled.
     */
    private final Map<BeanExecutableMethod<Scheduled>, ScheduledMethod> scheduledTasks = new ConcurrentHashMap<>();
    private volatile @Nullable BeanWatch watch; // NOSONAR java:S3077 only the handle is published; the watch is thread-safe
    private volatile @Nullable BeanWatch configurationWatch; // NOSONAR java:S3077 only the handle is published; the watch is thread-safe
    /**
     * Serializes the batches of the method watch and of the configuration watch, which may be delivered on different
     * threads: both cancel and create timers.
     */
    private final Object changeLock = new Object();
    /**
     * Whether the processor was closed, after which it registers no watch and keeps no timer. Set before the timers
     * held are cancelled, under the processor's monitor.
     */
    private volatile boolean closed;
    /**
     * What scheduling the startup batch threw, if anything: the watch isolates a watcher's failure, and a
     * schedule that cannot be read must still fail the startup as it always has.
     */
    private volatile @Nullable RuntimeException startupFailure; // NOSONAR java:S3077 only the reference is published, the exception is not mutated
    private final TaskExceptionHandler<?, ?> taskExceptionHandler;

    /**
     * @param beanContext       The bean context for DI of beans annotated with @Inject
     * @param conversionService To convert one type to another
     * @param taskExceptionHandler The default task exception handler
     */
    public ScheduledMethodProcessor(BeanContext beanContext, ConversionService conversionService, TaskExceptionHandler<?, ?> taskExceptionHandler) {
        this.beanContext = beanContext;
        this.conversionService = conversionService;
        this.taskExceptionHandler = taskExceptionHandler;
    }

    /**
     * Does nothing: the methods are watched, not processed.
     *
     * @param beanDefinition The bean definition to process
     * @param method The executable method
     * @param <B> The bean type
     * @deprecated The processor watches the {@link Scheduled} methods through
     * {@link io.micronaut.context.WatchableBeanContext#methods(Class)} since 5.3.0
     */
    @Override
    @Deprecated(since = "5.3.0", forRemoval = true)
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        // the watch registered at startup delivers every scheduled method, this one included
    }

    /**
     * On startup, registers the watch: its first batch is every scheduled method present, and the later
     * ones are what a reload retires and adds.
     *
     * @param ignore The startup event.
     */
    @EventListener
    void scheduleTasks(StartupEvent ignore) {
        if (beanContext instanceof WatchableBeanContext watchable) {
            watch = watchable.methods(Scheduled.class).watch(this);
            RuntimeException failure = startupFailure;
            if (failure != null) {
                startupFailure = null;
                throw failure;
            }
        }
    }

    @Override
    public void onChange(ExecutableMethodChange<Scheduled> change) {
        synchronized (changeLock) {
            applyChange(change);
        }
    }

    private void applyChange(ExecutableMethodChange<Scheduled> change) {
        for (BeanExecutableMethod<Scheduled> gone : change.removed()) {
            ScheduledMethod scheduled = scheduledTasks.remove(gone);
            if (scheduled == null) {
                continue;
            }
            Optional<BeanExecutableMethod<Scheduled>> back = change.replacementOf(gone);
            if (back.isPresent() && sameSchedule(gone, back.get()) && scheduled.schedules().equals(resolveSchedules(back.get()))) {
                // the same schedule in the new generation, as written and as resolved, on the same schedulers: the
                // running timers stay, and from now on invoke the replacement method on the new generation's bean
                scheduled.retarget(back.get());
                scheduledTasks.put(back.get(), scheduled);
            } else {
                scheduled.cancel();
            }
        }
        try {
            (change.initial() ? change.added().parallelStream() : change.added().stream())
                .filter(entry -> !scheduledTasks.containsKey(entry))
                .forEach(this::scheduleTask);
        } catch (RuntimeException e) {
            if (change.initial()) {
                // surfaced by the startup listener once the watch is registered, as it always failed the startup
                startupFailure = e;
                return;
            }
            throw e;
        }
    }

    private static boolean sameSchedule(BeanExecutableMethod<Scheduled> before, BeanExecutableMethod<Scheduled> after) {
        return before.method().getAnnotationValuesByType(Scheduled.class).equals(after.method().getAnnotationValuesByType(Scheduled.class));
    }

    /**
     * After a configuration change, schedules again the methods whose schedules no longer resolve as they did when
     * their timers were created, such as {@code fixedRate = "${x.rate}"} once {@code x.rate} changed.
     */
    private ReloadingConfigurationWatcher.Outcome rescheduleChanged() {
        boolean rescheduled = false;
        synchronized (changeLock) {
            for (Map.Entry<BeanExecutableMethod<Scheduled>, ScheduledMethod> held : List.copyOf(scheduledTasks.entrySet())) {
                BeanExecutableMethod<Scheduled> entry = held.getKey();
                ScheduledMethod scheduled = held.getValue();
                if (scheduled.schedules().equals(resolveSchedules(entry))) {
                    continue;
                }
                // the new timers first: a schedule that no longer reads keeps the timers it has, and the next
                // change compares against them again
                ScheduledMethod replacement = new ScheduledMethod(entry);
                try {
                    scheduleTimers(entry, replacement);
                } catch (RuntimeException e) {
                    replacement.cancel();
                    LOG.error("Failed to schedule method {} again after a configuration change, its timers are kept: {}", entry.method(), e.getMessage(), e);
                    continue;
                }
                if (scheduledTasks.replace(entry, scheduled, replacement)) {
                    scheduled.cancel();
                    rescheduled = true;
                    if (closed) {
                        // a close that began before the replacement was installed may have cancelled the old timers only
                        replacement.cancel();
                    }
                } else {
                    // closed, or removed by a reload, meanwhile
                    replacement.cancel();
                }
            }
        }
        return rescheduled ? ReloadingConfigurationWatcher.Outcome.APPLIED : ReloadingConfigurationWatcher.Outcome.IGNORED;
    }

    /**
     * Watches the configuration once a schedule reads it through a placeholder, {@code fixedRate = "${x.rate}"}:
     * such a schedule changes with the configuration and nothing else, and one written out in full never does.
     *
     * @param annotation The schedule
     */
    private void watchConfigurationFor(AnnotationValue<Scheduled> annotation) {
        if (configurationWatch != null || !(beanContext instanceof WatchableBeanContext watchable) || !readsConfiguration(annotation)) {
            return;
        }
        synchronized (this) {
            if (configurationWatch == null && !closed) {
                configurationWatch = watchable.configuration().watchReloading(change -> rescheduleChanged());
            }
        }
    }

    private static boolean readsConfiguration(AnnotationValue<Scheduled> annotation) {
        for (Object value : annotation.getValues().values()) {
            if (value != null && value.toString().contains("${")) {
                return true;
            }
        }
        return false;
    }

    /**
     * How the schedules of a method resolve now: its members with their placeholders resolved, and the scheduler
     * each one names.
     *
     * @param entry The method
     * @return The schedules, or null when one cannot be resolved, which never equals the schedules of timers
     */
    private @Nullable List<Schedule> resolveSchedules(BeanExecutableMethod<Scheduled> entry) {
        try {
            List<AnnotationValue<Scheduled>> annotations = entry.method().getAnnotationValuesByType(Scheduled.class);
            List<Schedule> schedules = new ArrayList<>(annotations.size());
            for (AnnotationValue<Scheduled> annotation : annotations) {
                SchedulerBean scheduler = findScheduler(annotation).orElse(null);
                if (scheduler == null) {
                    return null;
                }
                schedules.add(Schedule.of(annotation, scheduler.identity()));
            }
            return schedules;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The scheduler a schedule names: a {@link TaskScheduler} bean of the name, or else a
     * {@link ScheduledExecutorService} bean of the name, wrapped.
     */
    private Optional<SchedulerBean> findScheduler(AnnotationValue<Scheduled> annotation) {
        String scheduler = annotation.stringValue(MEMBER_SCHEDULER).orElse(TaskExecutors.SCHEDULED);
        Optional<SchedulerBean> found = beanContext.findBean(TaskScheduler.class, Qualifiers.byName(scheduler))
            .map(taskScheduler -> new SchedulerBean(taskScheduler, taskScheduler));
        if (found.isEmpty()) {
            found = beanContext.findBean(ExecutorService.class, Qualifiers.byName(scheduler))
                .filter(ScheduledExecutorService.class::isInstance)
                .map(executor -> new SchedulerBean(new ScheduledExecutorTaskScheduler(executor), executor));
        }
        return found;
    }

    private void scheduleTask(BeanExecutableMethod<Scheduled> entry) {
        ScheduledMethod scheduled = new ScheduledMethod(entry);
        // registered first, so that a close while the timers are being created cancels them
        scheduledTasks.put(entry, scheduled);
        try {
            scheduleTimers(entry, scheduled);
        } catch (RuntimeException | Error e) {
            // a method with a schedule that failed keeps none of its timers, nor counts as scheduled: the next
            // batch that adds it schedules it again
            scheduledTasks.remove(entry, scheduled);
            scheduled.cancel();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <B> void scheduleTimers(BeanExecutableMethod<Scheduled> entry, ScheduledMethod scheduled) {
        ExecutableMethod<B, ?> method = (ExecutableMethod<B, ?>) entry.method();
        List<AnnotationValue<Scheduled>> scheduledAnnotations = method.getAnnotationValuesByType(Scheduled.class);
        List<Schedule> schedules = new ArrayList<>(scheduledAnnotations.size());
        // every schedule is read before any timer starts, so that one that cannot be read starts none
        List<Supplier<ScheduledFuture<?>>> timers = new ArrayList<>(scheduledAnnotations.size());
        for (AnnotationValue<Scheduled> scheduledAnnotation : scheduledAnnotations) {
            String fixedRate = scheduledAnnotation.stringValue(MEMBER_FIXED_RATE).orElse(null);

            String initialDelayStr = scheduledAnnotation.stringValue(MEMBER_INITIAL_DELAY).orElse(null);
            final Duration initialDelay;
            if (StringUtils.hasText(initialDelayStr)) {
                initialDelay = conversionService.convert(initialDelayStr, Duration.class).orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid initial delay definition: " + initialDelayStr)
                );
            } else {
                initialDelay = null;
            }

            SchedulerBean schedulerBean = findScheduler(scheduledAnnotation).orElseThrow(() -> new SchedulerConfigurationException(method,
                "No scheduler of type TaskScheduler configured for name: " + scheduledAnnotation.stringValue(MEMBER_SCHEDULER).orElse(TaskExecutors.SCHEDULED)));
            TaskScheduler taskScheduler = schedulerBean.scheduler();
            // what the timers below were created from, which a reload or a configuration change compares against
            schedules.add(Schedule.of(scheduledAnnotation, schedulerBean.identity()));
            watchConfigurationFor(scheduledAnnotation);
            Runnable task = () -> {
                // the method and the definition of the moment: a timer kept across a generation invokes the replacement
                BeanExecutableMethod<Scheduled> current = scheduled.target();
                ExecutableMethod<B, ?> currentMethod = (ExecutableMethod<B, ?>) current.method();
                BeanDefinition<B> currentDefinition = (BeanDefinition<B>) current.definition();
                try {
                    ExecutableBeanContextBinder binder = new DefaultExecutableBeanContextBinder();
                    // a ScheduledExecution argument is not a bean: it is this invocation, supplied once it exists
                    ExecutionAwareExecutable<B> executable = new ExecutionAwareExecutable<>(currentMethod);
                    BoundExecutable<B, ?> boundExecutable = binder.bind(executable, beanContext);
                    @Nullable Object[] arguments = executable.arguments(boundExecutable.getBoundArguments());
                    B bean = beanContext.getBean(currentDefinition);
                    AnnotationValue<Scheduled> finalAnnotationValue = scheduledAnnotation;
                    if (finalAnnotationValue instanceof EvaluatedAnnotationValue<Scheduled> evaluated) {
                        finalAnnotationValue = evaluated.withArguments(bean, arguments);
                    }
                    boolean shouldRun = finalAnnotationValue.booleanValue(MEMBER_CONDITION).orElse(true);
                    if (shouldRun) {
                        // tells an interceptor of the method that the scheduler invoked it, and by which schedule
                        ScheduledExecution execution = new ScheduledExecution(currentMethod, finalAnnotationValue);
                        // created for this invocation alone, so the method receives the execution of this call
                        executable.supply(arguments, execution);
                        try {
                            // a block, so that it is the Runnable overload: the result of the method is not used
                            PropagatedContext.getOrEmpty().plus(execution).propagate(() -> {
                                currentMethod.invoke(bean, arguments);
                            });
                        } catch (Throwable e) {
                            handleException(currentDefinition.getBeanType(), bean, e);
                        }
                    }
                } catch (NoSuchBeanException noSuchBeanException) {
                    // ignore: a timing issue can occur when the context is being shutdown. If a scheduled job runs and the context
                    // is shutdown and available beans cleared then the bean is no longer available. The best thing to do here is just ignore the failure.
                    LOG.debug("Scheduled job skipped for context shutdown: {}.{}", currentDefinition.getBeanType().getSimpleName(), currentMethod.getDescription(true));
                } catch (Exception e) {
                    TaskExceptionHandler<B, Throwable> finalHandler = findHandler(currentDefinition.getBeanType(), e);
                    finalHandler.handleCreationFailure(currentDefinition, e);
                }
            };

            String cronExpr = scheduledAnnotation.stringValue(MEMBER_CRON).orElse(null);
            String zoneIdStr = scheduledAnnotation.stringValue(MEMBER_ZONE_ID).orElse(null);
            String fixedDelay = scheduledAnnotation.stringValue(MEMBER_FIXED_DELAY).orElse(null);

            if (StringUtils.isNotEmpty(cronExpr)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling cron task [{}] for method: {}", cronExpr, method);
                }

                timers.add(() -> taskScheduler.schedule(cronExpr, zoneIdStr, task));
            } else if (StringUtils.isNotEmpty(fixedRate)) {
                Optional<Duration> converted = conversionService.convert(fixedRate, Duration.class);
                Duration duration = converted.orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid fixed rate definition: " + fixedRate)
                );

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling fixed rate task [{}] for method: {}", duration, method);
                }

                timers.add(() -> taskScheduler.scheduleAtFixedRate(initialDelay, duration, task));
            } else if (StringUtils.isNotEmpty(fixedDelay)) {
                Optional<Duration> converted = conversionService.convert(fixedDelay, Duration.class);
                Duration duration = converted.orElseThrow(() ->
                    new SchedulerConfigurationException(method, "Invalid fixed delay definition: " + fixedDelay)
                );

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling fixed delay task [{}] for method: {}", duration, method);
                }

                timers.add(() -> taskScheduler.scheduleWithFixedDelay(initialDelay, duration, task));
            } else if (initialDelay != null) {
                timers.add(() -> taskScheduler.schedule(initialDelay, task));
            } else {
                throw new SchedulerConfigurationException(method, "Failed to schedule task. Invalid definition");
            }
        }
        for (Supplier<ScheduledFuture<?>> timer : timers) {
            scheduled.add(timer.get());
        }
        scheduled.schedules(schedules);
    }

    private <B> void handleException(Class<B> beanType, B bean, Throwable e) {
        TaskExceptionHandler<B, Throwable> finalHandler = findHandler(beanType, e);
        finalHandler.handle(bean, e);
    }

    @SuppressWarnings("unchecked")
    private <B> TaskExceptionHandler<B, Throwable> findHandler(Class<B> beanType, Throwable e) {
        return beanContext.findBean(Argument.of(TaskExceptionHandler.class, beanType, e.getClass()))
                          .orElse(this.taskExceptionHandler);
    }

    @Override
    @PreDestroy
    public void close() {
        BeanWatch registered = watch;
        if (registered != null) {
            registered.close();
            watch = null;
        }
        synchronized (this) {
            closed = true;
            BeanWatch configuration = configurationWatch;
            if (configuration != null) {
                configuration.close();
                configurationWatch = null;
            }
        }
        try {
            for (ScheduledMethod scheduled : scheduledTasks.values()) {
                scheduled.cancel();
            }
        } finally {
            scheduledTasks.clear();
        }
    }

    /**
     * @return How many scheduled methods hold timers
     */
    int scheduledMethods() {
        return scheduledTasks.size();
    }

    /**
     * A scheduled method as the bean context binder sees it: without its {@link ScheduledExecution} arguments,
     * which no bean satisfies and which are supplied for each invocation instead.
     *
     * @param <B> The bean type
     */
    private static final class ExecutionAwareExecutable<B> implements Executable<B, Object> {

        private final ExecutableMethod<B, ?> method;
        private final Argument<?>[] beanArguments;

        ExecutionAwareExecutable(ExecutableMethod<B, ?> method) {
            this.method = method;
            Argument<?>[] declared = method.getArguments();
            List<Argument<?>> bound = new ArrayList<>(declared.length);
            for (Argument<?> argument : declared) {
                if (!isExecution(argument)) {
                    bound.add(argument);
                }
            }
            this.beanArguments = bound.size() == declared.length ? declared : bound.toArray(Argument.ZERO_ARGUMENTS);
        }

        private static boolean isExecution(Argument<?> argument) {
            return argument.getType() == ScheduledExecution.class;
        }

        /**
         * The arguments of the method, with the slots of the execution left empty.
         *
         * @param bound The arguments the binder resolved, in the order of {@link #getArguments()}
         * @return The arguments of the method
         */
        @Nullable Object[] arguments(@Nullable Object[] bound) {
            Argument<?>[] declared = method.getArguments();
            if (bound.length == declared.length) {
                return bound;
            }
            @Nullable Object[] arguments = new Object[declared.length];
            int next = 0;
            for (int i = 0; i < declared.length; i++) {
                if (!isExecution(declared[i])) {
                    arguments[i] = bound[next++];
                }
            }
            return arguments;
        }

        /**
         * Fills the slots of the execution.
         *
         * @param arguments The arguments of the method
         * @param execution The execution of this invocation
         */
        void supply(@Nullable Object[] arguments, ScheduledExecution execution) {
            Argument<?>[] declared = method.getArguments();
            for (int i = 0; i < declared.length; i++) {
                if (isExecution(declared[i])) {
                    arguments[i] = execution;
                }
            }
        }

        @Override
        public Class<B> getDeclaringType() {
            return method.getDeclaringType();
        }

        @Override
        public Argument<?>[] getArguments() {
            return beanArguments;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }

        @Override
        public @Nullable Object invoke(B instance, @Nullable Object... arguments) {
            throw new UnsupportedOperationException("Invoked by the scheduler with the execution supplied");
        }
    }

    /**
     * The timers of one scheduled method and the method they invoke, which a replacement updates.
     */
    private static final class ScheduledMethod {
        /**
         * The timers, guarded by this method's monitor: a close may cancel them while they are being created.
         */
        private final List<ScheduledFuture<?>> futures = new ArrayList<>(1);
        private boolean cancelled;
        private List<Schedule> schedules = List.of();
        private final AtomicReference<BeanExecutableMethod<Scheduled>> target;

        ScheduledMethod(BeanExecutableMethod<Scheduled> entry) {
            this.target = new AtomicReference<>(entry);
        }

        BeanExecutableMethod<Scheduled> target() {
            return Objects.requireNonNull(target.get());
        }

        void retarget(BeanExecutableMethod<Scheduled> entry) {
            target.set(entry);
        }

        /**
         * Adds a timer, or cancels it at once when the method was cancelled while its timers were being created.
         *
         * @param future The timer
         */
        synchronized void add(ScheduledFuture<?> future) {
            if (cancelled) {
                future.cancel(false);
            } else {
                futures.add(future);
            }
        }

        synchronized List<Schedule> schedules() {
            return schedules;
        }

        synchronized void schedules(List<Schedule> resolved) {
            this.schedules = List.copyOf(resolved);
        }

        synchronized void cancel() {
            cancelled = true;
            for (ScheduledFuture<?> future : futures) {
                if (!future.isCancelled()) {
                    future.cancel(false);
                }
            }
            futures.clear();
        }
    }

    /**
     * A scheduler bean, and the bean that identifies it: the {@link TaskScheduler}, or the executor it wraps.
     *
     * @param scheduler The scheduler
     * @param identity The bean
     */
    private record SchedulerBean(TaskScheduler scheduler, Object identity) {
    }

    /**
     * A schedule as it resolved when its timer was created: the members with their placeholders resolved, and the
     * scheduler bean, compared by identity, that the timer runs on.
     *
     * @param cron The cron expression
     * @param zoneId The zone of the cron expression
     * @param fixedRate The fixed rate
     * @param fixedDelay The fixed delay
     * @param initialDelay The initial delay
     * @param scheduler The scheduler bean
     */
    private record Schedule(@Nullable String cron, @Nullable String zoneId, @Nullable String fixedRate, @Nullable String fixedDelay,
                            @Nullable String initialDelay, Object scheduler) {

        static Schedule of(AnnotationValue<Scheduled> annotation, Object scheduler) {
            return new Schedule(annotation.stringValue(MEMBER_CRON).orElse(null), annotation.stringValue(MEMBER_ZONE_ID).orElse(null),
                annotation.stringValue(MEMBER_FIXED_RATE).orElse(null), annotation.stringValue(MEMBER_FIXED_DELAY).orElse(null),
                annotation.stringValue(MEMBER_INITIAL_DELAY).orElse(null), scheduler);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Schedule other && Objects.equals(cron, other.cron) && Objects.equals(zoneId, other.zoneId)
                && Objects.equals(fixedRate, other.fixedRate) && Objects.equals(fixedDelay, other.fixedDelay)
                && Objects.equals(initialDelay, other.initialDelay) && scheduler == other.scheduler;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cron, zoneId, fixedRate, fixedDelay, initialDelay, System.identityHashCode(scheduler));
        }
    }
}
