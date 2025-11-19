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

import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.PropertyResolver;
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
import java.util.Map;
import java.util.Optional;

/**
 * Represents the resolution context for a current resolve of a given bean.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@UsedByGeneratedCode
public interface BeanResolutionContext extends ValueResolver<CharSequence>, AutoCloseable, BeanLocator, ConversionServiceProvider {

    @Override
    default void close() {
        // no-op
    }

    /**
     * @return The property resolver
     */
    @Nullable
    PropertyResolver getPropertyResolver();

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
     * @return The configuration path.
     * @since 4.0.0
     */
    @NonNull
    ConfigurationPath getConfigurationPath();

    /**
     * Store a value within the context.
     *
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
     *
     * @param key the key
     * @return The previous value
     */
    Object removeAttribute(CharSequence key);

    /**
     * Get the map representing current attributes.
     *
     * @return All attributes
     * @since 4.0.0
     */
    @Nullable
    Map<CharSequence, Object> getAttributes();

    /**
     * Set new attributes map (The map is supposed to be mutable).
     *
     * @param attributes The attributes
     * @since 4.0.0
     */
    void setAttributes(@Nullable Map<CharSequence, Object> attributes);

    /**
     * Adds a bean that is created as part of the resolution. This is used to store references to instances passed to {@link BeanContext#inject(Object)}.
     *
     * @param beanIdentifier The bean identifier
     * @param beanRegistration The bean registration
     * @param <T> The instance type
     */
    <T> void addInFlightBean(BeanIdentifier beanIdentifier, BeanRegistration<T> beanRegistration);

    /**
     * Removes a bean that is in the process of being created. This is used to store references to instances passed to {@link BeanContext#inject(Object)}.
     *
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
     *
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
    @UsedByGeneratedCode
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
     * Sets the configuration path.
     * @param configurationPath The configuration path.
     * @return The previous path
     */
    @Nullable
    ConfigurationPath setConfigurationPath(@Nullable ConfigurationPath configurationPath);

    /**
     * Resolve a property value.
     * @param argument The argument
     * @param stringValue The string value
     * @param cliProperty The CLI property
     * @param isPlaceholder Whether it is a place holder
     * @return The resolved value
     */
    @Nullable Object resolvePropertyValue(Argument<?> argument, String stringValue, String cliProperty, boolean isPlaceholder);

    /**
     * Callback when a value is resolved in some other context.
     * @param argument The argument
     * @param qualifier The qualifier
     * @param property The property
     * @param value The value
     */
    void valueResolved(Argument<?> argument, Qualifier<?> qualifier, String property, @Nullable Object value);

    /**
     * Resolves the proxy target for a given proxy bean definition. If the bean has no proxy then the original bean is returned.
     *
     * @param definition        The proxy bean definition
     * @param beanType          The bean type
     * @param qualifier         The bean qualifier
     * @param <T>               The generic type
     * @return The proxied instance
     * @since 5.0
     */
    @UsedByGeneratedCode
    @NonNull
    <T> T getProxyTargetBean(@NonNull BeanDefinition<T> definition,
                             @NonNull Argument<T> beanType,
                             @Nullable Qualifier<T> qualifier);

    /**
     * Represents a path taken to resolve a bean definitions dependencies.
     */
    interface Path extends Deque<Segment<?, ?>>, AutoCloseable {
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
         * @return This path
         */
        Path pushConstructorResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments);

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
         * @return This path
         */
        Path pushMethodArgumentResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments);

        /**
         * Push resolution of an event listener
         * @param declaringType The declaration type
         * @param eventType The event type
         * @return The path
         */
        Path pushEventListenerResolve(BeanDefinition<?> declaringType, Argument<?> eventType);

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
         * @return This path
         */
        Path pushFieldResolve(BeanDefinition declaringType, Argument fieldAsArgument);

        /**
         * Push resolution of a bean from an annotation.
         * @param beanDefinition The bean definition
         * @param annotationMemberBeanAsArgument The annotation member
         * @return The path
         */
        Path pushAnnotationResolve(BeanDefinition beanDefinition, Argument annotationMemberBeanAsArgument);

        /**
         * Converts the path to a circular string.
         *
         * @return The circular string
         */
        String toCircularString();

        /**
         * Converts the path to a circular string.
         *
         * @param ansiSupported  Whether ANSI colour is supported
         * @return The circular string
         * @since 4.8.0
         */
        default String toConsoleCircularString(boolean ansiSupported) {
            return toCircularString();
        }

        /**
         * Converts the path to a string.
         *
         * @param ansiSupported  Whether ANSI colour is supported
         * @return The string
         * @since 4.8.0
         */
        default String toConsoleString(boolean ansiSupported) {
            return toString();
        }

        /**
         * @return The current path segment
         */
        Optional<Segment<?, ?>> currentSegment();

        @Override
        default void close() {
            pop();
        }
    }

    /**
     * A segment in a path.
     *
     * @param <B> the declaring type
     * @param <T> the injected type
     */
    interface Segment<B, T> {

        /**
         * To a console string.
         * @param ansiSupported Whether ansi is supported
         * @return The string
         */
        default String toConsoleString(boolean ansiSupported) {
            return toString();
        }

        /**
         * @return The type requested
         */
        BeanDefinition<B> getDeclaringType();

        /**
         * @return The declaring type qualifier
         * @since 4.5.0
         */
        Qualifier<B> getDeclaringTypeQualifier();

        /**
         * @return The inject point
         */
        InjectionPoint<B> getInjectionPoint();

        /**
         * @return The name of the segment. For a field this is the field name, for a method the method name and for a constructor the type name
         */
        String getName();

        /**
         * @return The argument to create the type. For a field this will be empty
         */
        Argument<T> getArgument();
    }
}
