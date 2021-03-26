/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

/**
 * Reference for the {@link BeanProvider} factory.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class BeanProviderDefinitionReference implements BeanDefinitionReference<BeanProvider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public String getBeanDefinitionName() {
        return BeanProviderDefinition.class.getName();
    }

    @Override
    public BeanDefinition<BeanProvider<Object>> load() {
        return new BeanProviderDefinition();
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Class<BeanProvider<Object>> getBeanType() {
        return (Class) BeanProvider.class;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AbstractProviderDefinition.ANNOTATION_METADATA;
    }
}
