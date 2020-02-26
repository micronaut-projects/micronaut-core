/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>A hint annotation that can be added to {@link Bean} definitions or {@link Executable} methods to indicate that they can be initialized in
 * parallel with the application context.</p>
 *
 * <p>This allows optimized startup time by allowing beans that have slow initialization paths to startup and without impacting the overall startup time of the application.</p>
 *
 * <p>Note that beans and methods that are processed in parallel (unlike {@link Context} scoped beans) will not initially fail the startup of the {@link io.micronaut.context.ApplicationContext}
 * as they may be initialized after the {@link io.micronaut.context.ApplicationContext} has started and cannot participate in {@link io.micronaut.context.event.StartupEvent} processing. If a parallel bean fails to startup it will by default stop the {@link io.micronaut.context.ApplicationContext} with an error unless {@link #shutdownOnError()} set to to {@code false}.</p>
 *
 * <p>Adding {@link Parallel} to methods is most useful when used in conjunction with a {@link io.micronaut.context.processor.ExecutableMethodProcessor}. Be aware however, that the processor in this case should be thread safe as could be executed in parallel for different methods.</p>
 *
 * <p>NOTE: The use of {@link Parallel} generally only makes sense when combined with non {@link Prototype} scopes such as {@link javax.inject.Singleton} and {@link Context}</p>
 *
 * @author graemerocher
 * @since 1.0
 * @see Context
 * @see Executable
 * @see io.micronaut.context.processor.ExecutableMethodProcessor
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Experimental
public @interface Parallel {
    /**
     * The default behaviour is to shutdown the context if an error occurs on initialization. Can be set false if shutdown is not required.
     *
     * @return Whether to shutdown the application if an error occurs.
     */
    boolean shutdownOnError() default true;
}
