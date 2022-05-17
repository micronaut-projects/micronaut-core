/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.ValueResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents the resolution context for a current resolve of a given bean.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface BeanResolutionContext extends ValueResolver<CharSequence>, AutoCloseable {

    @Override
    default void close() {
        // no-op
    }

    /**
     * Get a bean of the given type and qualifier.
     *
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean
     * @since 3.5.0
     */
    @NonNull
    <T> T getBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Get all beans of the given type and qualifier.
     *
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found beans
     * @since 3.5.0
     */
    @NonNull
    <T> Collection<T> getBeansOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtains a stream of beans of the given type and qualifier.
     *
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean concrete type
     * @return A stream
     * @since 3.5.0
     */
    @NonNull
    <T> Stream<T> streamOfType(@NonNull  Argument<T> beanType, @Nullable  Qualifier<T> qualifier);

    /**
     * Find an optional bean of the given type and qualifier.
     *
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean wrapped as an {@link Optional}
     * @since 3.5.0
     */
    @NonNull
    <T> Optional<T> findBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Injects a bean.
     *
     * @param beanDefinition The requesting bean definition
     * @param instance       The instance
     * @param <T>            The instance type
     * @return The instance
     * @since 3.5.0
     */
    @NonNull
    <T> T inject(@Nullable BeanDefinition<?> beanDefinition, @NonNull T instance);

    /**
     * Obtains the bean registrations for the given type and qualifier.
     *
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The generic type
     * @return A collection of {@link BeanRegistration}
     * @since 3.5.0
     */
    @NonNull
    <T> Collection<BeanRegistration<T>> getBeanRegistrations(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Call back to destroy any {@link io.micronaut.context.annotation.InjectScope} beans.
     *
     * @see io.micronaut.context.annotation.InjectScope
     * @since 3.1.0
     */
    @UsedByGeneratedCode
    void destroyInjectScopedBeans();

    /**
     * Copy current context to be used later.
     *
     * @return The bean resolution context
     * @since 3.1.0
     */
    BeanResolutionContext copy();

    /**
     * @return The context
     */
    BeanContext getContext();

    /**
     * @return The class requested at the root of this resolution context
     */
    BeanDefinition getRootDefinition();

    /**
     * @return The path that this resolution has taken so far
     */
    Path getPath();

    /**
     * Store a value within the context.
     * @param key The key
     * @param value The value
     * @return The previous value or null
     */
    Object setAttribute(CharSequence key, Object value);

    /**
     * @param key The key
     * @return The attribute value
     */
    Object getAttribute(CharSequence key);

    /**
     * Remove the attribute for the given key.
     * @param key the key
     * @return The previous value
     */
    Object removeAttribute(CharSequence key);

    /**
     * Adds a bean that is created as part of the resolution. This is used to store references to instances passed to {@link BeanContext#inject(Object)}
     * @param beanIdentifier The bean identifier
     * @param beanRegistration The bean registration
     * @param <T> The instance type
     */
    <T> void addInFlightBean(BeanIdentifier beanIdentifier, BeanRegistration<T> beanRegistration);

    /**
     * Removes a bean that is in the process of being created. This is used to store references to instances passed to {@link BeanContext#inject(Object)}
     * @param beanIdentifier The bean identifier
     */
    void removeInFlightBean(BeanIdentifier beanIdentifier);

    /**
     * Obtains an inflight bean for the given identifier. An "In Flight" bean is one that is currently being
     * created but has not finished construction and been registered as a singleton just yet. For example
     * in the case whereby a bean as a {@code PostConstruct} method that also triggers bean resolution of the same bean.
     *
     * @param beanIdentifier The bean identifier
     * @param <T> The bean type
     * @return The bean
     */
    @Nullable <T> BeanRegistration<T> getInFlightBean(BeanIdentifier beanIdentifier);

    /**
     * @return The current bean identifier
     */
    @Nullable Qualifier<?> getCurrentQualifier();

    /**
     * Sets the current qualifier.
     * @param qualifier The qualifier
     */
    void setCurrentQualifier(@Nullable Qualifier<?> qualifier);

    /**
     * Adds a dependent bean to the resolution context.
     * @param beanRegistration The bean registration
     * @param <T> The generic type
     */
    <T> void addDependentBean(BeanRegistration<T> beanRegistration);

    /**
     * @return The dependent beans that must be destroyed by an upstream bean
     */
    default @NonNull List<BeanRegistration<?>> getAndResetDependentBeans() {
        return Collections.emptyList();
    }

    /**
     * @return The current dependent beans that must be destroyed by an upstream bean
     *
     * @since 3.5.0
     */
    default @Nullable List<BeanRegistration<?>> popDependentBeans() {
        return null;
    }

    /**
     * The push the current dependent beans that must be destroyed by an upstream bean.
     *
     * @param dependentBeans Dependent beans collection that can be used to add more dependents
     * @since 3.5.0
     */
    default void pushDependentBeans(@Nullable List<BeanRegistration<?>> dependentBeans) {
    }

    /**
     * Marks first dependent as factory.
     * Dependent can be missing which means it's a singleton or scoped bean.
     *
     * @since 3.5.0
     */
    default void markDependentAsFactory() {
    }

    /**
     * @return The dependent factory beans that was used to create the bean in context
     * @since 3.5.0
     */
    default @Nullable BeanRegistration<?> getAndResetDependentFactoryBean() {
        return null;
    }

    /**
     * Represents a path taken to resolve a bean definitions dependencies.
     */
    interface Path extends Deque<Segment<?>>, AutoCloseable {
        /**
         * Push an unresolved constructor call onto the queue.
         *
         * @param declaringType The type
         * @param beanType      The bean type
         * @return This path
         */
        Path pushBeanCreate(BeanDefinition<?> declaringType, Argument<?> beanType);

        /**
         * Push an unresolved constructor call onto the queue.
         *
         * @param declaringType        The type
         * @param methodName           The method name
         * @param argument             The unresolved argument
         * @param arguments            The arguments
         * @param requiresReflection  is requires reflection
         * @return This path
         */
        Path pushConstructorResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments, boolean requiresReflection);

        /**
         * Push an unresolved constructor call onto the queue.
         *
         * @param declaringType The type
         * @param argument      The unresolved argument
         * @return This path
         */
        Path pushConstructorResolve(BeanDefinition declaringType, Argument argument);

        /**
         * Push an unresolved method call onto the queue.
         *
         * @param declaringType        The type
         * @param methodInjectionPoint The method injection point
         * @param argument             The unresolved argument
         * @return This path
         */
        Path pushMethodArgumentResolve(BeanDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument);

        /**
         * Push an unresolved method call onto the queue.
         *
         * @param declaringType        The type
         * @param methodName           The method name
         * @param argument             The unresolved argument
         * @param arguments            The arguments
         * @param requiresReflection  is requires reflection
         * @return This path
         */
        Path pushMethodArgumentResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments, boolean requiresReflection);

        /**
         * Push an unresolved field onto the queue.
         *
         * @param declaringType       declaring type
         * @param fieldInjectionPoint The field injection point
         * @return This path
         */
        Path pushFieldResolve(BeanDefinition declaringType, FieldInjectionPoint fieldInjectionPoint);

        /**
         * Push an unresolved field onto the queue.
         *
         * @param declaringType       declaring type
         * @param fieldAsArgument     The field as argument
         * @param requiresReflection  is requires reflection
         * @return This path
         */
        Path pushFieldResolve(BeanDefinition declaringType, Argument fieldAsArgument, boolean requiresReflection);

        Path pushAnnotationResolve(BeanDefinition beanDefinition, Argument annotationMemberBeanAsArgument);

        /**
         * Converts the path to a circular string.
         *
         * @return The circular string
         */
        String toCircularString();

        /**
         * @return The current path segment
         */
        Optional<Segment<?>> currentSegment();

        @Override
        default void close() {
            pop();
        }
    }

    /**
     * A segment in a path.
     *
     * @param <T> the bean type
     */
    interface Segment<T> {

        /**
         * @return The type requested
         */
        BeanDefinition<T> getDeclaringType();

        /**
         * @return The inject point
         */
        InjectionPoint<T> getInjectionPoint();

        /**
         * @return The name of the segment. For a field this is the field name, for a method the method name and for a constructor the type name
         */
        String getName();

        /**
         * @return The argument to create the type. For a field this will be empty
         */
        Argument getArgument();
    }
}
