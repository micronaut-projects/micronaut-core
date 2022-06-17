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

import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.PrimaryQualifier;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Default implementation of {@link RuntimeBeanDefinition<T>}.
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.6.0
 */
@Experimental
final class DefaultRuntimeBeanDefinition<T> extends AbstractBeanContextConditional implements RuntimeBeanDefinition<T> {
    private static final AtomicInteger REF_COUNT = new AtomicInteger(0);
    private final Class<T> beanType;
    private final Supplier<T> supplier;
    private final AnnotationMetadata annotationMetadata;
    private final String beanName;
    private final Qualifier<T> qualifier;

    DefaultRuntimeBeanDefinition(
        @NonNull Class<T> beanType,
        @NonNull Supplier<T> supplier,
        @Nullable Qualifier<T> qualifier,
        @Nullable AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        Objects.requireNonNull(supplier, "Bean supplier cannot be null");

        this.beanType = beanType;
        this.supplier = supplier;
        this.beanName = generateBeanName(beanType);
        this.qualifier = qualifier;
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
    }

    @Override
    public boolean isPrimary() {
        return qualifier == PrimaryQualifier.INSTANCE || RuntimeBeanDefinition.super.isPrimary();
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        if (this.qualifier != null) {
            return this.qualifier;
        } else {
            return RuntimeBeanDefinition.super.getDeclaredQualifier();
        }
    }

    @Override
    public Qualifier<T> resolveDynamicQualifier() {
        return qualifier;
    }

    /**
     * Generates the bean name for the give ntype.
     * @param beanType The bean type
     * @return The bean name
     */
    static String generateBeanName(@NonNull Class<?> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return beanType.getName() + "$DynamicDefinition" + REF_COUNT.incrementAndGet();
    }

    @Override
    public String getBeanDefinitionName() {
        return beanName;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public BeanDefinition<T> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public Class<T> getBeanType() {
        return beanType;
    }

    @Override
    public boolean isSingleton() {
        return RuntimeBeanDefinition.super.isSingleton();
    }

    @Override
    public T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        return supplier.get();
    }
}

