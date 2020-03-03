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

import io.micronaut.core.annotation.Internal;

/**
 * @param <T> The type
 * @param <R> The result type
 * @author graemerocher
 * @since 1.0
 */
@Internal
class DefaultBeanDefinitionMethodReference<T, R> implements BeanDefinitionMethodReference<T, R>, DelegatingExecutableMethod<T, R> {

    private final BeanDefinition<T> definition;
    private final ExecutableMethod<T, R> method;

    /**
     * @param definition The bean definition
     * @param method     The method
     */
    DefaultBeanDefinitionMethodReference(BeanDefinition<T> definition, ExecutableMethod<T, R> method) {
        this.definition = definition;
        this.method = method;
    }

    @Override
    public BeanDefinition<T> getBeanDefinition() {
        return definition;
    }

    @Override
    public ExecutableMethod<T, R> getTarget() {
        return method;
    }

    @Override
    public String toString() {
        return getTarget().toString();
    }
}
