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
package io.micronaut.validation.validator;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.MessageSource;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;
import io.micronaut.validation.validator.extractors.ValueExtractorRegistry;

import io.micronaut.core.annotation.NonNull;
import javax.validation.ClockProvider;
import javax.validation.TraversableResolver;
import javax.validation.ValidatorContext;

/**
 * Configuration for the {@link Validator}.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ValidatorConfiguration {

    /**
     * The prefix to use for config.
     */
    String PREFIX = "micronaut.validator";

    /**
     * Whether the validator is enabled.
     */
    String ENABLED = PREFIX + ".enabled";

    /**
     * Whether to use the annotations defined for the iterable container on the elements of this iterable container
     * See {@link #setUseIterableAnnotationsForIterableValues(boolean)} for details.
     */
    String USE_ITERABLE_ANNOTATIONS_FOR_ITERABLE_VALUES = PREFIX + ".useIterableAnnotationsForIterableValues";

    /**
     * @return The constraint registry to use.
     */
    @NonNull
    ConstraintValidatorRegistry getConstraintValidatorRegistry();

    /**
     * @return The value extractor registry
     */
    @NonNull
    ValueExtractorRegistry getValueExtractorRegistry();

    /**
     * @return The clock provider
     */
    @NonNull
    ClockProvider getClockProvider();

    /**
     * @return The traversable resolver to use
     */
    @NonNull
    TraversableResolver getTraversableResolver();

    /**
     * @return The message source
     */
    @NonNull
    MessageSource getMessageSource();

    /**
     * The execution handler locator to use.
     * @return The locator
     */
    @NonNull
    ExecutionHandleLocator getExecutionHandleLocator();


    /**
     * Set flag to use annotations of iterable container for its elements.
     * Will use container annotations if set and iterable annotated with @javax.validation.Valid
     * The container will not be validated with these annotations in this case (they apply only to elements)
     * If set, generic annotations for iterables will be disabled
     */
    ValidatorContext setUseIterableAnnotationsForIterableValues(boolean useIterableAnnotationsForIterableValues);

    /**
     * Is flag to use annotations of iterable container for its elements set.
     * See {@link #setUseIterableAnnotationsForIterableValues(boolean)} for detailed description
     */
    boolean isUseIterableAnnotationsForIterableValues();
}
