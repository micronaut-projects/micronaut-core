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
package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.management.endpoint.info.InfoSource;

import java.util.Optional;

/**
 * <p>Extends {@link io.micronaut.management.endpoint.info.InfoEndpoint} to add a helper method for retrieving a
 * {@link PropertySource} from a properties file.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
public interface PropertiesInfoSource extends InfoSource {

    /**
     * <p>Extends {@link io.micronaut.management.endpoint.info.InfoEndpoint} to add a helper method for retrieving a
     * {@link PropertySource} from a properties file. </p>
     *
     * @param path             The path to the properties file
     * @param prefix           prefix for resolving the file (used if not included in {@code path})
     * @param extension        file extension (used if not included in {@code path})
     * @param resourceResolver Instance of {@link ResourceResolver} to resolve the file location
     * @return An {@link Optional} of {@link PropertySource} containing the values from the properties file
     */
    default Optional<PropertySource> retrievePropertiesPropertySource(String path, String prefix, String extension, ResourceResolver resourceResolver) {
        StringBuilder pathBuilder = new StringBuilder();

        if ((prefix != null) && !path.startsWith(prefix)) {
            pathBuilder.append(prefix);
        }

        if ((extension != null) && path.endsWith(extension)) {
            int index = path.indexOf(extension);
            pathBuilder.append(path, 0, index);
        } else {
            pathBuilder.append(path);
        }

        String propertiesPath = pathBuilder.toString();

        Optional<ResourceLoader> resourceLoader = resourceResolver.getSupportingLoader(propertiesPath);
        if (resourceLoader.isPresent()) {
            PropertiesPropertySourceLoader propertySourceLoader = new PropertiesPropertySourceLoader();
            return propertySourceLoader.load(propertiesPath, resourceLoader.get());
        }

        return Optional.empty();
    }

}
