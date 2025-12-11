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
import io.micronaut.context.env.PropertySourcesLocator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.BeanConfiguration;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An interface for building an application context.
 *
 * @author graemerocher
 * @since 1.0
 * @see ApplicationContextConfigurer
 * @see ApplicationContext#builder()
 */
@NullMarked
public interface ApplicationContextBuilder {

    /**
     * Sets the trace mode for bean resolution.
     * @param traceMode The debug mode
     * @param classPatterns The patterns
     * @since 4.8.0
     * @see BeanResolutionTraceMode
     * @return This builder
     */
    default ApplicationContextBuilder beanResolutionTrace(
        BeanResolutionTraceMode traceMode,
        String... classPatterns) {
        Objects.requireNonNull(traceMode, "Trace mode cannot be null");
        return beanResolutionTrace(
            new BeanResolutionTraceConfiguration(
                traceMode,
                Set.of(classPatterns),
                null
            )
        );
    }

    /**
     * Sets the trace mode for bean resolution.
     * @param configuration The trace configuration
     * @since 4.8.0
     * @see BeanResolutionTraceMode
     * @return This builder
     */
    default ApplicationContextBuilder beanResolutionTrace(
        BeanResolutionTraceConfiguration configuration) {
        return this;
    }

    /**
     * Whether to eager initialize {@link io.micronaut.context.annotation.ConfigurationProperties} beans.
     * @param eagerInitConfiguration True if configuration properties should be eagerly initialized
     * @return The context builder
     * @since 2.0
     */
    default ApplicationContextBuilder eagerInitConfiguration(boolean eagerInitConfiguration) {
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
    @SuppressWarnings("unchecked")
    default ApplicationContextBuilder eagerInitSingletons(boolean eagerInitSingletons) {
        if (eagerInitSingletons) {
            return eagerInitAnnotated(Singleton.class);
        }
        return this;
    }

    /**
     * Specify whether the default set of property sources should be enabled (default is {@code true}).
     * @param areEnabled Whether the default property sources are enabled
     * @return This builder
     * @since 3.7.0
     */
    default ApplicationContextBuilder enableDefaultPropertySources(boolean areEnabled) {
        return this;
    }

    /**
     * Specifies to eager init the given annotated types.
     *
     * @param annotations The annotation stereotypes
     * @return The context builder
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    ApplicationContextBuilder eagerInitAnnotated(Class<? extends Annotation>... annotations);

    /**
     * Override default config locations.
     *
     * @param configLocations The config locations
     * @return This environment
     * @since 2.0
     */
    ApplicationContextBuilder overrideConfigLocations(String... configLocations);

    /**
     * Additional singletons to register prior to startup.
     *
     * @param beans The beans
     * @return This builder
     */
    ApplicationContextBuilder singletons(@Nullable Object... beans);

    /**
     * Register additional runtime bean definitions prior to startup.
     * @param definitions The definitions.
     * @return The context builder
     * @since 4.5.0
     */
    default ApplicationContextBuilder beanDefinitions(RuntimeBeanDefinition<?>... definitions) {
        return this;
    }

    /**
     * Register additional bean configurations.
     * @param configurations The configurations.
     * @return This builder
     * @since 4.8.0
     */
    default ApplicationContextBuilder beanConfigurations(BeanConfiguration... configurations) {
        return this;
    }

    /**
     * If set to {@code true} (the default is {@code true}) Micronaut will attempt to automatically deduce the environment
     * it is running in using environment variables and/or stack trace inspection.
     *
     * <p>This method differs from {@link #deduceCloudEnvironment(boolean)} which performs extended network and/or disk probes
     * to try and automatically establish the Cloud environment.</p>
     *
     * <p>This behaviour controls the automatic activation of, for example, the {@link io.micronaut.context.env.Environment#TEST} when running tests.</p>
     *
     * @param deduceEnvironment The boolean
     * @return This builder
     */
    ApplicationContextBuilder deduceEnvironment(@Nullable Boolean deduceEnvironment);

    /**
     * If the package should be deduced from the stack trace. (default is {@code true})
     *
     * @param deducePackage The boolean
     * @return This builder
     * @since 5.0
     */
    ApplicationContextBuilder deducePackage(boolean deducePackage);

    /**
     * If set to {@code true} (the default value is {@code false}) Micronaut will attempt to automatically deduce the Cloud environment it is running within.
     *
     * <p>Enabling this should be done with caution since network probes are required to figure out whether the application is
     * running in certain clouds like GCP.</p>
     *
     * @param deduceEnvironment The boolean
     * @return This builder
     * @since 4.0.0
     */
    ApplicationContextBuilder deduceCloudEnvironment(boolean deduceEnvironment);

    /**
     * The environments to use.
     *
     * @param environments The environments
     * @return This builder
     */
    ApplicationContextBuilder environments(@Nullable String... environments);

    /**
     * The environments to use if no other environments are specified.
     *
     * @param environments The environments
     * @return This builder
     */
    ApplicationContextBuilder defaultEnvironments(@Nullable String... environments);

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
     * Set whether environment variables should contribute to configuration.
     *
     * @param environmentPropertySource The boolean
     * @return This builder
     */
    ApplicationContextBuilder environmentPropertySource(boolean environmentPropertySource);

    /**
     * Which environment variables should contribute to configuration.
     *
     * @param environmentVariables The environment variables
     * @return This builder
     */
    ApplicationContextBuilder environmentVariableIncludes(@Nullable String... environmentVariables);

    /**
     * Which environment variables should not contribute to configuration.
     *
     * @param environmentVariables The environment variables
     * @return This builder
     */
    ApplicationContextBuilder environmentVariableExcludes(@Nullable String... environmentVariables);

    /**
     * The main class used by this application.
     *
     * @param mainClass The main class
     * @return This builder
     */
    ApplicationContextBuilder mainClass(@Nullable Class<?> mainClass);

    /**
     * The class loader to be used.
     *
     * @param classLoader The classloader
     * @return This builder
     */
    ApplicationContextBuilder classLoader(@Nullable ClassLoader classLoader);

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
     * Whether the banner is enabled or not.
     *
     * @param isEnabled Whether the banner is enabled or not
     * @return This application
     */
    ApplicationContextBuilder banner(boolean isEnabled);

    /**
     * Whether to error on an empty bean provider. Defaults to {@code false}.
     *
     * @param shouldAllow True if empty {@link jakarta.inject.Provider} instances are allowed
     * @return This application
     * @since 3.0.0
     */
    ApplicationContextBuilder allowEmptyProviders(boolean shouldAllow);

    /**
     * Set the command line arguments.
     *
     * @param args The arguments
     * @return This application
     */
    default ApplicationContextBuilder args(@Nullable String... args) {
        return this;
    }

    /**
     * Sets whether the bootstrap environment should be initialized.
     *
     * @param bootstrapEnv True if it should be initialized. Default true
     * @return This application
     * @since 3.1.0
     */
    default ApplicationContextBuilder bootstrapEnvironment(boolean bootstrapEnv) {
        return this;
    }

    /**
     * Override the default {@link BeanDefinitionsProvider}.
     *
     * @param provider The bean definitions provider to be used.
     * @return This builder instance for method chaining.
     * @since 5.0
     */
    default ApplicationContextBuilder beanDefinitionsProvider(BeanDefinitionsProvider provider) {
        return this;
    }

    /**
     * Disable eager beans functionality.
     *
     * @param enabled True to enable eager beans; false to disable
     * @return This builder
     * @since 5.0
     */
    ApplicationContextBuilder eagerBeansEnabled(boolean enabled);

    /**
     * Enable or disable application events publishing.
     *
     * @param enabled True to enable events; false to disable
     * @return This builder
     * @since 5.0
     */
    ApplicationContextBuilder eventsEnabled(boolean enabled);

    /**
     * Set a predicate to filter beans considered by the context.
     *
     * @param predicate The predicate to apply, or null to clear it
     * @return This builder
     * @since 5.0
     */
    ApplicationContextBuilder beansPredicate(java.util.function.@Nullable Predicate<io.micronaut.inject.QualifiedBeanType<?>> predicate);

    /**
     * Sets the class path resource resolver for the application context builder.
     *
     * @param resourceResolver the class path resource resolver
     * @return This builder
     * @since 5.0
     */
    ApplicationContextBuilder resourceResolver(@Nullable ClassPathResourceLoader resourceResolver);

    /**
     * Adds a property sources locator to the application context builder.
     *
     * @param propertySourcesLocator the property sources locator
     * @return This builder
     * @since 5.0
     */
    ApplicationContextBuilder propertySourcesLocator(PropertySourcesLocator propertySourcesLocator);

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
     * @param <T>          The type, a subclass of {@link AutoCloseable}. The close method of the implementation should shut down the context.
     * @return The running bean
     */
    default <T extends AutoCloseable> T run(Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        ApplicationContext applicationContext = start();
        T bean = applicationContext.getBean(type);
        if (bean instanceof LifeCycle<?> lifeCycle) {
            if (!lifeCycle.isRunning()) {
                lifeCycle.start();
            }
        }
        return bean;
    }
}
