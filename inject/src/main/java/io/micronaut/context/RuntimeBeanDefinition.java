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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Allow the construction for bean definitions programmatically that can be registered
 * via {@link BeanDefinitionReferenceRegistry} at runtime.
 *
 * @param <T> The bean type
 * @since 3.5.2
 * @author graemerocher
 */
public final class RuntimeBeanDefinition<T> extends AbstractBeanContextConditional implements BeanDefinitionReference<T>, BeanDefinition<T>, BeanFactory<T> {
    private static final AtomicInteger REF_COUNT = new AtomicInteger(0);
    private final Class<T> beanType;
    private final Supplier<T> supplier;
    private final AnnotationMetadata annotationMetadata;
    private final String beanName;

    RuntimeBeanDefinition(
        @NonNull Class<T> beanType,
        @NonNull Supplier<T> supplier,
        @Nullable AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        Objects.requireNonNull(supplier, "Bean supplier cannot be null");

        this.beanType = beanType;
        this.supplier = supplier;
        this.beanName = generateBeanName(beanType);
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
    }

    private String generateBeanName(Class<T> beanType) {
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
        return BeanDefinitionReference.super.isSingleton();
    }

    @Override
    public T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        return supplier.get();
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param bean The bean
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.5.2
     */
    @NonNull
    public static <B> RuntimeBeanDefinition<B> of(@NonNull B bean) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked") Class<B> t = (Class<B>) bean.getClass();
        return of(t, () -> bean);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param bean The bean
     * @param annotationMetadata The annotation metadata for the bean
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.5.2
     */
    @NonNull
    public static <B> RuntimeBeanDefinition<B> of(@NonNull B bean, AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked") Class<B> t = (Class<B>) bean.getClass();
        return of(t, () -> bean, annotationMetadata);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @return The {@link RuntimeBeanDefinition}
     * @param <B> The bean type
     * @since 3.5.2
     */
    @NonNull
    public static <B> RuntimeBeanDefinition<B> of(Class<B> beanType, @NonNull Supplier<B> beanSupplier) {
        return of(beanType, beanSupplier, null);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @param annotationMetadata The annotation metadata for the bean
     * @return The {@link RuntimeBeanDefinition}
     * @param <B> The bean type
     * @since 3.5.2
     */
    @NonNull
    public static <B> RuntimeBeanDefinition<B> of(@NonNull Class<B> beanType, @NonNull Supplier<B> beanSupplier, @Nullable AnnotationMetadata annotationMetadata) {
        return new RuntimeBeanDefinition<>(beanType, beanSupplier, null);
    }
}
