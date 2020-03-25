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
package io.micronaut.web.router.version;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;

import javax.inject.Singleton;

/**
 * Implementation of {@link DefaultVersionProvider} which uses configuration.
 * If value micronaut.router.versioning.default-version is present, this bean is loaded and returns that value.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Requires(beans = RoutesVersioningConfiguration.class)
@Requires(property = RoutesVersioningConfiguration.PREFIX + ".default-version")
@Singleton
public class ConfigurationDefaultVersionProvider implements DefaultVersionProvider {

    private final String defaultVersion;

    /**
     *
     * @param routesVersioningConfiguration Routes Versioning Configuration.
     */
    public ConfigurationDefaultVersionProvider(RoutesVersioningConfiguration routesVersioningConfiguration) {
        if (!routesVersioningConfiguration.getDefaultVersion().isPresent()) {
            throw new ConfigurationException("this bean should not be loaded if " + RoutesVersioningConfiguration.PREFIX + ".default-version" + "is null");
        }
        this.defaultVersion = routesVersioningConfiguration.getDefaultVersion().get();
    }

    @Override
    public String resolveDefaultVersion() {
        return defaultVersion;
    }
}
