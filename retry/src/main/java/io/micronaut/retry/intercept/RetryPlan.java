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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.retry.RetryPolicy;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.Retryable;
import org.jspecify.annotations.Nullable;

/**
 * What the retry interceptor derives from the annotations of a method, kept by the method for its next calls.
 *
 * @param retry          The retryable annotation or {@code null} if the method isn't retryable
 * @param name           The name of the policy the annotation refers to, empty if none
 * @param isCircuitBreaker Whether the method has the circuit breaker stereotype
 * @param circuitBreaker The circuit breaker annotation, if any
 * @param policy         The policy of a method that uses neither a named policy nor a circuit breaker and keeps the
 *                       default predicate, which can be shared, or {@code null}
 */
record RetryPlan(@Nullable AnnotationValue<Retryable> retry,
                 String name,
                 boolean isCircuitBreaker,
                 @Nullable AnnotationValue<CircuitBreaker> circuitBreaker,
                 @Nullable RetryPolicy policy) {

    private static final String NAME = "name";
    private static final String PREDICATE = "predicate";

    static RetryPlan of(ExecutableMethod<?, ?> method) {
        AnnotationValue<Retryable> retry = method.getAnnotation(Retryable.class);
        if (retry == null) {
            return new RetryPlan(null, "", false, null, null);
        }
        String name = retry.stringValue(NAME).orElse("");
        boolean isCircuitBreaker = method.hasStereotype(CircuitBreaker.class);
        AnnotationValue<CircuitBreaker> circuitBreaker = isCircuitBreaker ? method.getAnnotation(CircuitBreaker.class) : null;
        RetryPolicy policy = null;
        // A custom predicate is created for every call, as it may not be shareable
        if (name.isEmpty() && !isCircuitBreaker
            && retry.classValue(PREDICATE).orElse(DefaultRetryPredicate.class) == DefaultRetryPredicate.class) {
            policy = new AnnotationRetryStateBuilder(method.getAnnotationMetadata()).retryPolicy();
        }
        return new RetryPlan(retry, name, isCircuitBreaker, circuitBreaker, policy);
    }
}
