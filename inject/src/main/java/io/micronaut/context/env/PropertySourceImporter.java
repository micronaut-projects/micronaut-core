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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.core.util.Toggleable;
import org.jspecify.annotations.Nullable;

import io.micronaut.context.env.PropertySource.Origin;

import java.util.Optional;

/**
 * Imports a {@link PropertySource} for a given protocol.
 *
 * <p>Importer instances are created for a single configuration loading cycle. Micronaut reuses the same importer
 * instance for all imports resolved during that cycle so implementations may share expensive resources such as
 * HTTP clients across multiple imports. Once configuration loading completes, Micronaut invokes {@link #close()}.
 * The same lifecycle applies during startup and each refresh operation.</p>
 *
 * @since 5.0
 */
@Experimental
public interface PropertySourceImporter extends Toggleable, AutoCloseable {

    /**
     * @return The protocol this importer supports.
     */
    String getProtocol();

    /**
     * Resolve a property source from the provided context.
     *
     * @param context The import context
     * @return The imported property source
     */
    Optional<PropertySource> importPropertySource(ImportContext context);

    /**
     * Close resources associated with this importer after a configuration loading cycle completes.
     *
     * <p>Micronaut invokes this method once after startup loading finishes and once after each refresh load finishes.
     * Implementations may override it to release per-load resources. The default implementation is a no-op.</p>
     */
    @Override
    default void close() {
    }

    /**
     * Import context information and helper methods.
     */
    interface ImportContext {

        /**
         * @return The environment that is resolving imports
         */
        Environment environment();

        /**
         * @return The parsed import declaration
         */
        ConnectionString connectionString();

        /**
         * @return The canonical location.
         */
        default String getCanonicalLocation() {
            return ConfigImportIdentity.canonicalLocation(connectionString(), parentOrigin());
        }

        /**
         * @return The origin of the property source that declared the import
         */
        @Nullable Origin parentOrigin();

        /**
         * Import a property source from a resource path.
         *
         * @param resourceLoader The resource loader
         * @param resourcePath   The resource path
         * @param sourceName     The imported source name
         * @param origin         The property source origin
         * @return Imported property source
         */
        Optional<PropertySource> importPropertySource(ResourceLoader resourceLoader,
                                                      String resourcePath,
                                                      String sourceName,
                                                      Origin origin);

        /**
         * Import a property source from inline text content.
         *
         * @param content    The inline content
         * @param sourceName The imported source name
         * @param extension  The format extension (for example yml, properties, json)
         * @param origin     The property source origin
         * @return Imported property source
         */
        Optional<PropertySource> importPropertySource(String content,
                                                      String sourceName,
                                                      String extension,
                                                      Origin origin);

        /**
         * Import a classpath property source with optional wildcard semantics.
         *
         * @param resourcePath  The classpath resource path
         * @param sourceName    The imported source name
         * @param origin        The property source origin
         * @param allowMultiple Whether multiple resources should be merged in discovered order
         * @return Imported property source
         */
        Optional<PropertySource> importClasspathPropertySource(String resourcePath,
                                                               String sourceName,
                                                               Origin origin,
                                                               boolean allowMultiple);

        /**
         * @return The resource path.
         */
        default String getResourcePath() {
            return ConfigImportIdentity.extractResourcePath(getCanonicalLocation());
        }
    }
}
