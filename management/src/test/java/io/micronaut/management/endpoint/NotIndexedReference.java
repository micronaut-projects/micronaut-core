/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.management.endpoint;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A bean definition reference as the processor emitted it before an annotation the bean carries was
 * indexed by itself: the same reference, without that index.
 *
 * @param <T> The bean type
 */
public final class NotIndexedReference<T> implements BeanDefinitionReference<T> {

    private final BeanDefinitionReference<T> delegate;
    private final Class<?> withoutIndex;

    public NotIndexedReference(BeanDefinitionReference<T> delegate, Class<?> withoutIndex) {
        this.delegate = delegate;
        this.withoutIndex = withoutIndex;
    }

    /**
     * Replaces the reference of the given bean with one that is not indexed by the given type. Matched by
     * definition name, so the other references are not introspected.
     *
     * @param references The references
     * @param beanType The bean whose reference to replace
     * @param withoutIndex The index to drop
     * @return The references
     */
    public static List<BeanDefinitionReference<?>> wrap(List<BeanDefinitionReference<?>> references, Class<?> beanType, Class<?> withoutIndex) {
        String definitionName = beanType.getPackageName() + ".$" + beanType.getSimpleName() + "$Definition";
        List<BeanDefinitionReference<?>> result = new ArrayList<>(references.size());
        for (BeanDefinitionReference<?> reference : references) {
            result.add(reference.getBeanDefinitionName().equals(definitionName) ? new NotIndexedReference<>(reference, withoutIndex) : reference);
        }
        return result;
    }

    @Override
    public Class<?>[] getIndexes() {
        return Arrays.stream(delegate.getIndexes()).filter(type -> type != withoutIndex).toArray(Class<?>[]::new);
    }

    @Override
    public String getBeanDefinitionName() {
        return delegate.getBeanDefinitionName();
    }

    @Override
    public BeanDefinition<T> load() {
        return delegate.load();
    }

    @Override
    public BeanDefinition<T> load(BeanContext context) {
        return delegate.load(context);
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public boolean isContextScope() {
        return delegate.isContextScope();
    }

    @Override
    public boolean isSingleton() {
        return delegate.isSingleton();
    }

    @Override
    public boolean isConfigurationProperties() {
        return delegate.isConfigurationProperties();
    }

    @Override
    public boolean isProxiedBean() {
        return delegate.isProxiedBean();
    }

    @Override
    public boolean isProxyTarget() {
        return delegate.isProxyTarget();
    }

    @Override
    public boolean isParallel() {
        return delegate.isParallel();
    }

    @Override
    public boolean isPrimary() {
        return delegate.isPrimary();
    }

    @Override
    public boolean requiresMethodProcessing() {
        return delegate.requiresMethodProcessing();
    }

    @Override
    public Class<T> getBeanType() {
        return delegate.getBeanType();
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return delegate.getExposedTypes();
    }

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return delegate.isEnabled(context, resolutionContext);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return delegate.getAnnotationMetadata();
    }
}
