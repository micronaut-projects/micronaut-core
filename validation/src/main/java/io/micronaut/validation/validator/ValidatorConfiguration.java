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
     * The configuration of Validator
     */
    public enum ValidatorBehaviour {
        /**
         * The default behaviour, that validates iterables with iterable annotations and validates
         * items of iterables with generic parameters, like List<@Ann T>
         */
        DEFAULT,
        /**
         * The behaviour, that disables validating iterable items with generic parameters.
         * If javax.validation.Valid annotations is used on an iterable, all its contraint annotations will be applied
         * to the items of this iterable, but not to the iterable itself. In this case the javax.validation.Valid
         * annotation will also be checked on the iterable items if the bean has introspection.
         */
        DISABLE_ITERABLE_GENERIC_PARAMETERS
    }

    /**
     * Validator behaviour
     */
    String VALIDATOR_BEHAVIOUR = PREFIX + ".validatorBehaviour";

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
     * Sets the type of behaviour of the validator
     * @param behaviour - a value from the ValidatorBehaviour enum
     * @return itself
     */
    ValidatorContext setValidatorBehaviour(ValidatorBehaviour behaviour);

    /**
     * @return validator behaviour
     */
    ValidatorBehaviour getValidatorBehaviour();
}
