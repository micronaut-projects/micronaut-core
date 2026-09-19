/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python.runtime;

import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.Optional;

/** Shared behavior for actual runtime-generated bean definition subclasses. */
@Internal
public abstract class RuntimePythonBeanDefinition implements RuntimeBeanDefinition<Object> {
    private final Class<Object> beanType;
    private final AnnotationMetadata metadata;

    /**
     * Initializes shared definition metadata.
     *
     * @param beanType The wrapper class described by this definition
     */
    @SuppressWarnings("unchecked")
    protected RuntimePythonBeanDefinition(Class<?> beanType) {
        this.beanType = (Class<Object>) beanType;
        metadata = RuntimePythonModel.metadata(true);
    }

    @Override
    public final Class<Object> getBeanType() {
        return beanType;
    }

    @Override
    public final String getBeanDefinitionName() {
        return getClass().getName();
    }

    @Override
    public final boolean isSingleton() {
        return true;
    }

    @Override
    public final Optional<String> getScopeName() {
        return Optional.of(Singleton.class.getName());
    }

    @Override
    public final AnnotationMetadata getAnnotationMetadata() {
        return metadata;
    }
}
