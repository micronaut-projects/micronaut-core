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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.Collection;

/**
 * A registry of bean definition references.
 */
public interface BeanDefinitionReferenceRegistry {
    /**
     * Get all of the enabled {@link BeanDefinitionReference}.
     *
     * @return The bean definitions
     */
    @NonNull
    Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences();

    /**
     * Registers a new reference at runtime. Not that registering beans can impact
     * the object graph therefore should this should be done as soon as possible prior to
     * the creation of other beans preferably with a high priority {@link io.micronaut.context.annotation.Context} scope bean.
     *
     * @param reference The reference.
     * @return The registry
     * @param <B> The bean type
     */
    @NonNull
    <B> BeanDefinitionReferenceRegistry registerBeanReference(@NonNull BeanDefinitionReference<B> reference);
}
