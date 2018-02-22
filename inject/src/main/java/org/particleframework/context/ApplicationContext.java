/*
 * Copyright 2017 original authors
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
package org.particleframework.context;

import org.particleframework.context.env.*;
import org.particleframework.core.io.ResourceLoader;
import org.particleframework.core.util.StringUtils;
import org.particleframework.core.value.PropertyResolver;
import org.particleframework.core.convert.ConversionService;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An application context extends a {@link BeanContext} and adds the concepts of configuration, environments and runtimes.
 *
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
     * Starts the application context
     *
     * @return The application context
     */
    @Override
    ApplicationContext start();
    /**
     * Stops the application context
     *
     * @return The application context
     */
    @Override
    ApplicationContext stop();

    @Override
    <T> ApplicationContext registerSingleton(Class<T> type, T singleton, Qualifier<T> qualifier);

    @Override
    <T> ApplicationContext registerSingleton(Class<T> type, T singleton);

    /**
     * Allow configuration the {@link Environment}
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
     * Run the {@link ApplicationContext}. This method will instantiate a new {@link ApplicationContext} and call {@link #start()}
     *
     * @param environments The environments to use
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(String... environments) {
        return build(environments).start();
    }

    /**
     * Run the {@link ApplicationContext}. This method will instantiate a new {@link ApplicationContext} and call {@link #start()}
     *
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run() {
        return run(StringUtils.EMPTY_STRING_ARRAY);
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method should not be used
     * if the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for shutting down the context
     *
     * @param properties Additional properties
     * @param environments The environment names
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(Map<String, Object> properties, String... environments) {
        PropertySource propertySource = PropertySource.of(PropertySource.CONTEXT, properties, SystemPropertiesPropertySource.POSITION + 100);
        return run(propertySource, environments);
    }


    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method should not be used
     * if the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for shutting down the context
     *
     * @param properties Additional properties
     * @param environments The environment names
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(PropertySource properties, String... environments) {
        return build(environments)
                .environment(env -> env.addPropertySource(properties))
                .start();
    }
    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method should not be used
     * if the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for shutting down the context
     *
     * @param type The type of the bean to run
     * @param environments The environments to use
     * @return The running bean
     */
    static <T> T run(Class<T> type, String... environments) {
        return run(type, Collections.emptyMap(), environments);
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method should not be used
     * if the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for shutting down the context
     *
     * @param type The type of the bean to run
     * @param properties Additional properties
     * @param environments The environment names
     * @return The running bean
     */
    static <T> T run(Class<T> type, Map<String, Object> properties, String... environments) {
        PropertySource propertySource = PropertySource.of(PropertySource.CONTEXT, properties, SystemPropertiesPropertySource.POSITION + 100);
        return run(type, propertySource, environments);
    }


    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method should not be used
     * if the {@link ApplicationContext} requires graceful shutdown unless the returned bean takes responsibility for shutting down the context
     *
     * @param type The environment to use
     * @param propertySource Additional properties
     * @param environments The environment names
     * @return The running {@link BeanContext}
     */
    static <T> T run(Class<T> type, PropertySource propertySource, String... environments) {
        T bean = build(type.getClassLoader(), environments)
                .environment(env -> env.addPropertySource(propertySource)
                                       .addPackage(type.getPackage()))
                .start()
                .getBean(type);
        if(bean instanceof LifeCycle) {
            LifeCycle lifeCycle = (LifeCycle) bean;
            if(!lifeCycle.isRunning()) {
                lifeCycle.start();
            }
        }
        return bean;
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @param environments The environments to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build(String... environments) {
        if(environments == null) environments = StringUtils.EMPTY_STRING_ARRAY;
        return new DefaultApplicationContext(environments);
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build() {
        return new DefaultApplicationContext();
    }
    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}
     *
     * @param classLoader The classloader to use
     * @param environments The environments to use
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(ClassLoader classLoader,String... environments) {
        return build(classLoader, environments).start();
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @param classLoader The classloader to use
     * @param environments The environment to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build(ClassLoader classLoader, String... environments) {
        return new DefaultApplicationContext(ResourceLoader.of(classLoader), environments);
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @param mainClass The main class of the application
     * @param environments The environment to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build(Class mainClass, String... environments) {
        DefaultApplicationContext applicationContext = new DefaultApplicationContext(ResourceLoader.of(mainClass.getClassLoader()), environments);
        applicationContext.getEnvironment().addPackage(mainClass.getPackage());
        return applicationContext;
    }
}
