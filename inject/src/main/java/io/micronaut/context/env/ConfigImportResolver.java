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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.env.PropertySource.Origin;
import io.micronaut.context.env.PropertySource.PropertyConvention;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.util.ConnectionString;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves recursive {@code micronaut.config.import} declarations for a property source list.
 */
final class ConfigImportResolver {

    private final DefaultEnvironment environment;

    /**
     * @param environment Environment used to resolve importers
     */
    ConfigImportResolver(DefaultEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Resolve imports for each root property source.
     *
     * @param roots Root property sources
     * @return Flattened list including roots and resolved imports
     */
    List<PropertySource> resolve(List<PropertySource> roots) {
        if (roots.isEmpty()) {
            return List.of();
        }
        List<PropertySource> resolved = new ArrayList<>(roots.size());
        Set<ConfigImportIdentity.ImportIdentity> visited = new LinkedHashSet<>();
        Deque<String> chain = new ArrayDeque<>();
        for (PropertySource root : roots) {
            ConfigImportDeclarations.ParsedPropertySource parsed = ConfigImportDeclarations.normalize(root);
            resolved.add(parsed.propertySource());
            resolveImports(parsed.imports(), parsed.propertySource().getOrigin(), parsed.propertySource().getOrder(), parsed.propertySource().getConvention(), resolved, visited, chain);
        }
        return resolved;
    }

    private void resolveImports(List<ConnectionString> imports,
                                Origin parentOrigin,
                                int tierOrder,
                                PropertyConvention parentConvention,
                                List<PropertySource> resolved,
                                Set<ConfigImportIdentity.ImportIdentity> visited,
                                Deque<String> chain) {
        for (ConnectionString declaration : imports) {
            String canonicalLocation = ConfigImportIdentity.canonicalLocation(declaration, parentOrigin);
            ConfigImportIdentity.ImportIdentity identity = new ConfigImportIdentity.ImportIdentity(canonicalLocation, tierOrder);

            if (chain.contains(canonicalLocation)) {
                String previous = chain.peekLast();
                throw new ConfigurationException("Cycle detected while resolving micronaut.config.import: " + ConfigImportIdentity.cycleDisplay(previous == null ? canonicalLocation : previous, canonicalLocation));
            }
            if (!visited.add(identity)) {
                continue;
            }

            Optional<PropertySource> imported = importOne(declaration, parentOrigin, tierOrder, canonicalLocation, parentConvention);
            if (imported.isEmpty()) {
                continue;
            }

            chain.addLast(canonicalLocation);
            try {
                ConfigImportDeclarations.ParsedPropertySource parsedImported = ConfigImportDeclarations.normalize(imported.get());
                PropertySource importedSource = parsedImported.propertySource();
                resolved.add(importedSource);
                resolveImports(parsedImported.imports(), importedSource.getOrigin(), tierOrder, importedSource.getConvention(), resolved, visited, chain);
            } finally {
                chain.removeLast();
            }
        }
    }

    private Optional<PropertySource> importOne(ConnectionString declaration,
                                               Origin parentOrigin,
                                               int tierOrder,
                                               String canonicalLocation,
                                               PropertyConvention fallbackConvention) {
        PropertySourceImporter importer = environment.findPropertySourceImporter(declaration.getProtocol());
        if (importer == null) {
            throw new ConfigurationException("Unsupported micronaut.config.import protocol [" + declaration.getProtocol() + "] in " + declaration.getRawValue());
        }

        PropertySourceImporter.ImportContext context = new DefaultImportContext(environment, declaration, parentOrigin);
        PropertySource imported = importer.importPropertySource(context).orElse(null);
        if (imported == null) {
            if (declaration.isOptional()) {
                return Optional.empty();
            }
            throw new ConfigurationException("Required config import not found: " + declaration.getRawValue());
        }

        PropertyConvention convention = imported.getConvention() != null ? imported.getConvention() : fallbackConvention;
        return Optional.of(ImportedPropertySourceFactory.wrap(imported, canonicalLocation, tierOrder, convention));
    }

    private record DefaultImportContext(DefaultEnvironment environment,
                                        ConnectionString connectionString,
                                        @Nullable Origin parentOrigin) implements PropertySourceImporter.ImportContext {

        @Override
        public String getCanonicalLocation() {
            return ConfigImportIdentity.canonicalLocation(connectionString, parentOrigin);
        }

        @Override
        public Optional<PropertySource> importPropertySource(ResourceLoader resourceLoader,
                                                             String resourcePath,
                                                             String sourceName,
                                                             Origin origin) {
            return environment.loadImportedPropertySource(resourceLoader, resourcePath, sourceName, origin);
        }

        @Override
        public Optional<PropertySource> importPropertySource(String content,
                                                             String sourceName,
                                                             String extension,
                                                             Origin origin) {
            return environment.loadImportedPropertySourceFromContent(content, sourceName, extension, origin);
        }

        @Override
        public Optional<PropertySource> importClasspathPropertySource(String resourcePath,
                                                                      String sourceName,
                                                                      Origin origin,
                                                                      boolean allowMultiple) {
            return environment.loadImportedClasspathPropertySource(resourcePath, sourceName, origin, allowMultiple);
        }

        @Override
        public String getResourcePath() {
            return ConfigImportIdentity.extractResourcePath(getCanonicalLocation());
        }
    }
}
