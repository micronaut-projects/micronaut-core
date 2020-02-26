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

/**
 * An interface for a {@link ExecutableMethod} that is associated with a {@link BeanDefinitionReference}.
 *
 * @param <T> The type
 * @param <R> The result type
 * @author graemerocher
 * @since 1.0
 */
public interface BeanDefinitionMethodReference<T, R> extends ExecutableMethod<T, R> {

    /**
     * @return The {@link BeanDefinition} associated with this method.
     */
    BeanDefinition<T> getBeanDefinition();

    /**
     * Create a {@link BeanDefinitionMethodReference} for the given {@link BeanDefinition} and {@link ExecutableMethod}.
     *
     * @param definition The definition
     * @param method     The method
     * @param <T1>       The type
     * @param <R1>       The result
     * @return The {@link BeanDefinitionMethodReference}
     */
    static <T1, R1> BeanDefinitionMethodReference<T1, R1> of(BeanDefinition<T1> definition, ExecutableMethod<T1, R1> method) {
        return new DefaultBeanDefinitionMethodReference<>(definition, method);
    }
}
