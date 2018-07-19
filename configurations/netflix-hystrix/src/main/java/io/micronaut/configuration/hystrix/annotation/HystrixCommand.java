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

import io.micronaut.aop.Around;
import io.micronaut.configuration.hystrix.HystrixInterceptor;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Applies AOP advise applied to Hystrix.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Around
@Type(HystrixInterceptor.class)
public @interface HystrixCommand {

    /**
     * Hystrix command key.
     * <p/>
     * default => the name of annotated method. for example:
     * <p/>
     * <code>
     * ...
     * {@code @}HystrixCommand
     * public User getUserById(...)
     * ...
     * </code>
     * <p/>
     * the command name will be: 'getUserById'
     *
     * @return command key
     */
    String value() default "";

    /**
     * Same as {@link #value()}.
     *
     * @see #value()
     * @return command value
     */
    @AliasFor(member = "value")
    String command() default "";

    /**
     * The command group key is used for grouping together commands such as for reporting,
     * alerting, dashboards or team/library ownership.
     * <p/>
     * default => The @Client ID if found otherwise the runtime class name of annotated method
     *
     * @return group key
     */
    @AliasFor(annotation = Hystrix.class, member = "group")
    String group() default "";

    /**
     * The thread-pool key is used to represent a
     * HystrixThreadPool for monitoring, metrics publishing, caching and other such uses.
     *
     * @return thread pool key
     */
    @AliasFor(annotation = Hystrix.class, member = "threadPool")
    String threadPool() default "";


    /**
     * @return Additional command specific Hystrix properties
     * @see com.netflix.hystrix.HystrixCommandProperties
     */
    @AliasFor(annotation = Hystrix.class, member = "commandProperties")
    Property[] properties() default {};
}
