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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * <p>An application context extends a {@link BeanContext} and adds the concepts of configuration, environments and
 *   runtimes.</p>
 *
 * <p>The {@link ApplicationContext} is the main entry point for starting and running Micronaut applications. It
 * can be thought of as a container object for all dependency injected objects.</p>
 *
 * <p>The {@link ApplicationContext} can be started via the {@link #run()} method. For example:</p>
 *
 * <pre class="code">
 *     ApplicationContext context = ApplicationContext.run();
 * </pre>
 *
 * <p>Alternatively, the {@link #builder()} method can be used to customize the {@code ApplicationContext} using the {@link ApplicationContextBuilder} interface
 * prior to running. For example:</p>
 * <pre class="code">
 *     ApplicationContext context = ApplicationContext.builder().environments("test").start();
 * </pre>
 *
 * <p>The {@link #getEnvironment()} method can be used to obtain a reference to the application {@link Environment}, which contains the loaded configuration
 * and active environment names.</p>
 *
 * @see ApplicationContextBuilder
 * @see Environment
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ApplicationContext extends BeanContext, PropertyResolver, PropertyPlaceholderResolver {

    /**
     * @return The application environment
     */
    Environment getEnvironment();

    /**
     * Starts the application context.
     *
     * @return The application context
     */
    @Override
    ApplicationContext start();

    /**
     * Stops the application context.
     *
     * @return The application context
     */
    @Override
    ApplicationContext stop();

    @Override
 <T> ApplicationContext registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier, boolean inject);

    @Override
    default <T> ApplicationContext registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier) {
        return registerSingleton(type, singleton, qualifier, true);
    }

    @Override
    default <T> ApplicationContext registerSingleton(Class<T> type, T singleton) {
        return registerSingleton(type, singleton, null, true);
    }

    @Override
    default ApplicationContext registerSingleton(Object singleton, boolean inject) {
        return (ApplicationContext) BeanContext.super.registerSingleton(singleton, inject);
    }

    /**
     * Allow configuration the {@link Environment}.
     *
     * @param consumer The consumer
     * @return This context
     */
    default ApplicationContext environment(Consumer<Environment> consumer) {
        ArgumentUtils.requireNonNull("consumer", consumer);
        consumer.accept(getEnvironment());
        return this;
    }

    @Override
    default ApplicationContext registerSingleton(Object singleton) {
        ArgumentUtils.requireNonNull("singleton", singleton);
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    /**
     * Run the {@link ApplicationContext}. This method will instantiate a new {@link ApplicationContext} and
     * call {@link #start()}.
     *
     * @param environments The environments to use
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        return builder(environments).start();
    }

    /**
     * Run the {@link ApplicationContext}. This method will instantiate a new {@link ApplicationContext} and
     * call {@link #start()}.
     *
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run() {
        return run(StringUtils.EMPTY_STRING_ARRAY);
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method
     * should not be used.
     * If the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for
     * shutting down the context.
     *
     * @param properties   Additional properties
     * @param environments The environment names
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(Map<String, Object> properties, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("properties", properties);
        PropertySource propertySource = PropertySource.of(PropertySource.CONTEXT, properties, SystemPropertiesPropertySource.POSITION + 100);
        return run(propertySource, environments);
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method
     * should not be used.
     * If the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for
     * shutting down the context.
     *
     * @param properties   Additional properties
     * @param environments The environment names
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(PropertySource properties, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("properties", properties);
        return builder(environments)
            .propertySources(properties)
            .start();
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method
     * should not be used.
     * If the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for
     * shutting down the context.
     *
     * @param type         The type of the bean to run
     * @param environments The environments to use
     * @param <T>          The type
     * @return The running bean
     */
    static <T extends AutoCloseable> T run(Class<T> type, String... environments) {
        ArgumentUtils.requireNonNull("type", type);
        ArgumentUtils.requireNonNull("environments", environments);
        return run(type, Collections.emptyMap(), environments);
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method
     * should not be used.
     * If the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for
     * shutting down the context.
     *
     * @param type         The type of the bean to run
     * @param properties   Additional properties
     * @param environments The environment names
     * @param <T>          The type
     * @return The running bean
     */
    static <T extends AutoCloseable> T run(Class<T> type, Map<String, Object> properties, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("properties", properties);
        ArgumentUtils.requireNonNull("type", type);
        PropertySource propertySource = PropertySource.of(PropertySource.CONTEXT, properties, SystemPropertiesPropertySource.POSITION + 100);
        return run(type, propertySource, environments);
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method
     * should not be used.
     * If the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for
     * shutting down the context.
     *
     * @param type           The environment to use
     * @param propertySource Additional properties
     * @param environments   The environment names
     * @param <T>            The type
     * @return The running {@link BeanContext}
     */
    static <T extends AutoCloseable> T run( Class<T> type,  PropertySource propertySource, String... environments) {
        ArgumentUtils.requireNonNull("propertySource", propertySource);
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("type", type);

        T bean = builder(environments)
            .mainClass(type)
            .propertySources(propertySource)
            .start()
            .getBean(type);
        if (bean instanceof LifeCycle<?> lifeCycle) {
            if (!lifeCycle.isRunning()) {
                lifeCycle.start();
            }
        }

        return bean;
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param environments The environments to use
     * @return The application context builder
     */
    static ApplicationContextBuilder builder(String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        return builder()
                .environments(environments);
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param properties   The properties
     * @param environments The environments to use
     * @return The application context builder
     */
    static ApplicationContextBuilder builder(Map<String, Object> properties, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("properties", properties);
        return builder()
                .properties(properties)
                .environments(environments);
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @return The application context builder
     */
    static ApplicationContextBuilder builder() {
        return new DefaultApplicationContextBuilder();
    }

    /**
     * @param classLoader The class loader to use
     * @return The application context builder
     */
    static ApplicationContextBuilder builder(ClassLoader classLoader) {
        return new DefaultApplicationContextBuilder(classLoader);
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}
     *
     * @param classLoader  The classloader to use
     * @param environments The environments to use
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(ClassLoader classLoader, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("classLoader", classLoader);
        return builder(classLoader, environments).start();
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param classLoader  The classloader to use
     * @param environments The environment to use
     * @return The application context builder
     */
    static ApplicationContextBuilder builder(ClassLoader classLoader, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("classLoader", classLoader);

        return builder(classLoader)
            .environments(environments);
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param mainClass    The main class of the application
     * @param environments The environment to use
     * @return The application context builder
     */
    static ApplicationContextBuilder builder(Class<?> mainClass, String... environments) {
        ArgumentUtils.requireNonNull("environments", environments);
        ArgumentUtils.requireNonNull("mainClass", mainClass);

        return builder(environments)
                .mainClass(mainClass);
    }

    /**
     * Creates the {@link ApplicationContext} using the given {@link Environment}.
     *
     * @return The created {@link ApplicationContext}
     * @since 5.0
     */
    static ApplicationContext create(Environment environment) {
        return new DefaultApplicationContext(new DefaultApplicationContextBuilder(), environment);
    }
}
