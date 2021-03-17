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

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Type;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.async.AsyncInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that can be applied to any method that returns void or an instance of {@link java.util.concurrent.CompletionStage} to indicate the actual execution should occur
 * on the given thread pool.
 *
 * <p>Additional thread pools can be configured with the {@code micronaut.executors} configuration.</p>
 *
 * @see io.micronaut.scheduling.executor.UserExecutorConfiguration
 * @see TaskExecutors
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Executable
@Around
@Type(AsyncInterceptor.class)
public @interface Async {
    /**
     * The name of the executor service to execute the task on. Defaults to {@link TaskExecutors#SCHEDULED}
     *
     * @return The name of the thread pool
     */
    String value() default TaskExecutors.SCHEDULED;
}
