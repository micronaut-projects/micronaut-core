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
package io.micronaut.scheduling.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.scheduling.TaskExecutors;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation for scheduling a re-occurring task.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Executable(processOnStartup = true)
@Repeatable(Schedules.class)
@Parallel
public @interface Scheduled {

    /**
     * @return The CRON expression
     */
    String cron() default "";

    /**
     * A String representation of the {@link java.time.Duration} between the time of the last execution and the
     * beginning of the next. For example 10m == 10 minutes
     *
     * @return The fixed delay
     */
    String fixedDelay() default "";

    /**
     * A String representation of the {@link java.time.Duration} before starting executions. For example
     * 10m == 10 minutes
     *
     * @return The fixed delay
     */
    String initialDelay() default "";

    /**
     * A String representation of the {@link java.time.Duration} between executions. For example 10m == 10 minutes
     *
     * @return The fixed rate
     */
    String fixedRate() default "";

    /**
     * @return The name of a {@link jakarta.inject.Named} bean that is a
     * {@link java.util.concurrent.ScheduledExecutorService} to use to schedule the task
     */
    String scheduler() default TaskExecutors.SCHEDULED;

    /**
     * The type of bean which properties can be referenced
     * in other annotation members like {@link #cronProperty()}.
     * The bean owning specified properties should be present within context for
     * the property value to be resolved
     *
     * @since 3.4.0
     * @return the bean to be used for property resolving
     */
    Class<?> bean() default void.class;

    /**
     * The property of {@link #bean()} containing CRON expression.
     * Only to be used in conjunction with {@link #bean()}.
     *
     * @since 3.4.0
     * @return name of bean property containing CRON expression
     */
    String cronProperty() default "";

    /**
     * The property of {@link #bean()} containing a {@link java.time.Duration} or a
     * String representation of duration between the time of the last execution and the
     * beginning of the next.
     * Only to be used in conjunction with {@link #bean()}
     *
     * @since 3.4.0
     * @return name of bean property containing fixed delay
     */
    String fixedDelayProperty() default "";

    /**
     * The property of {@link #bean()} containing a {@link java.time.Duration} or a
     * String representation of duration before starting executions.
     * Only to be used in conjunction with {@link #bean()}
     *
     * @since 3.4.0
     * @return name of bean property containing initial delay
     */
    String initialDelayProperty() default "";

    /**
     * The property of {@link #bean()} containing a {@link java.time.Duration} or a
     * String representation of duration between executions.
     * Only to be used in conjunction with {@link #bean()}
     *
     * @since 3.4.0
     * @return name of bean property containing fixed rate
     */
    String fixedRateProperty() default "";
}
