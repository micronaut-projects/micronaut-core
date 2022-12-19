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
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * <p>An type of {@link BeanDefinition} that can build a new instance.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 4.0
 */
@Internal
public interface InstantiatableBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Builds a bean instance.
     *
     * @param context    The context
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    @NonNull
    default T instantiate(@NonNull BeanContext context) throws BeanInstantiationException {
        try (DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(context, this)) {
            return instantiate(resolutionContext, context);
        }
    }

    /**
     * Builds a bean instance.
     *
     * @param resolutionContext The bean resolution context
     * @param context           The context
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    @NonNull
    T instantiate(@NonNull BeanResolutionContext resolutionContext, @NonNull BeanContext context) throws BeanInstantiationException;
}
