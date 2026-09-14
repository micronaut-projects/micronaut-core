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
import org.jspecify.annotations.Nullable;
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

    /**
     * Attribute that exposes the dependent bean registrations already created for the bean this context is operating
     * on.
     *
     * @since 5.2.0
     * @deprecated Since 5.3.0 the context a bean is destroyed with carries the dependents of that bean itself, so
     * {@link #getDependentBeans()} answers during destruction as it does during creation. Still set for a reader
     * compiled against an earlier version.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    String EXISTING_DEPENDENT_BEANS = "io.micronaut.context.existingDependentBeans";

    /**
     * Attribute used while a bean is being created to transfer the interceptor registrations selected for that bean
     * to its {@link BeanRegistration}.
     *
     * <p>The value is an identity map keyed by {@link BeanDefinition}. It is consumed by the bean context after
     * creation and must be treated as an implementation detail.</p>
     *
     * @since 5.2.0
     * @deprecated Since 5.3.0 nothing reads it: an interception point resolves its interceptors as the bean's own,
     * see {@link #getInterceptorRegistrations(Argument, Qualifier)}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    String INTERCEPTOR_REGISTRATIONS = "io.micronaut.aop.interceptorRegistrations";

    /**
     * Attribute used while a bean with constructor advice is being constructed to share the interceptor registrations
     * selected for it with its post-construct interception.
     *
     * <p>The value is a mutable stack that belongs to the creation in progress and must be treated as an
     * implementation detail.</p>
     *
     * @since 5.2.1
     */
    String SHARED_INTERCEPTOR_REGISTRATIONS = "io.micronaut.aop.sharedInterceptorRegistrations";

    /**
     * Attribute that exposes the interceptor registrations selected while creating the bean being disposed.
     *
     * <p>The value is a read-only {@code List<BeanRegistration<?>>}. It is set only while a bean is being disposed and
     * is intended for lifecycle interception.</p>
     *
     * @since 5.2.0
     * @deprecated Since 5.3.0 nothing sets or reads it: pre-destroy interception reuses the dependents handed over
     * as {@link #EXISTING_DEPENDENT_BEANS}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    String EXISTING_INTERCEPTOR_REGISTRATIONS = "io.micronaut.aop.existingInterceptorRegistrations";

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
    <T> Collection<BeanRegistration<T>> getBeanRegistrations(Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtains the registrations of the interceptors bound to the bean this context resolves for.
     *
     * <p>The interceptors of a bean are the bean's own. A singleton, or an interceptor of a custom scope, comes from
     * its scope as always. Any other interceptor is the one the bean already has among {@link #getDependentBeans()},
     * created for it earlier, and is otherwise created now as a new dependent of the bean, so that it is destroyed
     * with the bean. That is what gives every interception point of a bean, from its construction to its
     * destruction, the same instance of a non-singleton interceptor.</p>
     *
     * <p>A context of the container answers so. The default here is for a context of another kind, which owns
     * nothing: it resolves as {@link #getBeanRegistrations(Argument, Qualifier)} does.</p>
     *
     * @param interceptorType The interceptor type
     * @param binding         The interceptor binding qualifier
     * @param <I>             The interceptor type
     * @return The registrations
     * @since 5.3.0
     */
    default <I> Collection<BeanRegistration<I>> getInterceptorRegistrations(Argument<I> interceptorType, @Nullable Qualifier<I> binding) {
        return getBeanRegistrations(interceptorType, binding);
    }

    /**
     * Obtains the registration of one interceptor bound to the bean this context resolves for, the bean's own
     * instance of it, as {@link #getInterceptorRegistrations(Argument, Qualifier)} would list it.
     *
     * @param interceptor The interceptor definition
     * @param <I>         The interceptor type
     * @return The registration
     * @since 5.3.0
     */
    default <I> BeanRegistration<I> getInterceptorRegistration(BeanDefinition<I> interceptor) {
        return getContext().getBeanRegistration(interceptor);
    }

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
     * Copy current context to be used later when resolving a lazy proxy target.
     *
     * @param proxyBeanDefinition The proxy bean definition
     * @return The bean resolution context
     * @since 5.1.0
     */
    @UsedByGeneratedCode
    default BeanResolutionContext copyForLazyProxyTarget(BeanDefinition<?> proxyBeanDefinition) {
        return copy();
    }

    /**
     * Returns whether a newly constructed bean instance should receive field/method injection and initialization
     * callbacks.
     *
     * @param beanDefinition The bean definition
     * @param bean The newly constructed bean instance
     * @return True if the bean instance should be injected and initialized
     * @since 5.1.0
     */
    @UsedByGeneratedCode
    default boolean shouldInitializeBean(BeanDefinition<?> beanDefinition, Object bean) {
        BeanContext context = getContext();
        if (context instanceof DefaultBeanContext defaultBeanContext) {
            return defaultBeanContext.getBeanResolutionCustomizer().shouldInitializeBean(this, beanDefinition, bean);
        }
        return true;
    }

    /**
     * @return The context
     */
    BeanContext getContext();

    /**
     * @return The class requested at the root of this resolution context
     */
    @Nullable
    BeanDefinition getRootDefinition();

    /**
     * @return The path that this resolution has taken so far
     */
    Path getPath();

    /**
     * @return The configuration path.
     * @since 4.0.0
     */
    ConfigurationPath getConfigurationPath();

    /**
     * Store a value within the context.
     *
     * @param key The key
     * @param value The value
     * @return The previous value or null
     */
    @Nullable
    Object setAttribute(CharSequence key, @Nullable Object value);

    /**
     * @param key The key
     * @return The attribute value
     */
    @Nullable
    Object getAttribute(CharSequence key);

    /**
     * Remove the attribute for the given key.
     *
     * @param key the key
     * @return The previous value
     */
    @Nullable
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
     * The definition of the bean being instantiated through this context, while its constructor runs. A generated
     * proxy resolves its interceptors from its constructor, and a resolution that starts there is recorded on the
     * path against this definition, as a resolution of a constructor argument would be.
     *
     * @return The definition being instantiated, or {@code null} outside an instantiation
     * @since 5.3.0
     */
    @Nullable
    default BeanDefinition<?> getCurrentBeanDefinition() {
        return null;
    }

    /**
     * Sets the definition of the bean being instantiated. Set by the container around an instantiation and restored
     * afterwards.
     *
     * @param beanDefinition The definition, or {@code null} once the instantiation is over
     * @since 5.3.0
     */
    default void setCurrentBeanDefinition(@Nullable BeanDefinition<?> beanDefinition) {
    }

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
    default List<BeanRegistration<?>> getAndResetDependentBeans() {
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
     * The dependent beans of the bean this context operates on: the beans created for it so far while it is being
     * created, and the beans that were created with it when it is being destroyed. A non-singleton interceptor
     * created for the bean is among them, which is how {@link #getInterceptorRegistrations(Argument, Qualifier)}
     * gives every interception point of the bean the same instance.
     *
     * @return The dependent beans, never {@code null}
     * @since 5.1.0
     */
    default List<BeanRegistration<?>> getDependentBeans() {
        return Collections.emptyList();
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
     * <p>Superseded by {@link #markDependentAsFactory(Object)}, which generated code calls now; kept for bean
     * definitions compiled by earlier versions.</p>
     *
     * @since 3.5.0
     */
    @UsedByGeneratedCode
    default void markDependentAsFactory() {
    }

    /**
     * Marks the dependent registration of the given factory bean as the factory that produces the bean being
     * created, so that a factory which is itself a dependent, a prototype for instance, is destroyed once it has
     * produced the bean. A singleton or scoped factory has no dependent registration, and nothing is marked.
     *
     * <p>Unlike {@link #markDependentAsFactory()}, which marks whichever dependent was resolved first, this finds
     * the registration by the factory instance, so that dependents resolved before the factory was looked up, such
     * as the interceptors of a bean whose creation is advised, are left alone.</p>
     *
     * @param factoryBean The factory bean that was just looked up
     * @since 5.2.2
     */
    @UsedByGeneratedCode
    default void markDependentAsFactory(Object factoryBean) {
        markDependentAsFactory();
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
    @Nullable Object resolvePropertyValue(Argument<?> argument, String stringValue, @Nullable String cliProperty, boolean isPlaceholder);

    /**
     * Callback when a value is resolved in some other context.
     * @param argument The argument
     * @param qualifier The qualifier
     * @param property The property
     * @param value The value
     */
    void valueResolved(Argument<?> argument, @Nullable Qualifier<?> qualifier, String property, @Nullable Object value);

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
    <T> T getProxyTargetBean(BeanDefinition<T> definition,
                             Argument<T> beanType,
                             @Nullable Qualifier<T> qualifier);

    /**
     * Resolves the registration of the proxy target for a given proxy bean definition.
     *
     * <p>Where {@link #getProxyTargetBean(BeanDefinition, Argument, Qualifier)} returns the target alone, this
     * returns the registration the context holds for it: the singleton scope's for a singleton, the one a custom
     * scope keeps for a scoped bean, or the one created for a prototype. A generated proxy fronting a target that
     * is not a singleton resolves the target of each call this way and takes the non-singleton interceptors of the
     * call from the dependents of that registration, which is where the interceptors created with the target live.</p>
     *
     * @param definition The proxy target bean definition
     * @param beanType   The bean type
     * @param qualifier  The qualifier
     * @param <T>        The generic type
     * @return The registration of the proxy target
     * @since 5.3.0
     */
    @UsedByGeneratedCode
    default <T> BeanRegistration<T> getProxyTargetBeanRegistration(BeanDefinition<T> definition,
                                                                  Argument<T> beanType,
                                                                  @Nullable Qualifier<T> qualifier) {
        return BeanRegistration.of(
            getContext(),
            new DefaultBeanContext.BeanKey<>(beanType, qualifier),
            definition,
            getProxyTargetBean(definition, beanType, qualifier)
        );
    }

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
        @Nullable
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
