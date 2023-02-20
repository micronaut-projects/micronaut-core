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

import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanContextConditional;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Allow the construction for bean definitions programmatically that can be registered
 * via {@link BeanDefinitionRegistry} at runtime.
 *
 * <p>This differs from {@link BeanDefinitionRegistry#registerSingleton(Object)} in that
 * beans registered this way can be created lazily or not at all and participate
 * more completely in the life cycle of the {@link BeanContext} (for examples event listeners like {@link io.micronaut.context.event.BeanCreatedEventListener} will be fired).</p>
 *
 * <p>Note that it is generally not recommended to use this approach and build time bean computation is preferred. This type is
 * designed to support a few limited use cases where runtime bean registration is required.</p>
 *
 * @param <T> The bean type
 * @since 3.6.0
 * @author graemerocher
 * @see BeanDefinitionRegistry#registerBeanDefinition(RuntimeBeanDefinition)
 */
@Experimental
public interface RuntimeBeanDefinition<T> extends BeanDefinitionReference<T>, InstantiatableBeanDefinition<T>, BeanContextConditional {

    @Override
    @NonNull
    default AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    default boolean isEnabled(@NonNull BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    default List<Argument<?>> getTypeArguments(Class<?> type) {
        Class<T> beanType = getBeanType();
        if (type != null && type.isAssignableFrom(beanType)) {
            if (type.isInterface()) {
                return Arrays.stream(GenericTypeUtils.resolveInterfaceTypeArguments(beanType, type))
                    .map(Argument::of)
                    .collect(Collectors.toList());
            } else {
                return Arrays.stream(GenericTypeUtils.resolveSuperTypeGenericArguments(beanType, type))
                    .map(Argument::of)
                    .collect(Collectors.toList());
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    default boolean isContextScope() {
        return getAnnotationMetadata().hasDeclaredAnnotation(Context.class);
    }

    @Override
    default boolean isConfigurationProperties() {
        return BeanDefinitionReference.super.isConfigurationProperties();
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
     * Creates a new effectively singleton bean definition that references the given bean.
     *
     * @param bean The bean
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(@NonNull B bean) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked") Class<B> t = (Class<B>) bean.getClass();
        return builder(t, () -> bean).singleton(true).build();
    }

    /**
     * Creates a new bean definition that will resolve the bean from the given supplier.
     *
     * <p>The bean is by default not singleton and the supplier will be invoked for each injection point.</p>
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @return The {@link BeanDefinitionReference}
     * @param <B> The bean type
     * @since 3.6.0
     */
    @NonNull
    static <B> RuntimeBeanDefinition<B> of(
        @NonNull Class<B> beanType,
        @NonNull Supplier<B> beanSupplier) {
        return builder(beanType, beanSupplier).build();
    }

    /**
     * A new builder for constructing and configuring runtime created beans.
     * @param bean The bean to use
     * @return The builder
     * @param <B> The bean type
     */
    @NonNull
    static <B> Builder<B> builder(@NonNull B bean) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        @SuppressWarnings("unchecked")
        Argument<B> beanType = (Argument<B>) Argument.of(bean.getClass());
        return new DefaultRuntimeBeanDefinition.RuntimeBeanBuilder<>(
            beanType,
            () -> bean
        ).singleton(true);
    }

    /**
     * A new builder for constructing and configuring runtime created beans.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @return The builder
     * @param <B> The bean type
     */
    @NonNull
    static <B> Builder<B> builder(@NonNull Class<B> beanType, @NonNull Supplier<B> beanSupplier) {
        return new DefaultRuntimeBeanDefinition.RuntimeBeanBuilder<>(
            Argument.of(beanType),
            beanSupplier
        );
    }

    /**
     * A new builder for constructing and configuring runtime created beans.
     * @param beanType The bean type
     * @param beanSupplier The bean supplier
     * @return The builder
     * @param <B> The bean type
     */
    @NonNull
    static <B> Builder<B> builder(@NonNull Argument<B> beanType, @NonNull Supplier<B> beanSupplier) {
        return new DefaultRuntimeBeanDefinition.RuntimeBeanBuilder<>(
            beanType,
            beanSupplier
        );
    }

    /**
     * A builder for constructing {@link RuntimeBeanDefinition} instances.
     * @param <B> The bean type
     */
    interface Builder<B> {
        /**
         * The qualifier to use.
         * @param qualifier The qualifier
         * @return This builder
         */
        @NonNull
        Builder<B> qualifier(@Nullable Qualifier<B> qualifier);

        /**
         * Adds this type as a bean replacement of the given type.
         * @param otherType The other type
         * @return This bean builder
         * @since 4.0.0
         */
        @NonNull
        Builder<B> replaces(@Nullable Class<? extends B> otherType);

        /**
         * The qualifier to use.
         * @param name The named qualifier to use.
         * @return This builder
         * @since 3.7.0
         */
        @NonNull
        default Builder<B> named(@Nullable String name) {
            if (name == null) {
                qualifier(null);
            } else {
                qualifier(Qualifiers.byName(name));
            }
            return this;
        }

        /**
         * The scope to use.
         * @param scope The scope
         * @return This builder
         */
        @NonNull
        Builder<B> scope(@Nullable Class<? extends Annotation> scope);

        /**
         * Is the bean singleton.
         * @param isSingleton True if it is singleton
         * @return This builder
         */
        @NonNull
        Builder<B> singleton(boolean isSingleton);

        /**
         * Limit the exposed types of this bean.
         * @param types The exposed types
         * @return This builder
         */
        @NonNull
        Builder<B> exposedTypes(Class<?>...types);

        /**
         * The type arguments for the type.
         * @param arguments The arguments
         * @return This builder
         */
        @NonNull
        Builder<B> typeArguments(Argument<?>... arguments);

        /**
         * The type arguments for an implemented type of this type.
         * @param implementedType The implemented type
         * @param arguments The arguments
         * @return This builder
         */
        @NonNull
        Builder<B> typeArguments(Class<?> implementedType, Argument<?>... arguments);

        /**
         * The annotation metadata for the bean.
         * @param annotationMetadata The annotation metadata
         * @return This builder
         */
        @NonNull
        Builder<B> annotationMetadata(@Nullable AnnotationMetadata annotationMetadata);

        /**
         * Builds the runtime bean.
         * @return The runtime bean
         */
        @NonNull
        RuntimeBeanDefinition<B> build();
    }
}
