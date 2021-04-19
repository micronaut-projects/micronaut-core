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
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import javax.inject.Provider;


/**
 * Reference for the Javax Provider factory.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class JavaxProviderBeanDefinitionReference implements BeanDefinitionReference<Provider<Object>> {
    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return isPresent();
    }

    @Override
    public String getBeanDefinitionName() {
        return JavaxProviderBeanDefinition.class.getName();
    }

    @Override
    public BeanDefinition<Provider<Object>> load() {
        return new JavaxProviderBeanDefinition();
    }

    @Override
    public boolean isPresent() {
        return isTypePresent();
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public boolean isPrimary() {
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Provider<Object>> getBeanType() {
        return (Class) Provider.class;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AbstractProviderDefinition.ANNOTATION_METADATA;
    }

    static boolean isTypePresent() {
        try {
            return Provider.class.isInterface();
        } catch (Throwable e) {
            // class not present
            return false;
        }
    }
}
