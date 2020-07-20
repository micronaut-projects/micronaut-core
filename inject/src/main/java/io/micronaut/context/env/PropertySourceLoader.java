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
package io.micronaut.context.env;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.util.Toggleable;

import java.util.Optional;

/**
 * Loads the given property source for the given environment.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySourceLoader extends Toggleable, PropertySourceLocator, PropertySourceReader {

    /**
     * Load a {@link PropertySource} for the given {@link Environment}.
     *
     * @param environment The environment
     * @return An optional of {@link PropertySource}
     */
    @Override
    default Optional<PropertySource> load(Environment environment) {
        return load(Environment.DEFAULT_NAME, environment);
    }

    /**
     * Load a {@link PropertySource} for the given {@link Environment}.
     *
     * @param resourceName    The resourceName of the resource to load
     * @param resourceLoader  The {@link ResourceLoader} to retrieve the resource
     * @return An optional of {@link PropertySource}
     */
    Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader);

    /**
     * Load a {@link PropertySource} for the given {@link Environment}.
     *
     * @param resourceName        The resourceName of the resource to load
     * @param resourceLoader      The {@link ResourceLoader} to retrieve the resource
     * @param activeEnvironment   The environment to load
     * @return An optional of {@link PropertySource}
     */
    Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment);
}
