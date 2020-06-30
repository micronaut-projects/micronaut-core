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

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * An interface for building an application context.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ApplicationContextBuilder {

    /**
     * Whether to eager initialize {@link io.micronaut.context.annotation.ConfigurationProperties} beans.
     * @param eagerInitConfiguration True if configuration properties should be eagerly initialized
     * @return The context builder
     * @since 2.0
     */
    default @NonNull ApplicationContextBuilder eagerInitConfiguration(boolean eagerInitConfiguration) {
        if (eagerInitConfiguration) {
            return eagerInitAnnotated(ConfigurationReader.class);
        }
        return this;
    }

    /**
     * Whether to eager initialize singleton beans.
     * @param eagerInitSingletons True if singletons should be eagerly initialized
     * @return The context builder
     * @since 2.0
     */
    default @NonNull ApplicationContextBuilder eagerInitSingletons(boolean eagerInitSingletons) {
        if (eagerInitSingletons) {
            return eagerInitAnnotated(Singleton.class);
        }
        return this;
    }

    /**
     * Specifies to eager init the given annotated types.
     *
     * @param annotations The annotation stereotypes
     * @return The context builder
     * @since 2.0
     */
    @NonNull ApplicationContextBuilder eagerInitAnnotated(Class<? extends Annotation>... annotations);

    /**
     * Override default config locations
     *
     * @param configLocations The config locations
     * @return This environment
     * @since 2.0
     */
    @NonNull ApplicationContextBuilder overrideConfigLocations(String... configLocations);

    /**
     * Additional singletons to register prior to startup.
     *
     * @param beans The beans
     * @return This builder
     */
    @NonNull ApplicationContextBuilder singletons(@Nullable Object... beans);

    /**
     * Whether to deduce environments.
     *
     * @param deduceEnvironment The boolean
     * @return This builder
     */
    @NonNull ApplicationContextBuilder deduceEnvironment(@Nullable Boolean deduceEnvironment);

    /**
     * The environments to use.
     *
     * @param environments The environments
     * @return This builder
     */
    @NonNull ApplicationContextBuilder environments(@Nullable String... environments);

    /**
     * The packages to include for package scanning.
     *
     * @param packages The packages
     * @return This builder
     */
    @NonNull ApplicationContextBuilder packages(@Nullable String... packages);

    /**
     * Properties to override from the environment.
     *
     * @param properties The properties
     * @return This builder
     */
    @NonNull ApplicationContextBuilder properties(@Nullable Map<String, Object> properties);

    /**
     * Additional property sources.
     *
     * @param propertySources The property sources to include
     * @return This builder
     */
    @NonNull ApplicationContextBuilder propertySources(@Nullable PropertySource... propertySources);

    /**
     * Set whether environment variables should contribute to configuration.
     *
     * @param environmentPropertySource The boolean
     * @return This builder
     */
    @NonNull ApplicationContextBuilder environmentPropertySource(boolean environmentPropertySource);

    /**
     * Which environment variables should contribute to configuration.
     *
     * @param environmentVariables The environment variables
     * @return This builder
     */
    @NonNull ApplicationContextBuilder environmentVariableIncludes(@Nullable String... environmentVariables);

    /**
     * Which environment variables should not contribute to configuration.
     *
     * @param environmentVariables The environment variables
     * @return This builder
     */
    @NonNull ApplicationContextBuilder environmentVariableExcludes(@Nullable String... environmentVariables);

    /**
     * The main class used by this application.
     *
     * @param mainClass The main class
     * @return This builder
     */
    @NonNull ApplicationContextBuilder mainClass(@Nullable Class mainClass);

    /**
     * The class loader to be used.
     *
     * @param classLoader The classloader
     * @return This builder
     */
    @NonNull ApplicationContextBuilder classLoader(@Nullable ClassLoader classLoader);

    /**
     * Builds the {@link ApplicationContext}, but does not start it.
     *
     * @return The built, but not running {@link ApplicationContext}
     */
    @NonNull ApplicationContext build();

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to include
     * @return This application
     */
    @NonNull ApplicationContextBuilder include(@Nullable String... configurations);

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to exclude
     * @return This application
     */
    @NonNull ApplicationContextBuilder exclude(@Nullable String... configurations);

    /**
     * Set the command line arguments.
     *
     * @param args The arguments
     * @return This application
     */
    default  @NonNull ApplicationContextBuilder args(@Nullable String... args) {
        return this;
    }

    /**
     * Starts the {@link ApplicationContext}.
     *
     * @return The running {@link ApplicationContext}
     */
    default @NonNull ApplicationContext start() {
        return build().start();
    }


    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type.
     *
     * @param type         The type of the bean to run
     * @param <T>          The type, a subclass of {@link AutoCloseable}. The close method of the implementation should shutdown the context.
     * @return The running bean
     */
    default @NonNull <T extends AutoCloseable> T run(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
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
