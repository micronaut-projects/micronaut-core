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

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Experimental;
import jakarta.validation.constraints.Digits;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Variation of {@link Retryable} that implements the Circuit Breaker pattern. Has higher overhead than
 * {@link Retryable} as a {@link io.micronaut.retry.CircuitState} has to be maintained for each method call
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retryable
public @interface CircuitBreaker {

    /**
     * The maximum integral digits for circuit breaker attempts validation.
     */
    int MAX_RETRY_ATTEMPTS = 4;

    /**
     * Returns the exception types to include, which defaults to all.
     *
     * @return The exception types to include (defaults to all)
     */
    @AliasFor(annotation = Retryable.class, member = "includes")
    Class<? extends Throwable>[] includes() default {};

    /**
     * Returns the exception types to exclude, which defaults to none.
     *
     * @return The exception types to exclude (defaults to none)
     */
    @AliasFor(annotation = Retryable.class, member = "excludes")
    Class<? extends Throwable>[] excludes() default {};

    /**
     * Returns the maximum number of retry attempts.
     *
     * @return The maximum number of retry attempts
     */
    @Digits(integer = MAX_RETRY_ATTEMPTS, fraction = 0)
    @AliasFor(annotation = Retryable.class, member = "attempts")
    String attempts() default "3";

    /**
     * Returns the delay between retry attempts.
     *
     * @return The delay between retry attempts
     */
    @AliasFor(annotation = Retryable.class, member = "delay")
    String delay() default "500ms";

    /**
     * Returns the multiplier to use to calculate the delay between retries.
     *
     * @return The multiplier to use to calculate the delay between retries.
     */
    @Digits(integer = 2, fraction = 2)
    @AliasFor(annotation = Retryable.class, member = "multiplier")
    String multiplier() default "0";

    /**
     * The maximum overall delay for an operation to complete until the Circuit state is set to
     * {@link io.micronaut.retry.CircuitState#OPEN}.
     *
     * @return The maximum overall delay
     */
    @AliasFor(annotation = Retryable.class, member = "maxDelay")
    String maxDelay() default "5s";

    /**
     * Sets the {@link java.time.Duration} of time before resetting the circuit to
     * {@link io.micronaut.retry.CircuitState#HALF_OPEN} allowing a single retry.
     *
     * @return The {@link java.time.Duration} of time before reset
     */
    String reset() default "20s";

    /**
     * Returns the retry predicate class to use instead of {@link Retryable#includes} and {@link Retryable#excludes}.
     *
     * @return The retry predicate class to use instead of {@link Retryable#includes} and {@link Retryable#excludes}
     * (defaults to none)
     */
    @AliasFor(annotation = Retryable.class, member = "predicate")
    Class<? extends RetryPredicate> predicate() default DefaultRetryPredicate.class;

    /**
     * If {@code true} and the circuit is opened, it throws the original exception wrapped.
     * in a {@link io.micronaut.retry.exception.CircuitOpenException}
     *
     * @return Whether to wrap the original exception in a {@link io.micronaut.retry.exception.CircuitOpenException}
     */
    boolean throwWrappedException() default false;

    /**
     * The name of a circuit breaker of the {@link io.micronaut.retry.CircuitBreakerRegistry}
     * whose circuit the method shares, e.g. with other methods and with the programmatic circuit
     * breakers of the same name: they open, half-open and close together. The method keeps its
     * own retries, those of this annotation. The reset timeout and the rolling window of the
     * circuit are those of the configuration {@code micronaut.retry.circuit-breakers.<name>}, if
     * any, and a method that declares others fails. Without a configuration, every user of the
     * name must have the same ones, the defaults included, or its calls fail with an
     * {@link IllegalStateException} that names both users. Empty, the default, for a circuit of
     * the method alone.
     *
     * @return The name of the circuit breaker, or empty
     * @since 5.3.0
     */
    @Experimental
    String name() default "";

    /**
     * The size of the rolling window of the calls of a closed circuit, as in MicroProfile Fault
     * Tolerance, see {@link io.micronaut.retry.CircuitBreakerPolicy.Window}: the circuit opens
     * when the window is full and the ratio of failures in it reaches {@link #failureRatio()}.
     * Setting it, or {@link #failureRatio()}, {@link #successThreshold()}, {@link #failOn()} or
     * {@link #skipOn()}, gives the circuit a rolling window, with the defaults of the others.
     * Empty, the default, for the circuit breaker of Micronaut: the first failure that survives
     * the retries opens the circuit, and the first success of a half-open circuit closes it.
     * The outcome of a call is the outcome after its retries; an exception that
     * {@link #excludes()} or {@link #predicate()} does not retry counts as a success.
     *
     * @return The number of calls of the window, e.g. {@code "20"}, or empty
     * @since 5.3.0
     */
    @Experimental
    String requestVolumeThreshold() default "";

    /**
     * @return The ratio of failures of a full window that opens the circuit, between 0 and 1,
     * default 0.5 when the circuit has a rolling window, see {@link #requestVolumeThreshold()}
     * @since 5.3.0
     */
    @Experimental
    String failureRatio() default "";

    /**
     * @return The number of trial calls of a half-open circuit with a rolling window, which all
     * succeed to close it, default 1; the other calls of the half-open circuit are rejected
     * @since 5.3.0
     */
    @Experimental
    String successThreshold() default "";

    /**
     * @return The exceptions that count as a failure of a circuit with a rolling window, default
     * every one
     * @since 5.3.0
     */
    @Experimental
    Class<? extends Throwable>[] failOn() default {};

    /**
     * @return The exceptions that count as a success of a circuit with a rolling window, whatever
     * {@link #failOn()} says
     * @since 5.3.0
     */
    @Experimental
    Class<? extends Throwable>[] skipOn() default {};
}
