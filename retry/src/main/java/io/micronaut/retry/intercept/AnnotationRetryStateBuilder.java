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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.RetryStateBuilder;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.RetryPredicate;
import io.micronaut.retry.annotation.Retryable;

import java.time.Duration;
import java.util.List;

/**
 * Builds a {@link RetryState} from {@link AnnotationMetadata}.
 *
 * @author graemerocher
 * @since 1.0
 */
class AnnotationRetryStateBuilder implements RetryStateBuilder {

    private static final String ATTEMPTS = "attempts";
    private static final String MULTIPLIER = "multiplier";
    private static final String DELAY = "delay";
    private static final String MAX_DELAY = "maxDelay";
    private static final String INCLUDES = "includes";
    private static final String EXCLUDES = "excludes";
    private static final String PREDICATE = "predicate";
    private static final String CAPTURED_EXCEPTION = "capturedException";
    private static final String JITTER = "jitter";
    private static final String RESET = "reset";
    private static final String THROW_WRAPPED_EXCEPTION = "throwWrappedException";
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;

    private final AnnotationMetadata annotationMetadata;

    /**
     * Build the metadata for the given element with retry.
     *
     * @param annotationMetadata Allows the inspection of annotation metadata and stereotypes (meta-annotations)
     */
    AnnotationRetryStateBuilder(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public RetryState build() {
        return new PolicyRetryStateBuilder(retryPolicy()).build();
    }

    RetryPolicy retryPolicy() {
        AnnotationValue<Retryable> retry = annotationMetadata.findAnnotation(Retryable.class)
            .orElseThrow(() -> new IllegalStateException("Missing @Retryable annotation"));
        Duration maxDelay = retry.get(MAX_DELAY, Duration.class).orElse(null);
        RetryPolicy.Builder builder = RetryPolicy.builder()
            .maxAttempts(retry.intValue(ATTEMPTS).orElse(DEFAULT_RETRY_ATTEMPTS))
            .delay(retry.get(DELAY, Duration.class).orElse(Duration.ofSeconds(1)))
            .multiplier(retry.get(MULTIPLIER, Double.class).orElse(0d))
            .capturedException(retry.classValue(CAPTURED_EXCEPTION, Throwable.class).orElse(RuntimeException.class))
            .jitter(retry.get(JITTER, Double.class).orElse(0d));
        if (maxDelay != null) {
            builder.maxDelay(maxDelay);
        }

        @SuppressWarnings("unchecked")
        Class<? extends RetryPredicate> predicateClass = (Class<? extends RetryPredicate>) retry.classValue(PREDICATE).orElse(DefaultRetryPredicate.class);
        builder.includes(toThrowableClasses(retry.classValues(INCLUDES)));
        builder.excludes(toThrowableClasses(retry.classValues(EXCLUDES)));
        builder.predicate(createPredicate(predicateClass, retry));
        return builder.build();
    }

    CircuitBreakerPolicy circuitBreakerPolicy() {
        AnnotationValue<CircuitBreaker> circuitBreaker = annotationMetadata.findAnnotation(CircuitBreaker.class)
            .orElseThrow(() -> new IllegalStateException("Missing @CircuitBreaker annotation"));
        RetryPolicy retryPolicy = retryPolicy();
        CircuitBreakerPolicy.Builder builder = CircuitBreakerPolicy.builder()
            .maxAttempts(retryPolicy.maxAttempts())
            .delay(retryPolicy.delay())
            .multiplier(retryPolicy.multiplier())
            .jitter(retryPolicy.jitter())
            .capturedException(retryPolicy.capturedException())
            .predicate(retryPolicy.predicate())
            .resetTimeout(circuitBreaker.get(RESET, Duration.class).orElse(Duration.ofSeconds(20)))
            .throwWrappedException(circuitBreaker.booleanValue(THROW_WRAPPED_EXCEPTION).orElse(false));
        retryPolicy.getMaxDelay().ifPresent(builder::maxDelay);
        return builder.build();
    }

    private static RetryPredicate createPredicate(Class<? extends RetryPredicate> predicateClass, AnnotationValue<Retryable> retry) {
        if (predicateClass.equals(DefaultRetryPredicate.class)) {
            List<Class<? extends Throwable>> includes = resolveIncludes(retry, INCLUDES);
            List<Class<? extends Throwable>> excludes = resolveIncludes(retry, EXCLUDES);
            return new DefaultRetryPredicate(includes, excludes);
        } else {
            return InstantiationUtils.instantiate(predicateClass);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Class<? extends Throwable>> resolveIncludes(AnnotationValue<Retryable> retry, String includes) {
        Class<?>[] values = retry.classValues(includes);
        return (List) List.of(values);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable>[] toThrowableClasses(Class<?>[] values) {
        Class<? extends Throwable>[] converted = new Class[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = (Class<? extends Throwable>) values[i];
        }
        return converted;
    }
}
