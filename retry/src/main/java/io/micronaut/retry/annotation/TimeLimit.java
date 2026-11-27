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
import io.micronaut.retry.intercept.TimeLimitInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * AOP advice that gives a call of a method a limit of time. A call whose result is not there in time
 * fails with a {@link io.micronaut.retry.exception.TimeLimitExceededException}.
 *
 * <p>The method returns a {@link java.util.concurrent.CompletionStage} or a
 * {@link org.reactivestreams.Publisher}, or is a function that suspends: the limit is for the result to
 * be complete, and the time the method takes to return the stage or the publisher counts. The advice does not
 * interrupt a thread, so a method that answers on the thread it was
 * called on cannot be limited, and a call of one fails with an {@link IllegalStateException}.</p>
 *
 * <p>At the limit a publisher is cancelled and a {@link java.util.concurrent.CompletableFuture} too.
 * Work that does not listen to either goes on, and its result is dropped.</p>
 *
 * <p>The advice runs inside {@link Retryable} and outside {@link Bulkhead}: each attempt of a retry
 * has the limit to itself, and the wait for a place of a bulkhead counts.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Around
@Type(TimeLimitInterceptor.class)
@Experimental
public @interface TimeLimit {

    /**
     * @return The time a call has for its result, as a duration: {@code 2s}, {@code 500ms}
     */
    String value();
}
