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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.RetryStateBuilder;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.RetryPredicate;
import io.micronaut.retry.annotation.Retryable;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
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
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;

    private final AnnotationMetadata annotationMetadata;

    /**
     * Build the meta data for the given element with retry.
     *
     * @param annotationMetadata Allows the inspection of annotation metadata and stereotypes (meta-annotations)
     */
    AnnotationRetryStateBuilder(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public RetryState build() {
        AnnotationValue<Retryable> retry = annotationMetadata.findAnnotation(Retryable.class)
                                                             .orElseThrow(() -> new IllegalStateException("Missing @Retryable annotation"));
        int attempts = retry.get(ATTEMPTS, Integer.class).orElse(DEFAULT_RETRY_ATTEMPTS);
        Duration delay = retry.get(DELAY, Duration.class).orElse(Duration.ofSeconds(1));
        Class<? extends RetryPredicate> predicateClass = retry.get(PREDICATE, Class.class)
                                                              .orElse(DefaultRetryPredicate.class);
        RetryPredicate predicate = createPredicate(predicateClass, retry);

        return new SimpleRetry(
            attempts,
            retry.get(MULTIPLIER, Double.class).orElse(0d),
            delay,
            retry.get(MAX_DELAY, Duration.class).orElse(null),
            predicate
        );
    }

    private static RetryPredicate createPredicate(Class<? extends RetryPredicate> predicateClass, AnnotationValue<Retryable> retry) {
        if (predicateClass.equals(DefaultRetryPredicate.class)) {
            // annotationClassValues are independent from the runtime classpath
            AnnotationClassValue<?>[] annotationClassValues = retry.annotationClassValues(INCLUDES);
            List<Class<? extends Throwable>> includes = resolveThrowables(retry, INCLUDES);
            if (annotationClassValues.length > 0 && includes.size() == 0) {
                // None of the specified includes are on the classpath, retry will never be invoked
                return throwable -> false;
            }
            List<Class<? extends Throwable>> excludes = resolveThrowables(retry, EXCLUDES);
            return new DefaultRetryPredicate(includes, excludes);
        } else {
            return InstantiationUtils.instantiate(predicateClass);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Class<? extends Throwable>> resolveThrowables(AnnotationValue<Retryable> retry, String annotationMemberName) {
        // Resolves the value of the specified annotation member as a list of Throwables
        // Throwables missing on the runtime classpath are silently ignored
        Class<?>[] values = retry.classValues(annotationMemberName);
        return (List) Collections.unmodifiableList(Arrays.asList(values));
    }
}
