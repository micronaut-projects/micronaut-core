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
package io.micronaut.retry.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;
import io.micronaut.retry.intercept.DefaultRetryInterceptor;

import javax.validation.constraints.Digits;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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

    int MAX_INTEGRAL_DIGITS = 4;

    /**
     * @return The exception types to include (defaults to all)
     */
    Class<? extends Throwable>[] value() default {};

    /**
     * @return The exception types to include (defaults to all)
     */
    @AliasFor(member = "value")
    Class<? extends Throwable>[] includes() default {};

    /**
     * @return The exception types to exclude (defaults to none)
     */
    Class<? extends Throwable>[] excludes() default {};

    /**
     * @return The maximum number of retry attempts
     */
    @Digits(integer = MAX_INTEGRAL_DIGITS, fraction = 0)
    String attempts() default "3";

    /**
     * @return The delay between retry attempts
     */
    String delay() default "1s";

    /**
     * @return The maximum overall delay
     */
    String maxDelay() default "";

    /**
     * @return The multiplier to use to calculate the delay
     */
    @Digits(integer = 2, fraction = 2)
    String multiplier() default "1.0";

    /**
     * @return The retry predicate class to use instead of {@link Retryable#includes} and {@link Retryable#excludes}
     * (defaults to none)
     */
    Class<? extends RetryPredicate> predicate() default DefaultRetryPredicate.class;
}
