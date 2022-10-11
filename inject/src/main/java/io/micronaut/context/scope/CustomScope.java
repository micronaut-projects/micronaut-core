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
package io.micronaut.context.scope;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Represents a custom scope within the {@link io.micronaut.context.BeanContext}.
 *
 * @param <A> An annotation type
 * @author Graeme Rocher
 * @see javax.inject.Scope
 * @since 1.0
 */
@Indexed(CustomScope.class)
public interface CustomScope<A extends Annotation> {

    /**
     * @return The scope annotation type
     */
    Class<A> annotationType();

    /**
     * Resolve an object from the given scope.
     *
     * @param <T>               The bean generic type
     * @param creationContext   The creation context
     * @return The bean instance
     */
    <T> T getOrCreate(BeanCreationContext<T> creationContext);

    /**
     * Remove a bean definition from the scope.
     *
     * @param identifier The {@link BeanIdentifier}
     * @param <T>        The generic type
     * @return An {@link Optional} of the instance that was destroyed if it exists
     */
    <T> Optional<T> remove(BeanIdentifier identifier);

    /**
     * Get the {@link io.micronaut.inject.BeanDefinition} for the given bean.
     *
     * @param bean The bean
     * @param <T> The bean generic type
     * @return The bean definition if it can be resolved
     */
    default <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        return Optional.empty();
    }

    /**
     * Get the {@link io.micronaut.inject.BeanDefinition} for the given bean.
     *
     * @param beanDefinition The bean definition
     * @param <T> The bean generic type
     * @return The bean definition if it can be resolved
     * @since 3.5.0
     */
    default <T> Optional<BeanRegistration<T>> findBeanRegistration(BeanDefinition<T> beanDefinition) {
        return Optional.empty();
    }
}
