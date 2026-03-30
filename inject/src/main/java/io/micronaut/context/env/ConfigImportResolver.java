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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves recursive {@code micronaut.config.import} declarations for a property source list.
 */
final class ConfigImportResolver {

    static final String CONFIG_IMPORT = "micronaut.config.import";
    private static final Pattern INDEXED_PATTERN = Pattern.compile("^micronaut\\.config\\.import\\[(\\d+)]$");

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
     * @param root Root property sources
     * @return Flattened list including roots and resolved imports
     */
    List<PropertySource> resolve(PropertySource root) {
        List<PropertySource> resolved = new ArrayList<>();
        Set<ConfigImportIdentity.ImportIdentity> visited = new LinkedHashSet<>();
        Deque<String> chain = new ArrayDeque<>();
        ResolvedImportDeclarations parsed = normalize(root);
        resolved.add(parsed.propertySource());
        resolveImports(
            parsed.imports(),
            parsed.propertySource().getOrigin(),
            parsed.propertySource().getOrder(),
            parsed.propertySource().getConvention(),
            resolved,
            visited,
            chain
        );
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

            String parentLocation = parentOrigin != null ? parentOrigin.location() : null;
            if (parentLocation != null && parentLocation.equals(canonicalLocation)) {
                throw new ConfigurationException("Cycle detected while resolving micronaut.config.import: " + ConfigImportIdentity.cycleDisplay(parentLocation, canonicalLocation));
            }
            boolean cycleDetected = chain.contains(canonicalLocation);
            if (cycleDetected) {
                String previous = chain.peekLast();
                throw new ConfigurationException("Cycle detected while resolving micronaut.config.import: " + ConfigImportIdentity.cycleDisplay(previous, canonicalLocation));
            }
            if (!visited.contains(identity)) {
                Optional<PropertySource> imported = importOne(declaration, parentOrigin, tierOrder, canonicalLocation, parentConvention);
                if (imported.isPresent()) {
                    visited.add(identity);

                    chain.addLast(canonicalLocation);
                    try {
                        ResolvedImportDeclarations parsedImported = normalize(imported.get());
                        PropertySource importedSource = parsedImported.propertySource();
                        resolved.add(importedSource);
                        resolveImports(parsedImported.imports(), importedSource.getOrigin(), tierOrder, importedSource.getConvention(), resolved, visited, chain);
                    } finally {
                        chain.removeLast();
                    }
                }
            }
        }
    }

    private Optional<PropertySource> importOne(ConnectionString declaration,
                                               Origin parentOrigin,
                                               int tierOrder,
                                               String canonicalLocation,
                                               PropertyConvention fallbackConvention) {
        PropertySourceImporter<?> importer = findRequiredImporter(declaration, parentOrigin);

        PropertySource imported = importPropertySource(importer, declaration, parentOrigin).orElse(null);
        if (imported == null) {
            if (declaration.isOptional()) {
                return Optional.empty();
            }
            throw new ConfigurationException("Required config import not found: " + declaration.getRawValue());
        }

        return Optional.of(ImportedPropertySourceFactory.wrap(imported, canonicalLocation, tierOrder, fallbackConvention));
    }

    private <T> Optional<PropertySource> importPropertySource(PropertySourceImporter<T> importer,
                                                             ConnectionString declaration,
                                                             Origin parentOrigin) {
        T importDeclaration = importer.newImportDeclaration(declaration);
        PropertySourceImporter.ImportContext<T> context = new DefaultImportContext<>(environment, declaration, importDeclaration, parentOrigin);
        return importer.importPropertySource(context);
    }

    private PropertySourceImporter<?> findRequiredImporter(ConnectionString declaration, Origin parentOrigin) {
        PropertySourceImporter<?> importer = environment.findPropertySourceImporter(declaration.getProtocol());
        if (importer != null) {
            return importer;
        }
        String parentLocation = parentOrigin.location();
        throw new ConfigurationException("Unsupported micronaut.config.import protocol [" + declaration.getProtocol() + "] in " + declaration.getRawValue() + " declared from " + parentLocation);
    }

    ResolvedImportDeclarations normalize(PropertySource propertySource) {
        Object rootValue = null;
        boolean hasRoot = false;
        TreeMap<Integer, Object> indexedValues = new TreeMap<>();
        Map<String, Object> cleanMap = new LinkedHashMap<>();

        for (String key : propertySource) {
            Object value = propertySource.get(key);
            if (CONFIG_IMPORT.equals(key)) {
                hasRoot = true;
                rootValue = value;
                continue;
            }
            Matcher matcher = INDEXED_PATTERN.matcher(key);
            if (matcher.matches()) {
                int index = Integer.parseInt(matcher.group(1));
                indexedValues.put(index, value);
                continue;
            }
            cleanMap.put(key, value);
        }

        if (!hasRoot && indexedValues.isEmpty()) {
            return new ResolvedImportDeclarations(propertySource, List.of());
        }
        if (hasRoot && !indexedValues.isEmpty()) {
            throw new ConfigurationException("Cannot combine micronaut.config.import and indexed micronaut.config.import[n] declarations in " + propertySource.getName());
        }

        List<ConnectionString> declarations = hasRoot
            ? parseRootDeclaration(rootValue, propertySource)
            : parseIndexedDeclarations(indexedValues, propertySource);

        return new ResolvedImportDeclarations(copyPropertySource(propertySource, cleanMap), List.copyOf(declarations));
    }

    private List<ConnectionString> parseRootDeclaration(@Nullable Object value,
                                                        PropertySource propertySource) {
        if (value == null) {
            throw new ConfigurationException("micronaut.config.import cannot be null in " + propertySource.getName());
        }
        List<ConnectionString> declarations = new ArrayList<>();
        if (value instanceof CharSequence sequence) {
            declarations.add(ConnectionString.parse(sequence.toString()));
            return declarations;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof CharSequence sequence)) {
                    throw new ConfigurationException("micronaut.config.import list values must be strings in " + propertySource.getName());
                }
                declarations.add(ConnectionString.parse(sequence.toString()));
            }
            return declarations;
        }
        throw new ConfigurationException("micronaut.config.import must be a string or list in " + propertySource.getName());
    }

    private List<ConnectionString> parseIndexedDeclarations(Map<Integer, Object> values,
                                                            PropertySource propertySource) {
        List<ConnectionString> declarations = new ArrayList<>(values.size());
        int expectedIndex = 0;
        for (Map.Entry<Integer, Object> entry : values.entrySet()) {
            if (entry.getKey() != expectedIndex) {
                throw new ConfigurationException("micronaut.config.import indexed declarations must be contiguous from 0 in " + propertySource.getName());
            }
            Object value = entry.getValue();
            if (!(value instanceof CharSequence sequence)) {
                throw new ConfigurationException("micronaut.config.import[" + entry.getKey() + "] must be a string in " + propertySource.getName());
            }
            declarations.add(ConnectionString.parse(sequence.toString()));
            expectedIndex++;
        }
        return declarations;
    }

    private PropertySource copyPropertySource(PropertySource original, Map<String, Object> values) {
        return new MapPropertySource(original.getName(), values) {
            @Override
            public int getOrder() {
                return original.getOrder();
            }

            @Override
            public PropertyConvention getConvention() {
                return original.getConvention();
            }

            @Override
            public Origin getOrigin() {
                return original.getOrigin();
            }
        };
    }

    private record DefaultImportContext<T>(DefaultEnvironment environment,
                                            ConnectionString connectionString,
                                            T importDeclaration,
                                            @Nullable Origin parentOrigin) implements PropertySourceImporter.ImportContext<T> {

        @Override
        public String getCanonicalLocation() {
            return ConfigImportIdentity.canonicalLocation(connectionString, parentOrigin);
        }

        @Override
        public T importDeclaration() {
            return importDeclaration;
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

    record ResolvedImportDeclarations(PropertySource propertySource, List<ConnectionString> imports) {
    }
}
