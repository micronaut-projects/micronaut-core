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

package io.micronaut.configuration.hystrix.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.Property;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to configure default Hystrix settings at type level.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
public @interface Hystrix {
    /**
     * The command group key is used for grouping together commands such as for reporting,
     * alerting, dashboards or team/library ownership.
     * <p/>
     * default => The @Client ID if found otherwise the runtime class name of annotated method
     *
     * @return group key
     */
    String group() default "";

    /**
     * The thread-pool key is used to represent a
     * HystrixThreadPool for monitoring, metrics publishing, caching and other such uses.
     *
     * @return thread pool key
     */
    String threadPool() default "";

    /**
     * @return Whether to wrap exceptions
     */
    boolean wrapExceptions() default false;

    /**
     * @return Global properties to apply to all commands
     * @see com.netflix.hystrix.HystrixCommandProperties
     */
    Property[] commandProperties() default {};

    /**
     * @return Thread pool properties to apply to blocking commands
     * @see com.netflix.hystrix.HystrixThreadPoolProperties
     */
    Property[] threadPoolProperties() default {};
}
