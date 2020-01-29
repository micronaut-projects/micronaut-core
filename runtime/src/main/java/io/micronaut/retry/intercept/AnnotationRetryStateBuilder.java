/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.RetryStateBuilder;
import io.micronaut.retry.annotation.Retryable;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private static final String INCLUDES_ALL_OF = "includesAllOf";
    private static final String EXCLUDES_ALL_OF = "excludesAllOf";
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
        Set<Class<? extends Throwable>> includes = resolveIncludes(retry, INCLUDES);
        Set<Class<? extends Throwable>> excludes = resolveIncludes(retry, EXCLUDES);
        Set<Class<? extends Throwable>> includesAllOf = resolveIncludes(retry, INCLUDES_ALL_OF);
        Set<Class<? extends Throwable>> excludesAllOf = resolveIncludes(retry, EXCLUDES_ALL_OF);

        return new SimpleRetry(
            attempts,
            retry.get(MULTIPLIER, Double.class).orElse(0d),
            delay,
            retry.get(MAX_DELAY, Duration.class).orElse(null),
            includes,
            excludes,
            includesAllOf,
            excludesAllOf
        );
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends Throwable>> resolveIncludes(AnnotationValue<Retryable> retry, String includes) {
        Class<?>[] values = retry.classValues(includes);
        Set classes = new HashSet<>(values.length);
        classes.addAll(Arrays.asList(values));
        return classes;
    }
}
