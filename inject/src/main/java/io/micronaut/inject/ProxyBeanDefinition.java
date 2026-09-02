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
package io.micronaut.inject;

import io.micronaut.context.BeanDefinitionRegistry;

import java.util.Objects;
import java.util.Optional;

/**
 * Marker interface for a {@link BeanDefinition} that is an AOP proxy.
 *
 * @param <T> The bean definition type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ProxyBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * @return The target type
     */
    Class<BeanDefinition<T>> getTargetDefinitionType();

    /**
     * @return The target type
     */
    Class<T> getTargetType();

    /**
     * Finds the definition this proxy stands in front of.
     *
     * <p>{@link BeanDefinitionRegistry#findProxyTargetBeanDefinition(BeanDefinition)} answers by re-resolving the
     * proxy's type and qualifier. This answers by identity: the definition compiled as
     * {@link #getTargetDefinitionType()}, the same instance the registry resolves for it.</p>
     *
     * @param registry The registry holding the definitions
     * @return An {@link Optional} of the target definition, empty when the registry does not hold it
     * @since 5.2.0
     */
    default Optional<BeanDefinition<T>> findTargetDefinition(BeanDefinitionRegistry registry) {
        Objects.requireNonNull(registry, "Registry cannot be null");
        return registry.findBeanDefinitionByDefinitionClass(getTargetDefinitionType());
    }

    @Override
    default boolean isProxy() {
        return true;
    }
}
