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
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.validation.validator.constraints.ConstraintValidatorRegistry;
import io.micronaut.validation.validator.constraints.DefaultConstraintValidators;
import io.micronaut.validation.validator.extractors.DefaultValueExtractors;
import io.micronaut.validation.validator.extractors.ValueExtractorRegistry;
import io.micronaut.validation.validator.messages.DefaultValidationMessages;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.validation.*;
import javax.validation.Validator;
import javax.validation.valueextraction.ValueExtractor;
import java.lang.annotation.ElementType;

/**
 * The default configuration for the validator.
 *
 * @author graemerocher
 * @since 1.2
 */
@ConfigurationProperties(ValidatorConfiguration.PREFIX)
public class DefaultValidatorConfiguration implements ValidatorConfiguration, Toggleable, ValidatorContext {

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
    @NonNull
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
     * @return this configuration
     */
    public DefaultValidatorConfiguration setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Sets the constraint validator registry to use.
     * @param constraintValidatorRegistry The registry to use
     * @return this configuration
     */
    @Inject
    public DefaultValidatorConfiguration setConstraintValidatorRegistry(@Nullable ConstraintValidatorRegistry constraintValidatorRegistry) {
        this.constraintValidatorRegistry = constraintValidatorRegistry;
        return this;
    }

    @Override
    @NonNull
    public ValueExtractorRegistry getValueExtractorRegistry() {
        if (valueExtractorRegistry != null) {
            return valueExtractorRegistry;
        }
        return new DefaultValueExtractors();
    }

    /**
     * Sets the value extractor registry use.
     * @param valueExtractorRegistry The registry
     * @return this configuration
     */
    @Inject
    public DefaultValidatorConfiguration setValueExtractorRegistry(@Nullable ValueExtractorRegistry valueExtractorRegistry) {
        this.valueExtractorRegistry = valueExtractorRegistry;
        return this;
    }

    @Override
    @NonNull
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
     * @return this configuration
     */
    @Inject
    public DefaultValidatorConfiguration setClockProvider(@Nullable ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    @NonNull
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
     * @return This configuration
     */
    @Inject
    public DefaultValidatorConfiguration setTraversableResolver(@Nullable TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
        return this;
    }

    @Override
    @NonNull
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
     * @return this configuration
     */
    @Inject
    public DefaultValidatorConfiguration setMessageSource(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
        return this;
    }

    @Override
    @NonNull
    public ExecutionHandleLocator getExecutionHandleLocator() {
        if (executionHandleLocator != null) {
            return executionHandleLocator;
        } else {
            return ExecutionHandleLocator.EMPTY;
        }
    }

    /**
     * Sets the execution handler locator to use.
     *
     * @param executionHandleLocator The locator
     * @return this configuration
     */
    @Inject
    public DefaultValidatorConfiguration setExecutionHandleLocator(@Nullable ExecutionHandleLocator executionHandleLocator) {
        this.executionHandleLocator = executionHandleLocator;
        return this;
    }

    @Override
    public ValidatorContext messageInterpolator(MessageInterpolator messageInterpolator) {
        throw new UnsupportedOperationException("Method messageInterpolator(..) not supported");
    }

    @Override
    public ValidatorContext traversableResolver(TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
        return this;
    }

    @Override
    public ValidatorContext constraintValidatorFactory(ConstraintValidatorFactory factory) {
        throw new UnsupportedOperationException("Method constraintValidatorFactory(..) not supported");
    }

    @Override
    public ValidatorContext parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        throw new UnsupportedOperationException("Method parameterNameProvider(..) not supported");
    }

    @Override
    public ValidatorContext clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public ValidatorContext addValueExtractor(ValueExtractor<?> extractor) {
        throw new UnsupportedOperationException("Method addValueExtractor(..) not supported");
    }

    @Override
    public Validator getValidator() {
        return new DefaultValidator(this);
    }
}
