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

import io.micronaut.context.env.PropertySource;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * An interface for building an application context.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ApplicationContextBuilder {

    /**
     * Additional singletons to register prior to startup.
     *
     * @param beans The beans
     * @return This builder
     */
    ApplicationContextBuilder singletons(Object... beans);

    /**
     * The environments to use.
     *
     * @param environments The environments
     * @return This builder
     */
    ApplicationContextBuilder environments(@Nullable String... environments);

    /**
     * The packages to include for package scanning.
     *
     * @param packages The packages
     * @return This builder
     */
    ApplicationContextBuilder packages(@Nullable String... packages);

    /**
     * Properties to override from the environment.
     *
     * @param properties The properties
     * @return This builder
     */
    ApplicationContextBuilder properties(@Nullable Map<String, Object> properties);


    /**
     * Additional property sources.
     *
     * @param propertySources The property sources to include
     * @return This builder
     */
    ApplicationContextBuilder propertySources(@Nullable PropertySource... propertySources);

    /**
     * The main class used by this application.
     *
     * @param mainClass The main class
     * @return This builder
     */
    ApplicationContextBuilder mainClass(Class mainClass);

    /**
     * The class loader to be used.
     *
     * @param classLoader The classloader
     * @return This builder
     */
    ApplicationContextBuilder classLoader(ClassLoader classLoader);

    /**
     * Builds the {@link ApplicationContext}, but does not start it.
     *
     * @return The built, but not running {@link ApplicationContext}
     */
    ApplicationContext build();



    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to include
     * @return This application
     */
    ApplicationContextBuilder include(@Nullable String... configurations);

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to exclude
     * @return This application
     */
    ApplicationContextBuilder exclude(@Nullable String... configurations);

    /**
     * Starts the {@link ApplicationContext}.
     *
     * @return The running {@link ApplicationContext}
     */
    default ApplicationContext start() {
        return build().start();
    }


    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type.
     *
     * @param type         The type of the bean to run
     * @param <T>          The type, a subclass of {@link ApplicationContextLifeCycle}
     * @return The running bean
     */
    default <T extends AutoCloseable> T run(Class<T> type) {
        ApplicationContext applicationContext = start();
        T bean = applicationContext.getBean(type);
        if (bean instanceof LifeCycle) {
            LifeCycle lifeCycle = (LifeCycle) bean;
            if (!lifeCycle.isRunning()) {
                lifeCycle.start();
            }
        }
        return bean;
    }
}
