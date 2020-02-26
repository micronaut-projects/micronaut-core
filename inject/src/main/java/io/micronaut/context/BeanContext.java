/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.validation.BeanDefinitionValidator;
import org.jetbrains.annotations.NotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    ApplicationEventPublisher,
    AnnotationMetadataResolver,
    MutableAttributeHolder {

    /**
     * Inject an existing instance.
     *
     * @param instance The instance to inject
     * @param <T>      The bean generic type
     * @return The instance to inject
     */
    @NonNull <T> T inject(@NonNull T instance);

    /**
     * Creates a new instance of the given bean performing dependency injection and returning a new instance.
     * <p>
     * Note that the instance returned is not saved as a singleton in the context.
     *
     * @param beanType The bean type
     * @param <T>      The bean generic type
     * @return The instance
     */
    default @NonNull <T> T createBean(@NonNull Class<T> beanType) {
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
    @NonNull <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

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
    @NonNull <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues);

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
    @NonNull <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args);

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
    @NonNull default <T> T createBean(@NonNull Class<T> beanType, @Nullable Object... args) {
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
    @NonNull default <T> T createBean(@NonNull Class<T> beanType, @Nullable Map<String, Object> argumentValues) {
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
    @Nullable <T> T destroyBean(@NonNull Class<T> beanType);

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
    @NonNull <T> Optional<T> refreshBean(@Nullable BeanIdentifier identifier);

    /**
     * @return The class loader used by this context
     */
    @NonNull ClassLoader getClassLoader();

    /**
     * @return Get the configured bean validator, if any.
     */
    @NonNull BeanDefinitionValidator getBeanValidator();

    @Override
    @NonNull <T> BeanContext registerSingleton(@NonNull Class<T> type, @NonNull T singleton, @Nullable Qualifier<T> qualifier, boolean inject);

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

    @NotNull
    @Override
    default BeanContext registerSingleton(@NotNull Object singleton, boolean inject) {
        return (BeanContext) BeanDefinitionRegistry.super.registerSingleton(singleton, inject);
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}.
     *
     * @return The running {@link BeanContext}
     */
    static @NonNull BeanContext run() {
        return build().start();
    }

    /**
     * Build a {@link BeanContext}.
     *
     * @return The built, but not yet running {@link BeanContext}
     */
    static @NonNull BeanContext build() {
        return new DefaultBeanContext();
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}.
     *
     * @param classLoader The classloader to use
     * @return The running {@link BeanContext}
     */
    static @NonNull BeanContext run(ClassLoader classLoader) {
        return build(classLoader).start();
    }

    /**
     * Build a {@link BeanContext}.
     *
     * @param classLoader The classloader to use
     * @return The built, but not yet running {@link BeanContext}
     */
    static @NonNull BeanContext build(ClassLoader classLoader) {
        return new DefaultBeanContext(classLoader);
    }
}
