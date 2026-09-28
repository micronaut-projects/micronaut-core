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

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.attr.MutableAttributeHolder;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.validation.BeanDefinitionValidator;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <p>The core BeanContext abstraction which allows for dependency injection of classes annotated with
 * {@link jakarta.inject.Inject}.</p>
 *
 * <p>Apart of the standard {@code jakarta.inject} annotations for dependency injection, additional annotations within
 * the {@code io.micronaut.context.annotation} package allow control over configuration of the bean context.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanContext extends
        LifeCycle<BeanContext>,
        ExecutionHandleLocator,
        BeanLocator,
        BeanDefinitionRegistry,
        ApplicationEventPublisher<Object>,
        AnnotationMetadataResolver,
        MutableAttributeHolder,
    ConversionServiceProvider {

    /**
     * Obtains the configuration for this context.
     * @return The {@link io.micronaut.context.BeanContextConfiguration}
     * @since 3.0.0
     */
    BeanContextConfiguration getContextConfiguration();

    /**
     * Creates a fresh instance of the exact definition, bypassing its scope, and retains its complete
     * dependency tree in the returned registration. Dependencies still obey their own scope rules.
     * For a contextual target behind a proxy, supply the target definition. The caller owns the result.
     * @param definition The definition to instantiate
     * @param <T> The bean type
     * @return The created instance and its lifecycle
     * @since 5.3.0
     */
    @Experimental
    default <T> CreatedBean<T> createBeanRegistration(BeanDefinition<T> definition) {
        throw new UnsupportedOperationException("Fresh registrations are not supported by this context");
    }

    /**
     * Creates an independent dependency group. The caller must close it; context shutdown does not take ownership.
     * New lookups are rejected during shutdown. A destruction listener that needs temporary dependencies uses
     * {@link io.micronaut.context.event.BeanPreDestroyEvent#withDependencies(Function)}.
     * @return The group
     * @since 5.3.0
     */
    @Experimental
    default BeanDependencyGroup createDependencyGroup() {
        throw new UnsupportedOperationException("Dependency groups are not supported by this context");
    }

    /**
     * Resolves dependencies for a synchronous invocation and always releases them afterwards. Cleanup failures
     * are suppressed on an invocation failure. New groups and lookups are rejected once shutdown begins;
     * a destruction listener uses {@link io.micronaut.context.event.BeanPreDestroyEvent#withDependencies(Function)}.
     * @param action The invocation
     * @param <R> The result type
     * @return The result (which must not retain an owned dependency)
     * @since 5.3.0
     */
    @Experimental
    default <R> R withDependencies(Function<BeanDependencyGroup, R> action) {
        try (BeanDependencyGroup group = createDependencyGroup()) {
            return action.apply(group);
        }
    }

    /**
     * The predicate the context was built with, as passed to
     * {@link ApplicationContextBuilder#beansPredicate(java.util.function.Predicate)}.
     *
     * <p>The compiled definitions the context knows about are already narrowed by it: the references
     * reported by {@link BeanDefinitionRegistry#getBeanDefinitionReferences()} and the definitions
     * resolved from them are only ever ones the predicate accepted. The accessor is for code that
     * enumerates candidates from a source of its own and has to honour the same narrowing, rather than
     * carrying a second copy of the predicate alongside the one handed to the builder.</p>
     *
     * @return The beans predicate, or {@code null} if the context was not narrowed by one
     * @since 5.2.0
     */
    @Nullable
    default Predicate<QualifiedBeanType<?>> getBeansPredicate() {
        return getContextConfiguration().beansPredicate();
    }

    /**
     * Obtain an {@link io.micronaut.context.event.ApplicationEventPublisher} for the given even type.
     * @param eventType The event type
     * @param <E> The event generic type
     * @return The event publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    default <E> ApplicationEventPublisher<E> getEventPublisher(Class<E> eventType) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        return getBean(Argument.of(ApplicationEventPublisher.class, eventType));
    }

    /**
     * Inject an existing instance.
     *
     * @param instance The instance to inject
     * @param <T>      The bean generic type
     * @return The instance to inject
     */
    <T> T inject(T instance);

    /**
     * Creates a new instance of the given bean performing dependency injection and returning a new instance.
     * <p>
     * Note that the instance returned is not saved as a singleton in the context.
     *
     * @param beanType The bean type
     * @param <T>      The bean generic type
     * @return The instance
     */
    default <T> T createBean(Class<T> beanType) {
        return createBean(beanType, (Qualifier<T>) null);
    }

    /**
     * Creates a new instance of the given bean performing dependency injection and returning a new instance.
     * <p>
     * Note that the instance returned is not saved as a singleton in the context.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean generic type
     * @return The instance
     */
    <T> T createBean(Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     *
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed
     * in the {@code argumentValues} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType       The bean type
     * @param qualifier      The qualifier
     * @param argumentValues The argument values
     * @param <T>            The bean generic type
     * @return The instance
     */
    <T> T createBean(Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     *
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code argumentValues} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param args      The argument values
     * @param <T>       The bean generic type
     * @return The instance
     */
    <T> T createBean(Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     *
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code argumentValues} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType The bean type
     * @param args     The argument values
     * @param <T>      The bean generic type
     * @return The instance
     */
    default <T> T createBean(Class<T> beanType, @Nullable Object... args) {
        return createBean(beanType, null, args);
    }

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     *
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code argumentValues} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType       The bean type
     * @param argumentValues The argument values
     * @param <T>            The bean generic type
     * @return The instance
     */
    default <T> T createBean(Class<T> beanType, @Nullable Map<String, Object> argumentValues) {
        return createBean(beanType, null, argumentValues);
    }

    /**
     * <p>Creates a new instance of the bean of the given definition performing dependency injection and returning a new instance.</p>
     *
     * <p>The definition is not looked up again, so a caller that already holds it, for example from
     * {@link #getBeanDefinitions(Class)}, can create instances repeatedly without resolving the bean type and qualifier
     * on every call. The instance is otherwise created as by {@link #createBean(Class, Qualifier, Object...)}, including
     * the {@link io.micronaut.context.event.BeanCreatedEventListener} callbacks.</p>
     *
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code args} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param definition The bean definition, which must be one of this context
     * @param args       The argument values
     * @param <T>        The bean generic type
     * @return The instance
     * @since 5.3.0
     */
    default <T> T createBean(BeanDefinition<T> definition, @Nullable Object... args) {
        return createBean(definition.getBeanType(), definition.getDeclaredQualifier(), args);
    }

    /**
     * Destroys the bean for the given type causing it to be re-created. If a singleton has been loaded it will be
     * destroyed and removed from the context, otherwise null will be returned.
     *
     * @param beanType The bean type
     * @param <T>      The concrete class
     * @return The destroy instance or null if no such bean exists
     */
    @Nullable
    <T> T destroyBean(Class<T> beanType);

    /**
     * Destroys the bean for the given type causing it to be re-created. If a singleton has been loaded it will be
     * destroyed and removed from the context, otherwise null will be returned.
     *
     * @param beanType The bean type
     * @param <T>      The concrete class
     * @return The destroy instance or null if no such bean exists
     * @since 3.0.0
     */
    @Nullable
    default <T> T destroyBean(Argument<T> beanType) {
        return destroyBean(beanType, null);
    }

    /**
     * Destroys the bean for the given type causing it to be re-created. If a singleton has been loaded it will be
     * destroyed and removed from the context, otherwise null will be returned.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The concrete class
     * @return The destroy instance or null if no such bean exists
     * @since 3.0.0
     */
    @Nullable
    <T> T destroyBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Destroys the given bean.
     *
     * @param bean The bean
     * @param <T>  The concrete class
     * @return The destroy instance
     * @since 3.0.0
     */
    <T> T destroyBean(T bean);

    /**
     * Destroys the given bean.
     *
     * @param beanRegistration The bean registration
     * @param <T>  The bean type
     * @since 3.5.0
     */
    <T> void destroyBean(BeanRegistration<T> beanRegistration);

    /**
     * Destroys the given bean as a dependent, the way the context destroys the dependents of a bean when it destroys
     * that bean.
     *
     * <p>This is for a bean that was resolved on behalf of something else, whose dependent it is, such as one of the
     * registrations {@link BeanResolutionContext#getAndResetDependentBeans()} reports. It differs from
     * {@link #destroyBean(BeanRegistration)} in what it leaves alone:</p>
     *
     * <ul>
     *     <li>A proxy whose target lives in a custom scope, such as a {@code @ScopedProxy}, is destroyed together
     *     with its own dependents, and its target stays in the scope: the target belongs to the scope, which may
     *     have handed it to other beans as well. {@link #destroyBean(BeanRegistration)} removes the target from
     *     the scope.</li>
     *     <li>A proxy that is a singleton is not destroyed: a singleton is not the dependent of one bean.</li>
     *     <li>The context does not call {@link LifeCycle#stop()} on a bean that implements {@link LifeCycle},
     *     which {@link #destroyBean(BeanRegistration)} does once the bean's pre-destroy has run: stopping is for a
     *     bean destroyed in its own right, not for one destroyed because whatever it was resolved for is gone. The
     *     pre-destroy of the bean's definition runs either way, and stops the bean if the definition does.</li>
     * </ul>
     *
     * <p>The bean's own dependents are destroyed as dependents in either case. A registration that was already
     * closed, or destroyed through this method, is not destroyed again.</p>
     *
     * <p>Destroying a bean as a dependent needs a context that tracks what a bean owns. The default throws an
     * {@link UnsupportedOperationException} rather than destroy the bean in its own right, which would take a scoped
     * target out of its scope. {@link DefaultBeanContext} implements it.</p>
     *
     * @param registration The registration of the dependent bean
     * @param <T>          The bean type
     * @throws UnsupportedOperationException if the context cannot destroy a bean as a dependent
     * @since 5.3.0
     */
    default <T> void destroyDependentBean(BeanRegistration<T> registration) {
        throw new UnsupportedOperationException("This implementation of BeanContext doesn't support destroying a bean as a dependent");
    }

    /**
     * <p>Refresh the state of the given registered bean applying dependency injection and configuration wiring again.</p>
     *
     * <p>Note that if the bean was produced by a {@link io.micronaut.context.annotation.Factory} then this method will
     * refresh the factory too</p>
     *
     * @param identifier The {@link BeanIdentifier}
     * @param <T>        The concrete class
     * @return An {@link Optional} of the instance if it exists for the given registration
     */
    <T> Optional<T> refreshBean(@Nullable BeanIdentifier identifier);

    /**
     * <p>Refresh the state of the given registered bean applying dependency injection and configuration wiring again.</p>
     *
     * <p>Note that if the bean was produced by a {@link io.micronaut.context.annotation.Factory} then this method will
     * refresh the factory too</p>
     *
     * This methods skips an additional resolution of the {@link BeanRegistration}.
     *
     * @param beanRegistration The {@link BeanRegistration}
     * @param <T>              The concrete class
     * @since 3.5.0
     */
    <T> void refreshBean(BeanRegistration<T> beanRegistration);

    /**
     * @return The class loader used by this context
     */
    ClassLoader getClassLoader();

    /**
     * @return Get the configured bean validator, if any.
     */
    BeanDefinitionValidator getBeanValidator();

    @Override
    <T> BeanContext registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier, boolean inject);

    @Override
    default BeanContext registerSingleton(Object singleton) {
        Objects.requireNonNull(singleton, "Argument [singleton] must not be null");
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    @Override
    default <T> BeanContext registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier) {
        return registerSingleton(type, singleton, qualifier, true);
    }

    @Override
    default <T> BeanContext registerSingleton(Class<T> type, T singleton) {
        return registerSingleton(type, singleton, null, true);
    }

    @Override
    default BeanContext registerSingleton(Object singleton, boolean inject) {
        return (BeanContext) BeanDefinitionRegistry.super.registerSingleton(singleton, inject);
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}.
     *
     * @return The running {@link BeanContext}
     */
    static BeanContext run() {
        return build().start();
    }

    /**
     * Build a {@link BeanContext}.
     *
     * @return The built, but not yet running {@link BeanContext}
     */
    static BeanContext build() {
        return new DefaultBeanContext();
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}.
     *
     * @param classLoader The classloader to use
     * @return The running {@link BeanContext}
     */
    static BeanContext run(ClassLoader classLoader) {
        return build(classLoader).start();
    }

    /**
     * Build a {@link BeanContext}.
     *
     * @param classLoader The classloader to use
     * @return The built, but not yet running {@link BeanContext}
     */
    static BeanContext build(ClassLoader classLoader) {
        return new DefaultBeanContext(classLoader);
    }

    /**
     * The graph of which bean received which, recorded when the context was configured to
     * {@link BeanContextConfiguration#isTrackBeanDependencies() track dependencies}.
     *
     * @return The graph, or empty when the context does not record it
     * @since 5.3.0
     */
    default Optional<BeanDependencyGraph> findDependencyGraph() {
        return Optional.empty();
    }
}
