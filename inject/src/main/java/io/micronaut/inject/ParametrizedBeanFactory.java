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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.type.Argument;

import java.util.Map;

/**
 * A {@link BeanFactory} that requires additional (possibly user supplied) parameters in order construct a bean.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ParametrizedBeanFactory<T> extends BeanFactory<T> {

    /**
     * @return The arguments required to construct this bean
     */
    Argument<?>[] getRequiredArguments();

    /**
     * Variation of the {@link #build(BeanContext, BeanDefinition)} method that allows passing the values necessary for
     * successful bean construction.
     *
     * @param resolutionContext      The {@link BeanResolutionContext}
     * @param context                The {@link BeanContext}
     * @param definition             The {@link BeanDefinition}
     * @param requiredArgumentValues The required arguments values. The keys should match the names of the arguments
     *                               returned by {@link #getRequiredArguments()}
     * @return The instantiated bean
     * @throws BeanInstantiationException If the bean cannot be instantiated for the arguments supplied
     */
    T build(BeanResolutionContext resolutionContext,
            BeanContext context,
            BeanDefinition<T> definition,
            Map<String, Object> requiredArgumentValues) throws BeanInstantiationException;

    @Override
    default T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        throw new BeanInstantiationException(definition, "Cannot instantiate parametrized bean with no arguments");
    }
}
