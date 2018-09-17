/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * <p>An application context extends a {@link BeanContext} and adds the concepts of configuration, environments and
 *   runtimes.</p>
 * <p>
 * <p>The {@link ApplicationContext} is the main entry point for starting and running Micronaut applications. It
 * can be though of as a container object for all dependency injected objects.</p>
 * <p>
 * <p>The {@link ApplicationContext} can be started via the {@link #run()} method. For example:</p>
 *
 * <pre class="code">
 *     ApplicationContext context = ApplicationContext.run();
 * </pre>
 *
 * <p>Alternatively, the {@link #build()} method can be used to customize the {@code ApplicationContext} using the {@link ApplicationContextBuilder} interface
 * prior to running. For example:</p>
 * <pre class="code">
 *     ApplicationContext context = ApplicationContext.build().environments("test").start();
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
     * @return The default conversion service
     */
    ConversionService<?> getConversionService();

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
    <T> ApplicationContext registerSingleton(Class<T> type, T singleton, Qualifier<T> qualifier, boolean inject);

    @Override
    default <T> ApplicationContext registerSingleton(Class<T> type, T singleton, Qualifier<T> qualifier) {
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
        consumer.accept(getEnvironment());
        return this;
    }

    @Override
    default ApplicationContext registerSingleton(Object singleton) {
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
        return build(environments).start();
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
        return build(environments)
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
    static <T extends AutoCloseable> T run(Class<T> type, PropertySource propertySource, String... environments) {
        T bean = build(environments)
            .mainClass(type)
            .propertySources(propertySource)
            .start()
            .getBean(type);
        if (bean != null) {
            if (bean instanceof LifeCycle) {
                LifeCycle lifeCycle = (LifeCycle) bean;
                if (!lifeCycle.isRunning()) {
                    lifeCycle.start();
                }
            }
        }

        return bean;
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param environments The environments to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContextBuilder build(String... environments) {
        return new DefaultApplicationContextBuilder()
            .environments(environments);
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param properties   The properties
     * @param environments The environments to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContextBuilder build(Map<String, Object> properties, String... environments) {
        return new DefaultApplicationContextBuilder()
            .properties(properties)
            .environments(environments);
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContextBuilder build() {
        return new DefaultApplicationContextBuilder();
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}
     *
     * @param classLoader  The classloader to use
     * @param environments The environments to use
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(ClassLoader classLoader, String... environments) {
        return build(classLoader, environments).start();
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param classLoader  The classloader to use
     * @param environments The environment to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContextBuilder build(ClassLoader classLoader, String... environments) {
        return build(environments)
            .classLoader(classLoader);
    }

    /**
     * Build a {@link ApplicationContext}.
     *
     * @param mainClass    The main class of the application
     * @param environments The environment to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContextBuilder build(Class mainClass, String... environments) {
        return build(environments)
            .mainClass(mainClass);
    }
}
