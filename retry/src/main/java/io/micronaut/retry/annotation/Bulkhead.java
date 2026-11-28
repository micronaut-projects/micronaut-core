/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.retry.annotation;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.retry.intercept.BulkheadInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * AOP advice that limits how many calls of a method run at the same time. A call that finds no place
 * fails with a {@link io.micronaut.retry.exception.BulkheadFullException}, at once or after it waited
 * for {@link #maxWait()}.
 *
 * <p>A call holds its place until its result is there: until the method returns, the
 * {@link java.util.concurrent.CompletionStage} it returned completes or the
 * {@link org.reactivestreams.Publisher} it returned terminates or is cancelled. A method that returns
 * one of the two waits for a place without holding a thread; any other method blocks its thread while
 * it waits, and so should not wait on an event loop.</p>
 *
 * <p>The advice runs inside {@link Retryable} and {@link TimeLimit}: each attempt of a retry takes a
 * place of its own, and a time limit counts the wait for a place.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Around
@Type(BulkheadInterceptor.class)
@Experimental
public @interface Bulkhead {

    /**
     * @return How many calls run at the same time
     */
    String maxConcurrent() default "10";

    /**
     * @return How long a call waits for a place before it fails, which by default it does at once
     */
    String maxWait() default "0s";

    /**
     * The name of a bulkhead that the method shares with every other method of the same name: together
     * they run {@link #maxConcurrent()} calls, which they must all declare alike. A method without a
     * name has a bulkhead of its own. The {@link io.micronaut.retry.BulkheadRegistry} says how full a
     * named bulkhead is.
     *
     * @return The name of the bulkhead
     */
    String name() default "";
}
