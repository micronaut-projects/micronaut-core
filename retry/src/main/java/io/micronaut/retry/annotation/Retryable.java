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
package io.micronaut.retry.annotation;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;
import io.micronaut.retry.intercept.DefaultRetryInterceptor;
import jakarta.validation.constraints.Digits;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * AOP Advice that can be applied to any method.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Around
@Type(DefaultRetryInterceptor.class)
public @interface Retryable {

    /**
     * The maximum integral digits for retry attempts validation.
     */
    int MAX_INTEGRAL_DIGITS = 4;

    /**
     * Returns the exception types to include, which defaults to all.
     *
     * @return The exception types to include (defaults to all)
     */
    @AliasFor(member = "includes")
    Class<? extends Throwable>[] value() default {};

    /**
     * Returns the exception types to include, which defaults to all.
     *
     * @return The exception types to include (defaults to all)
     */
    Class<? extends Throwable>[] includes() default {};

    /**
     * Returns the exception types to exclude, which defaults to none.
     *
     * @return The exception types to exclude (defaults to none)
     */
    Class<? extends Throwable>[] excludes() default {};

    /**
     * Returns the maximum number of retry attempts.
     *
     * @return The maximum number of retry attempts
     */
    @Digits(integer = MAX_INTEGRAL_DIGITS, fraction = 0)
    String attempts() default "3";

    /**
     * Returns the delay between retry attempts.
     *
     * @return The delay between retry attempts
     */
    String delay() default "1s";

    /**
     * Returns the maximum overall delay.
     *
     * @return The maximum overall delay
     */
    String maxDelay() default "";

    /**
     * Returns the multiplier to use to calculate the delay.
     *
     * @return The multiplier to use to calculate the delay
     */
    @Digits(integer = 2, fraction = 2)
    String multiplier() default "1.0";

    /**
     * Returns the retry predicate class to use instead of {@link Retryable#includes} and {@link Retryable#excludes}.
     *
     * @return The retry predicate class to use instead of {@link Retryable#includes} and {@link Retryable#excludes}
     * (defaults to none)
     */
    Class<? extends RetryPredicate> predicate() default DefaultRetryPredicate.class;

    /**
     * Returns the exception type that should be intercepted for retry handling.
     *
     * @return The exception type that should be intercepted for retry handling. Once an exception is captured,
     * it is still evaluated by {@link Retryable#includes}, {@link Retryable#excludes}, or
     * {@link Retryable#predicate()} to decide whether to retry. Defaults to {@link RuntimeException}.
     */
    Class<? extends Throwable> capturedException() default RuntimeException.class;

    /**
     * Returns the jitter factor used to apply random deviation to retry delays.
     *
     * @return The jitter factor used to apply random deviation to retry delays
     */
    @Digits(integer = 1, fraction = 2)
    String jitter() default "0.0";
}
