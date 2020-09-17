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
package io.micronaut.http.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.http.HttpStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to method to map it to an error route.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@HttpMethodMapping
public @interface Error {

    /**
     * @return The exception to map to
     */
    @AliasFor(member = "exception")
    Class<? extends Throwable> value() default Throwable.class;

    /**
     * @return The exception to map to
     */
    @AliasFor(member = "value")
    Class<? extends Throwable> exception() default Throwable.class;

    /**
     * @return The {@link io.micronaut.http.HttpStatus} code to map
     */
    HttpStatus status() default HttpStatus.INTERNAL_SERVER_ERROR;

    /**
     * Whether the error handler should be registered as a global error handler or just locally to the declaring
     * {@link Controller}.
     *
     * @return True if it should be global
     */
    boolean global() default false;
}
