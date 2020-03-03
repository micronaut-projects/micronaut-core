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
package io.micronaut.validation.validator;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.MessageSource;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;
import io.micronaut.validation.validator.extractors.ValueExtractorRegistry;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.validation.ClockProvider;
import javax.validation.TraversableResolver;

/**
 * Configuration for the {@link Validator}.
 *
 * @author graemerocher
 * @since 1.2
 */
@Immutable
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
     * @return The constraint registry to use.
     */
    @Nonnull
    ConstraintValidatorRegistry getConstraintValidatorRegistry();

    /**
     * @return The value extractor registry
     */
    @Nonnull
    ValueExtractorRegistry getValueExtractorRegistry();

    /**
     * @return The clock provider
     */
    @Nonnull
    ClockProvider getClockProvider();

    /**
     * @return The traversable resolver to use
     */
    @Nonnull
    TraversableResolver getTraversableResolver();

    /**
     * @return The message source
     */
    @Nonnull
    MessageSource getMessageSource();

    /**
     * The execution handler locator to use.
     * @return The locator
     */
    @Nonnull
    ExecutionHandleLocator getExecutionHandleLocator();
}
