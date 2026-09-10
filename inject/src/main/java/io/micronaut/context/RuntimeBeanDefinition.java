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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanContextConditional;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
    default AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    default boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    default List<Argument<?>> getTypeArguments(Class<?> type) {
        Class<T> beanType = getBeanType();
        if (type != null && type.isAssignableFrom(beanType)) {
            return Arrays.stream(GenericTypeUtils.resolveTypeArguments(beanType, type))
                .map(Argument::of)
                .collect(Collectors.toList());
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
    static <B> RuntimeBeanDefinition<B> of(B bean) {
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
    static <B> RuntimeBeanDefinition<B> of(
        Class<B> beanType,
        Supplier<B> beanSupplier) {
        return builder(beanType, beanSupplier).build();
    }

    /**
     * A new builder for constructing and configuring runtime created beans.
     * @param bean The bean to use
     * @return The builder
     * @param <B> The bean type
     */
    static <B> Builder<B> builder(B bean) {
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
    static <B> Builder<B> builder(Class<B> beanType, Supplier<B> beanSupplier) {
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
    static <B> Builder<B> builder(Argument<B> beanType, Supplier<B> beanSupplier) {
        return new DefaultRuntimeBeanDefinition.RuntimeBeanBuilder<>(
            beanType,
            beanSupplier
        );
    }

    /**
     * A new builder for constructing and configuring runtime created beans where the bean factory
     * receives the {@link CreationContext} of each creation.
     *
     * <p>The creation context carries the beans resolved for the injection points declared with
     * {@link Builder#injectionPoint(Argument)} and the injection point the bean is being created for.</p>
     *
     * @param beanType The bean type
     * @param beanFactory The bean factory that receives the {@link CreationContext}
     * @return The builder
     * @param <B> The bean type
     * @since 5.2.0
     */
    @Experimental
    static <B> Builder<B> builder(Class<B> beanType, Function<CreationContext, B> beanFactory) {
        return new DefaultRuntimeBeanDefinition.RuntimeBeanBuilder<>(
            Argument.of(beanType),
            beanFactory
        );
    }

    /**
     * A new builder for constructing and configuring runtime created beans where the bean factory
     * receives the {@link CreationContext} of each creation.
     *
     * <p>The creation context carries the beans resolved for the injection points declared with
     * {@link Builder#injectionPoint(Argument)} and the injection point the bean is being created for.</p>
     *
     * @param beanType The bean type
     * @param beanFactory The bean factory that receives the {@link CreationContext}
     * @return The builder
     * @param <B> The bean type
     * @since 5.2.0
     */
    @Experimental
    static <B> Builder<B> builder(Argument<B> beanType, Function<CreationContext, B> beanFactory) {
        return new DefaultRuntimeBeanDefinition.RuntimeBeanBuilder<>(
            beanType,
            beanFactory
        );
    }

    /**
     * The lookups a runtime built bean can perform while it is being created or disposed of.
     *
     * <p>Every lookup here is resolved through the {@link BeanResolutionContext} of the creation or of the
     * disposal, which is what makes core's own dependency handling apply to it: a dependent object
     * (a {@code @Prototype} or {@code @Dependent} bean, for example) that a lookup resolves becomes a dependent
     * of that creation or disposal and is destroyed with it, a circular dependency is detected, and an
     * unsatisfied {@link #getBean(Argument)} fails with the usual
     * {@link io.micronaut.context.exceptions.DependencyInjectionException}. Resolving the same bean from
     * {@link #getBeanContext()} instead resolves it outside that context, where none of that applies.</p>
     *
     * <p>How long what a lookup resolves lives is decided by the context it was made from:
     * a dependent resolved from a {@link CreationContext} is destroyed when the created bean is, and one
     * resolved from a {@link DisposalContext} is destroyed when the disposer returns.</p>
     *
     * @since 5.2.0
     */
    @Experimental
    interface LookupContext {

        /**
         * @return The bean context the bean belongs to
         */
        BeanContext getBeanContext();

        /**
         * Looks up a bean of the given type.
         *
         * @param type The type to look up
         * @return The bean, never {@code null}
         * @param <V> The looked up type
         * @throws io.micronaut.context.exceptions.DependencyInjectionException If no bean of the type exists
         */
        default <V> V getBean(Argument<V> type) {
            return getBean(type, null);
        }

        /**
         * Looks up a bean of the given type and qualifier.
         *
         * @param type The type to look up
         * @param qualifier The qualifier, or {@code null} for none
         * @return The bean, never {@code null}
         * @param <V> The looked up type
         * @throws io.micronaut.context.exceptions.DependencyInjectionException If no such bean exists
         */
        <V> V getBean(Argument<V> type, @Nullable Qualifier<V> qualifier);

        /**
         * Looks up a bean of the given type if one exists.
         *
         * @param type The type to look up
         * @return The bean, or empty if none exists
         * @param <V> The looked up type
         */
        default <V> Optional<V> findBean(Argument<V> type) {
            return findBean(type, null);
        }

        /**
         * Looks up a bean of the given type and qualifier if one exists.
         *
         * @param type The type to look up
         * @param qualifier The qualifier, or {@code null} for none
         * @return The bean, or empty if none exists
         * @param <V> The looked up type
         */
        <V> Optional<V> findBean(Argument<V> type, @Nullable Qualifier<V> qualifier);

        /**
         * Looks up all the beans of the given type.
         *
         * @param type The type to look up
         * @return The beans, empty if there are none
         * @param <V> The looked up type
         */
        default <V> Collection<V> getBeansOfType(Argument<V> type) {
            return getBeansOfType(type, null);
        }

        /**
         * Looks up all the beans of the given type and qualifier.
         *
         * @param type The type to look up
         * @param qualifier The qualifier, or {@code null} for none
         * @return The beans, empty if there are none
         * @param <V> The looked up type
         */
        <V> Collection<V> getBeansOfType(Argument<V> type, @Nullable Qualifier<V> qualifier);
    }

    /**
     * The context of a single creation of a bean built at runtime, passed to the bean factory of a definition
     * built with {@link #builder(Argument, Function)}.
     *
     * <p>It carries the beans resolved for the injection points the definition declared with
     * {@link Builder#injectionPoint(Argument)}, the injection point the bean is being created for, and the
     * lookups of {@link LookupContext} for anything the definition could not declare up front.</p>
     *
     * <p>The injected beans were resolved through the resolution context of this creation, so any of them that
     * is a dependent object (a {@code @Prototype} or {@code @Dependent} bean, for example) is a dependent of the
     * created bean and is destroyed with it. A bean the factory looks up itself with
     * {@link #getBean(Argument)} is resolved through the same resolution context and has the same lifetime; the
     * difference between the two is only that a declared injection point is described by the definition and
     * resolved before the factory runs, while a lookup is decided by the factory as it runs. The exception is a
     * bean obtained from {@link BeanContext#createBean(Class)}, which records no dependents for any bean,
     * runtime built or compiled.</p>
     *
     * @since 5.2.0
     */
    @Experimental
    interface CreationContext extends LookupContext {

        /**
         * The injection point the bean is being created for.
         *
         * <p>For a constructor or method argument this is an
         * {@link io.micronaut.inject.ArgumentInjectionPoint}, which exposes the argument and the bean that
         * declares it. It is empty when the bean is not being created for an injection point, as it is for a
         * direct {@link BeanContext#getBean(Class)} lookup.</p>
         *
         * @return The injection point, or empty if there is none
         */
        Optional<InjectionPoint<?>> getInjectionPoint();

        /**
         * @return The number of injection points the definition declared
         */
        int getInjectedBeanCount();

        /**
         * The bean resolved for the injection point at the given index, in the order the injection points were
         * declared on the builder.
         *
         * @param index The index
         * @return The resolved bean, which is {@code null} only when the injection point was declared with a
         *         nullable argument and no bean was found
         * @param <V> The injected type
         * @throws IndexOutOfBoundsException If the index is out of range
         */
        <V> @Nullable V getInjectedBean(int index);

        /**
         * The bean resolved for the injection point declared with the given type and no qualifier.
         *
         * @param type The type the injection point was declared with
         * @return The resolved bean, which is {@code null} only when the injection point was declared with a
         *         nullable argument and no bean was found
         * @param <V> The injected type
         * @throws IllegalArgumentException If no such injection point was declared
         */
        default <V> @Nullable V getInjectedBean(Argument<V> type) {
            return getInjectedBean(type, null);
        }

        /**
         * The bean resolved for the injection point declared with the given type and qualifier.
         *
         * @param type The type the injection point was declared with
         * @param qualifier The qualifier the injection point was declared with
         * @return The resolved bean, which is {@code null} only when the injection point was declared with a
         *         nullable argument and no bean was found
         * @param <V> The injected type
         * @throws IllegalArgumentException If no such injection point was declared
         */
        <V> @Nullable V getInjectedBean(Argument<V> type, @Nullable Qualifier<V> qualifier);
    }

    /**
     * The context of a single disposal of a bean built at runtime, passed to the disposer of a definition built
     * with {@link Builder#injectedDisposer(BiConsumer)}.
     *
     * <p>It offers the same lookups as the {@link CreationContext} of a creation, resolved fresh for this
     * disposal: the disposer shares neither the resolution context nor the resolved instances with the creation
     * of the bean it is disposing of, so a lookup of a dependent object returns an instance of its own, not the
     * one the factory received for the same type.</p>
     *
     * <p>What a lookup resolves is destroyed when the disposer returns, unlike a bean resolved during creation,
     * which is destroyed with the created bean. A bean whose lifetime the context manages, a singleton for
     * example, is of course not destroyed by the disposal.</p>
     *
     * @since 5.2.0
     */
    @Experimental
    interface DisposalContext extends LookupContext {
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
        Builder<B> qualifier(@Nullable Qualifier<B> qualifier);

        /**
         * Adds this type as a bean replacement of the given type.
         * @param otherType The other type
         * @return This bean builder
         * @since 4.0.0
         */
        Builder<B> replaces(@Nullable Class<? extends B> otherType);

        /**
         * The qualifier to use.
         * @param name The named qualifier to use.
         * @return This builder
         * @since 3.7.0
         */
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
        Builder<B> scope(@Nullable Class<? extends Annotation> scope);

        /**
         * Is the bean singleton.
         * @param isSingleton True if it is singleton
         * @return This builder
         */
        Builder<B> singleton(boolean isSingleton);

        /**
         * Limit the exposed types of this bean.
         * @param types The exposed types
         * @return This builder
         */
        Builder<B> exposedTypes(Class<?>...types);

        /**
         * The type arguments for the type.
         * @param arguments The arguments
         * @return This builder
         */
        Builder<B> typeArguments(Argument<?>... arguments);

        /**
         * The type arguments for an implemented type of this type.
         * @param implementedType The implemented type
         * @param arguments The arguments
         * @return This builder
         */
        Builder<B> typeArguments(Class<?> implementedType, Argument<?>... arguments);

        /**
         * The annotation metadata for the bean.
         * @param annotationMetadata The annotation metadata
         * @return This builder
         */
        Builder<B> annotationMetadata(@Nullable AnnotationMetadata annotationMetadata);

        /**
         * Declares an injection point of this bean.
         *
         * <p>The declared injection points are resolved through the resolution context of the bean creation,
         * in the order they were declared, and are handed to the bean factory of a definition built
         * with {@link RuntimeBeanDefinition#builder(Argument, Function)} as its {@link CreationContext}. Resolving them
         * this way is what makes core's own dependency handling apply to a runtime built bean: a dependent object
         * resolved for an injection point becomes a dependent of the created bean and is destroyed with it,
         * a circular dependency is detected, and an unsatisfied injection point fails the creation with the usual
         * {@link io.micronaut.context.exceptions.DependencyInjectionException}.</p>
         *
         * <p>An injection point declared with a {@link Argument#isDeclaredNullable() nullable} argument resolves
         * to {@code null} instead of failing when no bean is found.</p>
         *
         * <p>The declared injection points are also the arguments of {@link BeanDefinition#getConstructor()} and
         * the types of {@link BeanDefinition#getRequiredComponents()}, so that the bean's dependencies can be
         * described the way a compiled bean's are.</p>
         *
         * @param type The type to inject
         * @return This builder
         * @since 5.2.0
         */
        @Experimental
        default Builder<B> injectionPoint(Argument<?> type) {
            return injectionPoint(type, null);
        }

        /**
         * Declares a qualified injection point of this bean.
         *
         * @param type The type to inject
         * @param qualifier The qualifier, or {@code null} for none
         * @return This builder
         * @see #injectionPoint(Argument)
         * @since 5.2.0
         */
        @Experimental
        Builder<B> injectionPoint(Argument<?> type, @Nullable Qualifier<?> qualifier);

        /**
         * The disposer to run when an instance created by this definition is destroyed.
         *
         * <p>The disposer is invoked once per instance, after the
         * {@link io.micronaut.context.event.BeanPreDestroyEventListener} instances and before the
         * {@link io.micronaut.context.event.BeanDestroyedEventListener} instances, whenever the instance is destroyed
         * by the {@link BeanContext}: when the {@link BeanRegistration} of a non-singleton is closed, when a singleton
         * is destroyed through {@link BeanContext#destroyBean(Object)} or when the context itself is closed.</p>
         *
         * <p>A definition built with a disposer is a {@link io.micronaut.inject.DisposableBeanDefinition}; a
         * definition built without one is not, so existing definitions keep their current behaviour.</p>
         *
         * <p>A disposer that needs to resolve beans of its own takes a {@link DisposalContext} instead, see
         * {@link #injectedDisposer(BiConsumer)}. The two forms are alternatives: setting one clears the
         * other.</p>
         *
         * @param disposer The disposer, receiving the bean context and the instance being destroyed, or {@code null}
         *                 to clear a previously set disposer
         * @return This builder
         * @since 5.2.0
         */
        Builder<B> disposer(@Nullable BiConsumer<BeanContext, B> disposer);

        /**
         * The disposer to run when an instance created by this definition is destroyed, receiving a
         * {@link DisposalContext} with which it can resolve the beans it needs to do the disposal.
         *
         * <p>It is invoked at the same point in the destruction of the instance as
         * {@link #disposer(BiConsumer)}, and the two forms are alternatives: setting one clears the other.</p>
         *
         * <p>The lookups of the disposal context are resolved through a resolution context created for the
         * disposal, which shares nothing with the creation of the bean being disposed of: what the disposer
         * resolves is resolved fresh, and a dependent object among what it resolved is destroyed when the
         * disposer returns rather than being kept for the lifetime of anything.</p>
         *
         * @param disposer The disposer, receiving the disposal context and the instance being destroyed, or
         *                 {@code null} to clear a previously set disposer
         * @return This builder
         * @since 5.2.0
         */
        @Experimental
        Builder<B> injectedDisposer(@Nullable BiConsumer<DisposalContext, B> disposer);

        /**
         * Builds the runtime bean.
         * @return The runtime bean
         */
        RuntimeBeanDefinition<B> build();
    }
}
