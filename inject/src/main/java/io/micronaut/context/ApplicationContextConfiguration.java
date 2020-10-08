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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.scan.ClassPathResourceLoader;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

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
     * @return The environment names
     */
    @NonNull List<String> getEnvironments();

    /**
     * @return True if the environments should be deduced
     */
    default Optional<Boolean> getDeduceEnvironments() {
        return Optional.empty();
    }

    /**
     * @return The default environments to be applied if no other environments
     * are explicitly specified or deduced.
     */
    default List<String> getDefaultEnvironments() {
        return Collections.emptyList();
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
    default @Nullable List<String> getEnvironmentVariableIncludes() {
        return null;
    }

    /**
     * @return The environment variables to exclude from configuration
     */
    default @Nullable List<String> getEnvironmentVariableExcludes() {
        return null;
    }

    /**
     * The default conversion service to use.
     *
     * @return The conversion service
     */
    default @NonNull ConversionService<?> getConversionService() {
        return ConversionService.SHARED;
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
    default @Nullable List<String> getOverrideConfigLocations() {
        return null;
    }
}
