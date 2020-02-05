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
package io.micronaut.inject.validation;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Interface to integrate bean validation into the construction of beans within the {@link io.micronaut.context.BeanContext}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface BeanDefinitionValidator {
    /**
     * A default no-op validator.
     */
    BeanDefinitionValidator DEFAULT = new BeanDefinitionValidator() {
    };

    /**
     * Validates the given bean after it has been constructor.
     *
     * @param resolutionContext The resolution context
     * @param injectionPoint    The injection point
     * @param argument          The argument
     * @param index             The argument index
     * @param value             The value
     * @param <T>               The bean type
     * @throws BeanInstantiationException if the bean is invalid
     */
    default <T> void validateBeanArgument(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull InjectionPoint injectionPoint,
            @NonNull Argument<T> argument,
            int index,
            @Nullable T value)
            throws BeanInstantiationException {
        // no-op
    }

    /**
     * Validates the given bean after it has been constructor.
     *
     * @param resolutionContext The resolution context
     * @param definition        The definition
     * @param bean              The bean to validate
     * @param <T>               The bean type
     * @throws BeanInstantiationException if the bean is invalid
     */
    default <T> void validateBean(
            @NonNull BeanResolutionContext resolutionContext,
            @NonNull BeanDefinition<T> definition,
            @NonNull T bean)
            throws BeanInstantiationException {
        // no-op
    }
}
