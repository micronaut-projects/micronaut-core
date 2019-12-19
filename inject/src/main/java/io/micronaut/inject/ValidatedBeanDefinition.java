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
package io.micronaut.inject;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.validation.BeanDefinitionValidator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A bean definition that is validated with javax.validation.
 *
 * @param <T> The bean definition type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ValidatedBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Validates the bean with the validator factory if present.
     *
     * @param resolutionContext The resolution context
     * @param instance          The instance
     * @return The instance
     */
    default T validate(BeanResolutionContext resolutionContext, T instance) {
        BeanDefinitionValidator validator = resolutionContext.getContext().getBeanValidator();
        validator.validateBean(resolutionContext, this, instance);
        return instance;
    }

    /**
     * Validates the given bean after it has been constructor.
     *
     * @param resolutionContext The resolution context
     * @param injectionPoint    The injection point
     * @param argument          The argument
     * @param index             The argument index
     * @param value             The value
     * @param <V>               The value type
     * @throws BeanInstantiationException if the bean is invalid
     */
    default <V> void validateBeanArgument(
            @Nonnull BeanResolutionContext resolutionContext,
            @Nonnull InjectionPoint injectionPoint,
            @Nonnull Argument<V> argument,
            int index,
            @Nullable V value) throws BeanInstantiationException {
        BeanDefinitionValidator validator = resolutionContext.getContext().getBeanValidator();
        validator.validateBeanArgument(resolutionContext, injectionPoint, argument, index, value);
    }
}
