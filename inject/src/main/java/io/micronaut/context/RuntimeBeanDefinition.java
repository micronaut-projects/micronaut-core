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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanContextConditional;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Allow the construction for bean definitions programmatically that can be registered
 * via {@link BeanDefinitionRegistry} at runtime.
 *
 * <p>This differs from {@link BeanDefinitionRegistry#registerSingleton(Object)} in that
 * beans registered this way can be created lazily or not at all and participate
 * more completely in the life cycle of the {@link BeanContext} (for examples event listeners like {@link io.micronaut.context.event.BeanCreatedEventListener} will be fired).</p>
 *
 * <p></p>
 *
 * @param <T> The bean type
 * @since 3.6.0
 * @author graemerocher
 * @see BeanDefinitionRegistry#registerBeanDefinition(RuntimeBeanDefinition)
 */
@Experimental
public interface RuntimeBeanDefinition<T> extends BeanDefinitionReference<T>, BeanDefinition<T>, BeanFactory<T>, BeanContextConditional {

    @Override
    @NonNull
    default AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    default boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    default BeanDefinition<T> load() {
        return this;
    }

    @Override
    default String getBeanDefinitionName() {
        return DefaultRuntimeBeanDefinition.generateBeanName(getBeanType());
    }

    @Override
    default BeanDefinition<T> load(BeanContext context) {
        return this;
    }

    @Override
    default boolean isPresent() {
        return true;
    }

    @Override
    default boolean isSingleton() {
        return BeanDefinitionReference.super.isSingleton();
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param bean The bean
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(@NonNull B bean) {
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
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(@NonNull B bean, @Nullable AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked") Class<B> t = (Class<B>) bean.getClass();
        return of(t, () -> bean, annotationMetadata);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param bean The bean
     * @param qualifier The qualifier
     * @param annotationMetadata The annotation metadata for the bean
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(@NonNull B bean, @Nullable Qualifier<B> qualifier, @Nullable AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked") Class<B> t = (Class<B>) bean.getClass();
        return of(t, () -> bean, qualifier, annotationMetadata);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param bean The bean
     * @param qualifier The qualifier
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(@NonNull B bean, @Nullable Qualifier<B> qualifier) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked") Class<B> t = (Class<B>) bean.getClass();
        return of(t, () -> bean, qualifier, null);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @return The {@link RuntimeBeanDefinition}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(
        Class<B> beanType,
        @NonNull Supplier<B> beanSupplier) {
        return of(beanType, beanSupplier, null, null);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @param annotationMetadata The annotation metadata for the bean
     * @return The {@link RuntimeBeanDefinition}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(
        @NonNull Class<B> beanType,
        @NonNull Supplier<B> beanSupplier,
        @Nullable AnnotationMetadata annotationMetadata) {
        return of(beanType, beanSupplier, null, annotationMetadata);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @param qualifier   The qualifier
     * @return The {@link RuntimeBeanDefinition}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(
        @NonNull Class<B> beanType,
        @NonNull Supplier<B> beanSupplier,
        @Nullable Qualifier<B> qualifier) {
        return of(beanType, beanSupplier, qualifier, null);
    }

    /**
     * Creates a new reference for the given object with empty annotation metadata.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @param qualifier   The qualifier
     * @param annotationMetadata The annotation metadata for the bean
     * @return The {@link RuntimeBeanDefinition}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(
        @NonNull Class<B> beanType,
        @NonNull Supplier<B> beanSupplier,
        @Nullable Qualifier<B> qualifier,
        @Nullable AnnotationMetadata annotationMetadata) {
        return new DefaultRuntimeBeanDefinition<>(
            beanType,
            beanSupplier,
            qualifier,
            annotationMetadata
        );
    }
}
