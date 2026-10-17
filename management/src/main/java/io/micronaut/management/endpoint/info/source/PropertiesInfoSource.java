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
package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.management.endpoint.info.InfoSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Extends {@link io.micronaut.management.endpoint.info.InfoEndpoint} to add a helper method for retrieving a
 * {@link PropertySource} from a properties file, or from any other format a {@link PropertySourceLoader} supports.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
public interface PropertiesInfoSource extends InfoSource {

    /**
     * <p>Extends {@link io.micronaut.management.endpoint.info.InfoEndpoint} to add a helper method for retrieving a
     * {@link PropertySource} from a properties file. </p>
     *
     * @param path The path to the properties file
     * @param prefix prefix for resolving the file (used if not included in {@code path})
     * @param extension file extension (used if not included in {@code path})
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

    /**
     * <p>Retrieves a {@link PropertySource} from a properties file, or from a file in any other format that one of the
     * given {@link PropertySourceLoader}s supports.</p>
     *
     * <p>A {@code path} without an extension, or one that ends with {@code extension}, is read as a properties file
     * exactly as {@link #retrievePropertiesPropertySource(String, String, String, ResourceResolver)} does. Any other
     * extension selects the loader whose {@link PropertySourceLoader#getExtensions() extensions} contain it, and the
     * file is read with that loader. When no loader supports the extension a warning is logged and an empty
     * {@link Optional} is returned.</p>
     *
     * @param path The path to the file
     * @param prefix prefix for resolving the file (used if not included in {@code path})
     * @param extension file extension, including the leading dot, of the default properties format
     * @param resourceResolver Instance of {@link ResourceResolver} to resolve the file location
     * @param propertySourceLoaders The loaders that may read the file, typically
     *                              {@link io.micronaut.context.env.Environment#getPropertySourceLoaders()}
     * @return An {@link Optional} of {@link PropertySource} containing the values from the file
     * @since 5.3.0
     */
    default Optional<PropertySource> retrievePropertySource(String path, String prefix, String extension, ResourceResolver resourceResolver, Collection<PropertySourceLoader> propertySourceLoaders) {
        String fileExtension = NameUtils.extension(path);
        if (fileExtension.isEmpty() || (extension != null && path.endsWith(extension))) {
            return retrievePropertiesPropertySource(path, prefix, extension, resourceResolver);
        }

        PropertySourceLoader loader = null;
        for (PropertySourceLoader candidate : propertySourceLoaders) {
            if (candidate.isEnabled() && candidate.getExtensions().contains(fileExtension)) {
                // as in the environment, a later loader supporting the same extension takes precedence
                loader = candidate;
            }
        }
        if (loader == null) {
            LoggerFactory.getLogger(PropertiesInfoSource.class)
                .warn("No enabled PropertySourceLoader supports the [{}] extension, ignoring [{}]", fileExtension, path);
            return Optional.empty();
        }

        String resourcePath = (prefix != null) && !path.startsWith(prefix) ? prefix + path : path;
        Optional<ResourceLoader> resourceLoader = resourceResolver.getSupportingLoader(resourcePath);
        if (resourceLoader.isEmpty()) {
            return Optional.empty();
        }
        Optional<InputStream> resource = resourceLoader.get().getResourceAsStream(resourcePath);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        String name = resourcePath.substring(0, resourcePath.length() - fileExtension.length() - 1);
        try (InputStream input = resource.get()) {
            Map<String, Object> values = loader.read(name, input);
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(PropertySource.of(name, values, PropertySource.Origin.of(resourcePath)));
        } catch (IOException e) {
            throw new ConfigurationException("I/O exception occurred reading [" + resourcePath + "]: " + e.getMessage(), e);
        }
    }

}
