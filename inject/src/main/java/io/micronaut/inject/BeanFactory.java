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
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;

/**
 * <p>An interface for classes that are capable of taking the {@link BeanDefinition} instance and building a concrete
 * instance.
 * This interface is generally implemented by a build time tool such as an AST transformation framework that will build
 * the code necessary to construct a valid bean instance.</p>
 *
 * @param <T> The bean type
 * @author Graeme Rocher
 * @see io.micronaut.inject.writer.BeanDefinitionWriter
 * @since 1.0
 */
public interface BeanFactory<T> {

    /**
     * Builds a bean instance.
     *
     * @param context    The context
     * @param definition The definition
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    default T build(BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        return build(new DefaultBeanResolutionContext(context, definition), context, definition);
    }

    /**
     * Builds a bean instance.
     *
     * @param resolutionContext The bean resolution context
     * @param context           The context
     * @param definition        The definition
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException;
}
