/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.env.PropertySource.Origin;
import io.micronaut.context.env.PropertySource.PropertyConvention;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ConnectionString;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code micronaut.config.import} declarations and loads the associated property sources.
 *
 * @since 5.0
 */
@Internal
public final class ConfigImportPropertySourcesLocator implements PropertySourcesLocator, Closeable {

    private static final String MICRONAUT_CONFIG_PREFIX = "micronaut.config.";
    private static final String CONFIG_IMPORT = MICRONAUT_CONFIG_PREFIX + "import";
    private static final Pattern INDEXED_PATTERN = Pattern.compile("^micronaut\\.config\\.import\\[(\\d+)]$");
    private static final Pattern INDEXED_PROPERTY_PATTERN = Pattern.compile("^micronaut\\.config\\.import\\[(\\d+)]\\.(.+)$");
    private static final String STRUCTURED_ROOT_PREFIX = CONFIG_IMPORT + ".";
    private static final String PROVIDER = "provider";

    private final @Nullable Supplier<Collection<PropertySourceImporter<?>>> propertySourceImporterSupplier;
    private final Map<String, PropertySourceImporter<?>> importerByKindMap = CollectionUtils.newLinkedHashMap(10);

    public ConfigImportPropertySourcesLocator() {
        this(null);
    }

    public ConfigImportPropertySourcesLocator(@Nullable Supplier<Collection<PropertySourceImporter<?>>> propertySourceImporterSupplier) {
        this.propertySourceImporterSupplier = propertySourceImporterSupplier;
    }

    @Override
    public Collection<PropertySource> load(Environment environment) {
        cleanupImporters();
        Collection<PropertySourceImporter<?>> importers;
        if (propertySourceImporterSupplier != null) {
            importers = propertySourceImporterSupplier.get();
        } else {
            importers = evaluatePropertySourceImporters(environment);
        }
        importerByKindMap.putAll(toImporterByProvider(importers));
        List<PropertySource> result = new ArrayList<>();
        for (PropertySource propertySource : environment.getPropertySources()) {
            result.addAll(resolveConfigImports(environment, propertySource));
        }
        return result;
    }

    @Override
    public void close() {
        cleanupImporters();
    }

    private void cleanupImporters() {
        Collection<PropertySourceImporter<?>> importers = importerByKindMap.values();
        if (!importers.isEmpty()) {
            closeImporters(importers);
        }
        importerByKindMap.clear();
    }

    @Nullable
    private PropertySourceImporter<?> findPropertySourceImporter(String provider) {
        return importerByKindMap.get(provider.toLowerCase(Locale.ROOT));
    }

    private List<PropertySource> resolveConfigImports(Environment environment, PropertySource root) {
        List<PropertySource> resolved = new ArrayList<>();
        Set<ConfigImportIdentity.ImportIdentity> visited = new LinkedHashSet<>();
        Deque<String> chain = new ArrayDeque<>();
        ResolvedImportDeclarations parsed = normalize(root);
        resolveImports(
            environment,
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

    private void resolveImports(Environment environment,
                                List<ImportDeclaration> imports,
                                Origin parentOrigin,
                                int tierOrder,
                                PropertyConvention parentConvention,
                                List<PropertySource> resolved,
                                Set<ConfigImportIdentity.ImportIdentity> visited,
                                Deque<String> chain) {
        for (ImportDeclaration declaration : imports) {
            String identityLocation = declaration.identityLocation(parentOrigin);
            ConfigImportIdentity.ImportIdentity identity = new ConfigImportIdentity.ImportIdentity(identityLocation, tierOrder);

            String parentLocation = parentOrigin.location();
            if (parentLocation.equals(identityLocation)) {
                throw new ConfigurationException("Cycle detected while resolving micronaut.config.import: " + ConfigImportIdentity.cycleDisplay(parentLocation, identityLocation));
            }
            boolean cycleDetected = chain.contains(identityLocation);
            if (cycleDetected) {
                String previous = chain.peekLast();
                throw new ConfigurationException("Cycle detected while resolving micronaut.config.import: " + ConfigImportIdentity.cycleDisplay(previous, identityLocation));
            }
            if (!visited.contains(identity)) {
                Optional<PropertySource> imported = importOne(environment, declaration, parentOrigin, tierOrder, parentConvention);
                if (imported.isPresent()) {
                    visited.add(identity);

                    chain.addLast(identityLocation);
                    try {
                        ResolvedImportDeclarations parsedImported = normalize(imported.get());
                        PropertySource importedSource = parsedImported.propertySource();
                        resolved.add(importedSource);
                        resolveImports(environment, parsedImported.imports(), importedSource.getOrigin(), tierOrder, importedSource.getConvention(), resolved, visited, chain);
                    } finally {
                        chain.removeLast();
                    }
                }
            }
        }
    }

    private Optional<PropertySource> importOne(Environment environment,
                                               ImportDeclaration declaration,
                                               Origin parentOrigin,
                                               int tierOrder,
                                               PropertyConvention fallbackConvention) {
        PropertySourceImporter<?> importer = findRequiredImporter(declaration, parentOrigin);

        PropertySource imported = importPropertySource(environment, importer, declaration, parentOrigin).orElse(null);
        if (imported == null) {
            if (declaration.optional()) {
                return Optional.empty();
            }
            throw new ConfigurationException("Required config import not found: " + declaration.displayValue());
        }

        return Optional.of(ImportedPropertySourceFactory.wrap(imported, declaration.identityLocation(parentOrigin), tierOrder, fallbackConvention));
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<PropertySource> importPropertySource(Environment environment,
                                                              PropertySourceImporter<?> importer,
                                                              ImportDeclaration declaration,
                                                              Origin parentOrigin) {
        PropertySourceImporter<T> typedImporter = (PropertySourceImporter<T>) importer;
        T importDeclaration = declaration.createImportDeclaration(typedImporter);
        PropertySourceImporter.ImportContext<T> context = new DefaultImportContext<>(this, (DefaultEnvironment) environment, declaration.connectionString().orElse(null), importDeclaration, parentOrigin);
        return typedImporter.importPropertySource(context);
    }

    private PropertySourceImporter<?> findRequiredImporter(ImportDeclaration declaration, Origin parentOrigin) {
        PropertySourceImporter<?> importer = findPropertySourceImporter(declaration.provider());
        if (importer != null) {
            return importer;
        }
        String parentLocation = parentOrigin.location();
        throw new ConfigurationException("Unsupported micronaut.config.import provider [" + declaration.provider() + "] in " + declaration.displayValue() + " declared from " + parentLocation);
    }

    ResolvedImportDeclarations normalize(PropertySource propertySource) {
        Object rootValue = null;
        boolean hasRoot = false;
        TreeMap<Integer, Object> indexedValues = new TreeMap<>();
        TreeMap<Integer, Map<String, Object>> indexedObjectValues = new TreeMap<>();
        Map<String, Object> structuredRootValues = new LinkedHashMap<>();

        for (String key : propertySource) {
            if (!key.startsWith(MICRONAUT_CONFIG_PREFIX)) {
                continue;
            }
            Object value = propertySource.get(key);
            if (value == null) {
                continue;
            }
            if (CONFIG_IMPORT.equals(key)) {
                hasRoot = true;
                rootValue = value;
                continue;
            }
            if (key.startsWith(STRUCTURED_ROOT_PREFIX)) {
                hasRoot = true;
                structuredRootValues.put(key.substring(STRUCTURED_ROOT_PREFIX.length()), value);
                continue;
            }
            Matcher indexedMatcher = INDEXED_PATTERN.matcher(key);
            if (indexedMatcher.matches()) {
                int index = Integer.parseInt(indexedMatcher.group(1));
                indexedValues.put(index, value);
                continue;
            }
            Matcher indexedPropertyMatcher = INDEXED_PROPERTY_PATTERN.matcher(key);
            if (indexedPropertyMatcher.matches()) {
                int index = Integer.parseInt(indexedPropertyMatcher.group(1));
                indexedObjectValues.computeIfAbsent(index, ignored -> new LinkedHashMap<>())
                    .put(indexedPropertyMatcher.group(2), value);
            }
        }

        if (hasRoot && rootValue == null && !structuredRootValues.isEmpty()) {
            rootValue = new LinkedHashMap<>(structuredRootValues);
        }
        if (hasRoot && rootValue != null && !(rootValue instanceof Map) && !structuredRootValues.isEmpty()) {
            throw new ConfigurationException("Cannot combine micronaut.config.import and micronaut.config.import.* declarations in " + propertySource.getName());
        }

        if (!hasRoot && indexedValues.isEmpty() && indexedObjectValues.isEmpty()) {
            return new ResolvedImportDeclarations(propertySource, List.of());
        }
        if (hasRoot && (!indexedValues.isEmpty() || !indexedObjectValues.isEmpty())) {
            throw new ConfigurationException("Cannot combine micronaut.config.import and indexed micronaut.config.import[n] declarations in " + propertySource.getName());
        }
        if (!indexedValues.isEmpty() && !indexedObjectValues.isEmpty()) {
            throw new ConfigurationException("Cannot combine scalar and structured indexed micronaut.config.import[n] declarations in " + propertySource.getName());
        }

        List<ImportDeclaration> declarations = hasRoot
            ? parseRootDeclaration(rootValue, propertySource)
            : parseIndexedDeclarations(indexedValues, indexedObjectValues, propertySource);

        return new ResolvedImportDeclarations(propertySource, List.copyOf(declarations));
    }

    private List<ImportDeclaration> parseRootDeclaration(@Nullable Object value,
                                                         PropertySource propertySource) {
        return switch (value) {
            case null -> throw new ConfigurationException("micronaut.config.import cannot be null in " + propertySource.getName());
            case CharSequence sequence -> List.of(ImportDeclaration.of(ConnectionString.parse(sequence.toString())));
            case Map<?, ?> map -> List.of(ImportDeclaration.ofConvertibleValues(toConvertibleValues(map, propertySource.getName())));
            case Iterable<?> iterable -> {
                List<ImportDeclaration> declarations = new ArrayList<>();
                for (Object item : iterable) {
                    if (item instanceof CharSequence sequence) {
                        declarations.add(ImportDeclaration.of(ConnectionString.parse(sequence.toString())));
                    } else if (item instanceof Map<?, ?> map) {
                        declarations.add(ImportDeclaration.ofConvertibleValues(toConvertibleValues(map, propertySource.getName())));
                    } else {
                        throw new ConfigurationException("micronaut.config.import list values must be strings or maps in " + propertySource.getName());
                    }
                }
                yield declarations;
            }
            default -> throw new ConfigurationException("micronaut.config.import must be a string, map, or list in " + propertySource.getName());
        };
    }

    private List<ImportDeclaration> parseIndexedDeclarations(Map<Integer, Object> scalarValues,
                                                             Map<Integer, Map<String, Object>> structuredValues,
                                                             PropertySource propertySource) {
        if (!scalarValues.isEmpty()) {
            List<ImportDeclaration> declarations = new ArrayList<>(scalarValues.size());
            int expectedIndex = 0;
            for (Map.Entry<Integer, Object> entry : scalarValues.entrySet()) {
                if (entry.getKey() != expectedIndex) {
                    throw new ConfigurationException("micronaut.config.import indexed declarations must be contiguous from 0 in " + propertySource.getName());
                }
                Object value = entry.getValue();
                if (!(value instanceof CharSequence sequence)) {
                    throw new ConfigurationException("micronaut.config.import[" + entry.getKey() + "] must be a string in " + propertySource.getName());
                }
                declarations.add(ImportDeclaration.of(ConnectionString.parse(sequence.toString())));
                expectedIndex++;
            }
            return declarations;
        }

        List<ImportDeclaration> declarations = new ArrayList<>(structuredValues.size());
        int expectedIndex = 0;
        for (Map.Entry<Integer, Map<String, Object>> entry : structuredValues.entrySet()) {
            if (entry.getKey() != expectedIndex) {
                throw new ConfigurationException("micronaut.config.import indexed declarations must be contiguous from 0 in " + propertySource.getName());
            }
            declarations.add(ImportDeclaration.ofConvertibleValues(new ConvertibleValuesMap<>(entry.getValue())));
            expectedIndex++;
        }
        return declarations;
    }

    private ConvertibleValues<Object> toConvertibleValues(Map<?, ?> map, String propertySourceName) {
        Map<String, Object> values = CollectionUtils.newLinkedHashMap(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof CharSequence keySequence)) {
                throw new ConfigurationException("micronaut.config.import map keys must be strings in " + propertySourceName);
            }
            values.put(keySequence.toString(), entry.getValue());
        }
        return new ConvertibleValuesMap<>(values);
    }

    private static Collection<PropertySourceImporter<?>> evaluatePropertySourceImporters(Environment environment) {
        SoftServiceLoader<PropertySourceImporter> definitions = SoftServiceLoader.load(PropertySourceImporter.class, environment.getClassLoader());
        List<PropertySourceImporter<?>> importers = new ArrayList<>();
        for (PropertySourceImporter importer : definitions.collectAll()) {
            importers.add(importer);
        }
        Map<String, PropertySourceImporter<?>> importersByType = CollectionUtils.newLinkedHashMap(importers.size());
        for (PropertySourceImporter<?> importer : importers) {
            importersByType.putIfAbsent(importer.getClass().getName(), importer);
        }
        return new ArrayList<>(importersByType.values());
    }

    static Map<String, PropertySourceImporter<?>> toImporterByProvider(Collection<PropertySourceImporter<?>> importers) {
        Map<String, PropertySourceImporter<?>> importersByProvider = CollectionUtils.newLinkedHashMap(importers.size());
        for (PropertySourceImporter<?> importer : importers) {
            if (!importer.isEnabled()) {
                continue;
            }
            String provider = importer.getProvider();
            if (provider.isBlank()) {
                throw new ConfigurationException("Property source importer [" + importer + "] returned an empty provider");
            }
            String providerKey = provider.toLowerCase(Locale.ROOT);
            PropertySourceImporter<?> previous = importersByProvider.putIfAbsent(providerKey, importer);
            if (previous != null) {
                throw new ConfigurationException("Duplicate property source importer for provider [" + providerKey + "]: " + previous + " and " + importer);
            }
        }
        return importersByProvider;
    }

    private static void closeImporters(Collection<PropertySourceImporter<?>> importers) {
        RuntimeException closeException = null;
        for (PropertySourceImporter<?> importer : importers) {
            try {
                importer.close();
            } catch (Exception e) {
                RuntimeException wrapped = new RuntimeException("Error closing property source importer: " + importer.getClass().getName(), e);
                if (closeException == null) {
                    closeException = wrapped;
                } else {
                    closeException.addSuppressed(wrapped);
                }
            }
        }
        if (closeException != null) {
            throw closeException;
        }
    }

    private record DefaultImportContext<T>(
        ConfigImportPropertySourcesLocator configImportPropertySourcesLocator,
        DefaultEnvironment defaultEnvironment,
        @Nullable ConnectionString connectionString,
        T importDeclaration,
        @Nullable Origin parentOrigin) implements PropertySourceImporter.ImportContext<T> {

        @Override
        public Environment environment() {
            return defaultEnvironment;
        }

        @Override
        public @Nullable ConnectionString connectionString() {
            return connectionString;
        }

        @Override
        public T importDeclaration() {
            return importDeclaration;
        }

        @Override
        public @Nullable Origin parentOrigin() {
            return parentOrigin;
        }

        @Override
        public Optional<PropertySource> importPropertySource(ResourceLoader resourceLoader,
                                                             String resourcePath,
                                                             String sourceName,
                                                             Origin origin) {
            return defaultEnvironment.loadImportedPropertySource(resourceLoader, resourcePath, sourceName, origin);
        }

        @Override
        public Optional<PropertySource> importPropertySource(String content,
                                                             String sourceName,
                                                             String extension,
                                                             Origin origin) {
            return defaultEnvironment.loadImportedPropertySourceFromContent(content, sourceName, extension, origin);
        }

        @Override
        public Optional<PropertySource> importClasspathPropertySource(String resourcePath,
                                                                      String sourceName,
                                                                      Origin origin,
                                                                      boolean allowMultiple) {
            return defaultEnvironment.loadImportedClasspathPropertySource(resourcePath, sourceName, origin, allowMultiple);
        }
    }

    private sealed interface ImportDeclaration permits ScalarImportDeclaration, StructuredImportDeclaration {
        String provider();

        boolean optional();

        String displayValue();

        String identityLocation(@Nullable Origin parentOrigin);

        <T> T createImportDeclaration(PropertySourceImporter<T> importer);

        Optional<ConnectionString> connectionString();

        static ImportDeclaration of(ConnectionString connectionString) {
            return new ScalarImportDeclaration(connectionString);
        }

        static ImportDeclaration ofConvertibleValues(ConvertibleValues<Object> values) {
            return new StructuredImportDeclaration(values);
        }
    }

    private record ScalarImportDeclaration(
        ConnectionString declaration) implements ImportDeclaration {
        @Override
        public String provider() {
            return declaration.getProtocol();
        }

        @Override
        public boolean optional() {
            return declaration.isOptional();
        }

        @Override
        public String displayValue() {
            return declaration.getRawValue();
        }

        @Override
        public String identityLocation(@Nullable Origin parentOrigin) {
            return ConfigImportIdentity.canonicalLocation(declaration, parentOrigin);
        }

        @Override
        public <T> T createImportDeclaration(PropertySourceImporter<T> importer) {
            return importer.newImportDeclaration(declaration);
        }

        @Override
        public Optional<ConnectionString> connectionString() {
            return Optional.of(declaration);
        }
    }

    private record StructuredImportDeclaration(
        ConvertibleValues<Object> values) implements ImportDeclaration {
        @Override
        public String provider() {
            return values.get(PROVIDER, String.class)
                .filter(provider -> !provider.isBlank())
                .orElseThrow(() -> new ConfigurationException("micronaut.config.import map declarations require non-blank ['" + PROVIDER + "']"));
        }

        @Override
        public boolean optional() {
            return values.get("optional", Boolean.class).orElse(false);
        }

        @Override
        public String displayValue() {
            return values.asMap().toString();
        }

        @Override
        public String identityLocation(@Nullable Origin parentOrigin) {
            Map<String, Object> filteredValues = new TreeMap<>(values.asMap());
            filteredValues.remove("optional");

            Object resourcePath = filteredValues.size() == 1 ? filteredValues.get(FilePropertySourceImporter.RESOURCE_PATH) : null;
            if (resourcePath instanceof String rp && !rp.isBlank()) {
                try {
                    ConnectionString connectionString = ConnectionString.parse(provider() + "://" + rp);
                    return ConfigImportIdentity.canonicalLocation(connectionString, parentOrigin);
                } catch (IllegalArgumentException | ConfigurationException ignored) {
                    // Fall through to default
                }
            }
            return provider() + ":" + filteredValues;
        }

        @Override
        public <T> T createImportDeclaration(PropertySourceImporter<T> importer) {
            return importer.newImportDeclaration(values);
        }

        @Override
        public Optional<ConnectionString> connectionString() {
            return Optional.empty();
        }
    }

    record ResolvedImportDeclarations(PropertySource propertySource,
                                      List<ImportDeclaration> imports) {
    }
}
