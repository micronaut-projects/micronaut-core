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
package io.micronaut.validation.validator;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;
import io.micronaut.validation.validator.constraints.DefaultConstraintValidators;
import io.micronaut.validation.validator.extractors.DefaultValueExtractors;
import io.micronaut.validation.validator.extractors.ValueExtractorRegistry;
import io.micronaut.validation.validator.messages.DefaultValidationMessages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.ClockProvider;
import javax.validation.Path;
import javax.validation.TraversableResolver;
import java.lang.annotation.ElementType;

/**
 * The default configuration for the validator.
 *
 * @author graemerocher
 * @since 1.2
 */
@ConfigurationProperties(ValidatorConfiguration.PREFIX)
public class DefaultValidatorConfiguration implements ValidatorConfiguration, Toggleable {

    @Nullable
    private ConstraintValidatorRegistry constraintValidatorRegistry;

    @Nullable
    private ValueExtractorRegistry valueExtractorRegistry;

    @Nullable
    private ClockProvider clockProvider;

    @Nullable
    private TraversableResolver traversableResolver;

    @Nullable
    private MessageSource messageSource;

    @Nullable
    private ExecutionHandleLocator executionHandleLocator;

    private boolean enabled = true;

    @Override
    @Nonnull
    public ConstraintValidatorRegistry getConstraintValidatorRegistry() {
        if (constraintValidatorRegistry != null) {
            return constraintValidatorRegistry;
        }
        return new DefaultConstraintValidators();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether Micronaut's validator is enabled.
     *
     * @param enabled True if it is
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the constraint validator registry to use.
     * @param constraintValidatorRegistry The registry to use
     */
    @Inject
    public void setConstraintValidatorRegistry(@Nullable ConstraintValidatorRegistry constraintValidatorRegistry) {
        this.constraintValidatorRegistry = constraintValidatorRegistry;
    }

    @Override
    @Nonnull
    public ValueExtractorRegistry getValueExtractorRegistry() {
        if (valueExtractorRegistry != null) {
            return valueExtractorRegistry;
        }
        return new DefaultValueExtractors();
    }

    /**
     * Sets the value extractor registry use.
     * @param valueExtractorRegistry The registry
     */
    @Inject
    public void setValueExtractorRegistry(@Nullable ValueExtractorRegistry valueExtractorRegistry) {
        this.valueExtractorRegistry = valueExtractorRegistry;
    }

    @Override
    @Nonnull
    public ClockProvider getClockProvider() {
        if (clockProvider != null) {
            return clockProvider;
        } else {
            return new DefaultClockProvider();
        }
    }

    /**
     * Sets the clock provider to use.
     * @param clockProvider The clock provider
     */
    @Inject
    public void setClockProvider(@Nullable ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    @Override
    @Nonnull
    public TraversableResolver getTraversableResolver() {
        if (traversableResolver != null) {
            return traversableResolver;
        } else {
            return new TraversableResolver() {
                @Override
                public boolean isReachable(Object object, Path.Node node, Class<?> rootType, Path path, ElementType elementType) {
                    return true;
                }

                @Override
                public boolean isCascadable(Object object, Path.Node node, Class<?> rootType, Path path, ElementType elementType) {
                    return true;
                }
            };
        }
    }

    /**
     * Sets the traversable resolver to use.
     * @param traversableResolver The resolver
     */
    @Inject
    public void setTraversableResolver(@Nullable TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
    }

    @Override
    @Nonnull
    public MessageSource getMessageSource() {
        if (messageSource != null) {
            return messageSource;
        }
        return new DefaultValidationMessages();
    }

    /**
     * Sets the message source to use.
     *
     * @param messageSource The message source
     */
    @Inject
    public void setMessageSource(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    @Nonnull
    public ExecutionHandleLocator getExecutionHandleLocator() {
        if (executionHandleLocator != null) {
            return executionHandleLocator;
        } else {
            return new ExecutionHandleLocator() {
            };
        }
    }

    /**
     * Sets the execution handler locator to use.
     *
     * @param executionHandleLocator The locator
     */
    @Inject
    public void setExecutionHandleLocator(@Nullable ExecutionHandleLocator executionHandleLocator) {
        this.executionHandleLocator = executionHandleLocator;
    }
}
