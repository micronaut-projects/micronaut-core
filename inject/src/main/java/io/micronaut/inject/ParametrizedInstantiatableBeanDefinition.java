/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;

import java.util.Map;

/**
 * <p>An type of {@link BeanDefinition} that can build a new instance, construction requires additional (possibly user supplied) parameters in order construct a bean</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 4.0
 */
@Internal
public interface ParametrizedInstantiatableBeanDefinition<T> extends InstantiatableBeanDefinition<T> {

    /**
     * @return The arguments required to construct this bean
     */
    @NonNull
    Argument<Object>[] getRequiredArguments();

    /**
     * Variation of the {@link #instantiate(BeanContext)} method that allows passing the values necessary for
     * successful bean construction.
     *
     * @param resolutionContext      The {@link BeanResolutionContext}
     * @param context                The {@link BeanContext}
     * @param requiredArgumentValues The required arguments values. The keys should match the names of the arguments
     *                               returned by {@link #getRequiredArguments()}
     * @return The instantiated bean
     * @throws BeanInstantiationException If the bean cannot be instantiated for the arguments supplied
     */
    @NonNull
    T instantiate(@NonNull BeanResolutionContext resolutionContext,
                  @NonNull BeanContext context,
                  @NonNull Map<String, Object> requiredArgumentValues) throws BeanInstantiationException;

    @Override
    @NonNull
    default T instantiate(@NonNull BeanResolutionContext resolutionContext, @NonNull BeanContext context) throws BeanInstantiationException {
        throw new BeanInstantiationException(this, "Cannot instantiate parametrized bean with no arguments");
    }
}
