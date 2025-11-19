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

import io.micronaut.context.env.EnvironmentNamesDeducer;
import io.micronaut.context.env.EnvironmentPackagesDeducer;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.scan.ClassPathResourceLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An interface for configuring an application context.
 *
 * @author Zachary Klein
 * @author graemerocher
 * @since 1.0
 */
public interface ApplicationContextConfiguration extends BeanContextConfiguration {

    /**
     * The loaded {@link ApplicationContextConfigurer}.
     * @return The context configurer.
     * @since 4.5.0
     */
    default Optional<ApplicationContextConfigurer> getContextConfigurer() {
        return Optional.empty();
    }

    /**
     * @return The environment names
     */
    @NonNull List<String> getEnvironments();

    /**
     * If set to {@code true} (the default is {@code true}) Micronaut will attempt to automatically deduce the environment
     * it is running in using environment variables and/or stack trace inspection.
     *
     * <p>This method differs from {@link #isDeduceCloudEnvironment()} which controls whether network and/or disk probes
     *  are performed to try and automatically establish the Cloud environment.</p>
     *
     * <p>This behaviour controls the automatic activation of, for example, the {@link io.micronaut.context.env.Environment#TEST} when running tests.</p>
     *
     * @return True if the environments should be deduced
     */
    default Optional<Boolean> getDeduceEnvironments() {
        return Optional.empty();
    }

    /**
     * If set to {@code true} (the default is {@code true}) Micronaut will attempt to automatically deduce the user package.
     * Alternative is to use {@link io.micronaut.context.env.Environment#addPackage(String)}
     * @return True if the package should be deduced
     * @since 5.0
     */
    default boolean isDeducePackage() {
        return true;
    }

    /**
     * If set to {@code true} Micronaut will attempt to deduce the environment using safe methods like environment variables and the stack trace.
     *
     * <p>Enabling this should be done with caution since network probes are required to figure out whether the application is
     * running in certain clouds like GCP.</p>
     *
     * @return True if the environments should be deduced
     * @since 4.0.0
     */
    default boolean isDeduceCloudEnvironment() {
        return false;
    }

    /**
     * @return The default environments to be applied if no other environments
     * are explicitly specified or deduced.
     */
    default List<String> getDefaultEnvironments() {
        return Collections.emptyList();
    }

    /**
     * Whether to load the default set of property sources.
     * @return Returns {@code true} if the default set of property sources should be loaded.
     * @since 3.7.0
     */
    default boolean isEnableDefaultPropertySources() {
        return true;
    }

    /**
     * @return True if environment variables should contribute to configuration
     */
    default boolean isEnvironmentPropertySource() {
        return true;
    }

    /**
     * @return The environment variables to include in configuration
     */
    @SuppressWarnings("java:S1168") // null used to establish absence of config
    default @Nullable List<String> getEnvironmentVariableIncludes() {
        return null;
    }

    /**
     * @return The environment variables to exclude from configuration
     */
    @SuppressWarnings("java:S1168") // null used to establish absence of config
    default @Nullable List<String> getEnvironmentVariableExcludes() {
        return null;
    }

    /**
     * The optional conversion service to use.
     *
     * @return The conversion service
     */
    default Optional<MutableConversionService> getConversionService() {
        return Optional.empty();
    }

    /**
     * The class path resource loader to use.
     *
     * @return The classpath resource loader
     */
    default @NonNull ClassPathResourceLoader getResourceLoader() {
        return ClassPathResourceLoader.defaultLoader(getClassLoader());
    }

    /**
     * The config locations.
     *
     * @return The config locations
     */
    @SuppressWarnings("java:S1168") // null used to establish absence of config
    default @Nullable List<String> getOverrideConfigLocations() {
        return null;
    }

    /**
     * The banner is enabled by default.
     *
     * @return The banner is enabled by default
     */
    default boolean isBannerEnabled() {
        return true;
    }

    @Nullable
    @SuppressWarnings("java:S2447") // null used to establish absence of config
    default Boolean isBootstrapEnvironmentEnabled() {
        return null;
    }

    /**
     * @return The environment names deducer
     * @since 5.0
     */
    @Nullable
    default EnvironmentNamesDeducer getEnvironmentNamesDeducer() {
        return null;
    }

    /**
     * @return The package deducer
     * @since 5.0
     */
    @Nullable
    default EnvironmentPackagesDeducer getPackageDeducer() {
        return null;
    }

    /**
     * @return The application name
     * @since 5.0
     */
    @NonNull
    default String getApplicationName() {
        return "application";
    }

    /**
     * @return The extra property sources locators
     * @since 5.0
     */
    default Collection<PropertySourcesLocator> getPropertySourcesLocators() {
        return Collections.emptyList();
    }

}
