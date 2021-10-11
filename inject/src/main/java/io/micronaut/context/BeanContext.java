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
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.attr.MutableAttributeHolder;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.validation.BeanDefinitionValidator;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

/**
 * <p>The core BeanContext abstraction which allows for dependency injection of classes annotated with
 * {@link javax.inject.Inject}.</p>
 * <p>
 * <p>Apart of the standard {@code javax.inject} annotations for dependency injection, additional annotations within
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
        MutableAttributeHolder {

    /**
     * Obtains the configuration for this context.
     * @return The {@link io.micronaut.context.BeanContextConfiguration}
     * @since 3.0.0
     */
    @NonNull BeanContextConfiguration getContextConfiguration();

    /**
     * Obtain an {@link io.micronaut.context.event.ApplicationEventPublisher} for the given even type.
     * @param eventType The event type
     * @param <E> The event generic type
     * @return The event publisher, never {@code null}
     */
    @SuppressWarnings("unchecked")
    default @NonNull <E> ApplicationEventPublisher<E> getEventPublisher(@NonNull Class<E> eventType) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        return getBean(Argument.of(ApplicationEventPublisher.class, eventType));
    }

    /**
     * Publish the given event. The event will be published synchronously and only return once all listeners have consumed the event.
     *
     * @deprecated Preferred way is to use event typed {@code ApplicationEventPublisher<MyEventType>}
     * @param event The event to publish
     */
    @Override
    @Deprecated
    void publishEvent(Object event);

    /**
     * Publish the given event. The event will be published asynchronously. A future is returned that can be used to check whether the event completed successfully or not.
     *
     * @deprecated Preferred way is to use event typed {@code ApplicationEventPublisher<MyEventType>}
     * @param event The event to publish
     * @return A future that completes when the event is published
     */
    @Override
    @Deprecated
    default Future<Void> publishEventAsync(Object event) {
        return ApplicationEventPublisher.super.publishEventAsync(event);
    }

    /**
     * Inject an existing instance.
     *
     * @param instance The instance to inject
     * @param <T>      The bean generic type
     * @return The instance to inject
     */
    @NonNull
    <T> T inject(@NonNull T instance);

    /**
     * Creates a new instance of the given bean performing dependency injection and returning a new instance.
     * <p>
     * Note that the instance returned is not saved as a singleton in the context.
     *
     * @param beanType The bean type
     * @param <T>      The bean generic type
     * @return The instance
     */
    default @NonNull
    <T> T createBean(@NonNull Class<T> beanType) {
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
    @NonNull
    <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     * <p>
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed
     * in the {@code argumentValues} parameter will be used</p>
     * <p>
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType       The bean type
     * @param qualifier      The qualifier
     * @param argumentValues The argument values
     * @param <T>            The bean generic type
     * @return The instance
     */
    @NonNull
    <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     * <p>
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code argumentValues} parameter will be used</p>
     * <p>
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param args      The argument values
     * @param <T>       The bean generic type
     * @return The instance
     */
    @NonNull
    <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     * <p>
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code argumentValues} parameter will be used</p>
     * <p>
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType The bean type
     * @param args     The argument values
     * @param <T>      The bean generic type
     * @return The instance
     */
    @NonNull
    default <T> T createBean(@NonNull Class<T> beanType, @Nullable Object... args) {
        return createBean(beanType, null, args);
    }

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     * <p>
     * <p>If the bean defines any {@link io.micronaut.context.annotation.Parameter} values then the values passed in
     * the {@code argumentValues} parameter will be used</p>
     * <p>
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType       The bean type
     * @param argumentValues The argument values
     * @param <T>            The bean generic type
     * @return The instance
     */
    @NonNull
    default <T> T createBean(@NonNull Class<T> beanType, @Nullable Map<String, Object> argumentValues) {
        return createBean(beanType, null, argumentValues);
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
    <T> T destroyBean(@NonNull Class<T> beanType);

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
    default <T> T destroyBean(@NonNull Argument<T> beanType) {
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
    <T> T destroyBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Destroys the given bean.
     *
     * @param bean The bean
     * @param <T>  The concrete class
     * @return The destroy instance
     * @since 3.0.0
     */
    @NonNull
    <T> T destroyBean(@NonNull T bean);

    /**
     * <p>Refresh the state of the given registered bean applying dependency injection and configuration wiring again.</p>
     * <p>
     * <p>Note that if the bean was produced by a {@link io.micronaut.context.annotation.Factory} then this method will
     * refresh the factory too</p>
     *
     * @param identifier The {@link BeanIdentifier}
     * @param <T>        The concrete class
     * @return An {@link Optional} of the instance if it exists for the given registration
     */
    @NonNull
    <T> Optional<T> refreshBean(@Nullable BeanIdentifier identifier);

    /**
     * @return The class loader used by this context
     */
    @NonNull
    ClassLoader getClassLoader();

    /**
     * @return Get the configured bean validator, if any.
     */
    @NonNull
    BeanDefinitionValidator getBeanValidator();

    @Override
    @NonNull
    <T> BeanContext registerSingleton(@NonNull Class<T> type, @NonNull T singleton, @Nullable Qualifier<T> qualifier, boolean inject);

    @Override
    default BeanContext registerSingleton(@NonNull Object singleton) {
        Objects.requireNonNull(singleton, "Argument [singleton] must not be null");
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    @Override
    default <T> BeanContext registerSingleton(Class<T> type, T singleton, Qualifier<T> qualifier) {
        return registerSingleton(type, singleton, qualifier, true);
    }

    @Override
    default <T> BeanContext registerSingleton(Class<T> type, T singleton) {
        return registerSingleton(type, singleton, null, true);
    }

    @NonNull
    @Override
    default BeanContext registerSingleton(@NonNull Object singleton, boolean inject) {
        return (BeanContext) BeanDefinitionRegistry.super.registerSingleton(singleton, inject);
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}.
     *
     * @return The running {@link BeanContext}
     */
    static @NonNull
    BeanContext run() {
        return build().start();
    }

    /**
     * Build a {@link BeanContext}.
     *
     * @return The built, but not yet running {@link BeanContext}
     */
    static @NonNull
    BeanContext build() {
        return new DefaultBeanContext();
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}.
     *
     * @param classLoader The classloader to use
     * @return The running {@link BeanContext}
     */
    static @NonNull
    BeanContext run(ClassLoader classLoader) {
        return build(classLoader).start();
    }

    /**
     * Build a {@link BeanContext}.
     *
     * @param classLoader The classloader to use
     * @return The built, but not yet running {@link BeanContext}
     */
    static @NonNull
    BeanContext build(ClassLoader classLoader) {
        return new DefaultBeanContext(classLoader);
    }
}
