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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.EnvironmentProperties;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.MapPropertyResolver;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.core.value.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A {@link PropertyResolver} that resolves from one or many {@link PropertySource} instances.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class PropertySourcePropertyResolver implements PropertyResolver, AutoCloseable {

    public static final DefaultPropertyEntry NULL_ENTRY = new DefaultPropertyEntry(
        "NULL", null, null, null
    );
    private static final EnvironmentProperties CURRENT_ENV = StaticOptimizations.get(EnvironmentProperties.class)
            .orElseGet(EnvironmentProperties::empty);
    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

    private static final Object NO_VALUE = new Object();
    private static final PropertyCatalog[] CONVENTIONS = {PropertyCatalog.GENERATED, PropertyCatalog.RAW};
    private static final String WILD_CARD_SUFFIX = ".*";
    private final ConversionService conversionService;
    protected final PropertyPlaceholderResolver propertyPlaceholderResolver;
    // properties are stored in an array of maps organized by character in the alphabet
    // this allows optimization of searches by prefix
    @SuppressWarnings("MagicNumber")
    private final Map<String, DefaultPropertyEntry>[] catalog = new Map[58];
    private final Map<String, DefaultPropertyEntry>[] rawCatalog = new Map[58];
    private final Map<String, DefaultPropertyEntry>[] nonGenerated = new Map[58];

    private final Logger log;

    private final Map<String, Boolean> containsCache = new ConcurrentHashMap<>(20);
    /**
     * Cache for values <i>before</i> conversion. This avoids recomputing placeholders, which keeps
     * random values (e.g. {@code ${random.port}} stable).
     */
    private final Map<String, Object> placeholderResolutionCache = new ConcurrentHashMap<>(20);
    /**
     * Cache for values <i>after</i> conversion.
     */
    private final Map<ConversionCacheKey, Object> resolvedValueCache = new ConcurrentHashMap<>(20);
    private final EnvironmentProperties environmentProperties = EnvironmentProperties.fork(CURRENT_ENV);

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver} for the given {@link ConversionService}.
     *
     * @param conversionService The {@link ConversionService}
     * @param logEnabled        flag to enable or disable logger
     */
    public PropertySourcePropertyResolver(ConversionService conversionService, boolean logEnabled) {
        this.log = logEnabled ? LoggerFactory.getLogger(getClass()) : NOPLogger.NOP_LOGGER;
        this.conversionService = conversionService;
        this.propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(this, conversionService);
    }

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver} for the given {@link ConversionService}.
     *
     * @param conversionService The {@link ConversionService}
     */
    public PropertySourcePropertyResolver(ConversionService conversionService) {
        this(conversionService, true);
    }

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver}.
     */
    public PropertySourcePropertyResolver() {
        this(ConversionService.SHARED);
    }

    /**
     * Creates a new {@link PropertySourcePropertyResolver} for the given {@link PropertySource} instances.
     *
     * @param propertySources The {@link PropertySource} instances
     */
    public PropertySourcePropertyResolver(PropertySource... propertySources) {
        this(ConversionService.SHARED);
        if (propertySources != null) {
            for (PropertySource propertySource : propertySources) {
                addPropertySource(propertySource);
            }
        }
    }

    void reset() {
        synchronized (catalog) {
            Arrays.fill(catalog, null);
            resetCaches();
        }
    }

    public Map<String, Object> diff(Runnable change) {
        Map<String, DefaultPropertyEntry>[] copiedCatalog = copyCatalog(catalog);
        change.run();
        return diffCatalog(copiedCatalog, catalog);
    }


    private Map<String, Object> diffCatalog(Map<String, DefaultPropertyEntry>[] original, Map<String, DefaultPropertyEntry>[] newCatalog) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < original.length; i++) {
            Map<String, DefaultPropertyEntry> map = original[i];
            Map<String, DefaultPropertyEntry> newMap = newCatalog[i];
            boolean hasNew = newMap != null;
            boolean hasOld = map != null;
            if (!hasOld && hasNew) {
                changes.putAll(newMap);
            } else {
                if (!hasNew && hasOld) {
                    changes.putAll(map);
                } else if (hasOld && hasNew) {
                    diffMap(map, newMap, changes);
                }
            }
        }
        if (!changes.isEmpty()) {
            Map<String, Object> placeholdersAltered = new LinkedHashMap<>();
            for (Map<String, DefaultPropertyEntry> map :
                newCatalog) {
                if (map != null) {
                    map.forEach((key, v) -> {
                        if (v.value() instanceof String val) {
                            for (String changed : changes.keySet()) {
                                if (val.contains(changed)) {
                                    placeholdersAltered.put(key, v.value());
                                }
                            }
                        }
                    });
                }
            }
            changes.putAll(placeholdersAltered);
        }
        return changes;
    }

    private void diffMap(
        Map<String, DefaultPropertyEntry> map,
        Map<String, DefaultPropertyEntry> newMap,
        Map<String, Object> changes) {
        Map<String, DefaultPropertyEntry> remainingMap = new LinkedHashMap<>(map);
        for (Map.Entry<String, DefaultPropertyEntry> entry : newMap.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue().value();
            if (!map.containsKey(key)) {
                changes.put(key, newValue);
            } else {
                Object oldValue = map.getOrDefault(key, PropertySourcePropertyResolver.NULL_ENTRY).value();
                boolean hasNew = newValue != null;
                boolean hasOld = oldValue != null;
                if (hasNew && !hasOld) {
                    changes.put(key, null);
                } else if (hasOld && !hasNew) {
                    changes.put(key, oldValue);
                } else if (hasNew && hasOld && hasChanged(newValue, oldValue)) {
                    changes.put(key, oldValue);
                }
                remainingMap.remove(key);
            }
        }
        remainingMap.forEach((key, value) -> {
            changes.put(key, value.value());
        });
    }

    private static boolean hasChanged(Object newValue, Object oldValue) {
        return !Objects.deepEquals(newValue, oldValue);
    }

    private Map<String, DefaultPropertyEntry>[] copyCatalog(Map<String, DefaultPropertyEntry>[] catalog) {
        Map<String, DefaultPropertyEntry>[] newCatalog = new Map[catalog.length];
        for (int i = 0; i < catalog.length; i++) {
            Map<String, DefaultPropertyEntry> entry = catalog[i];
            if (entry != null) {
                newCatalog[i] = new LinkedHashMap<>(entry);
            }
        }
        return newCatalog;
    }

    /**
     * Add a {@link PropertySource} to this resolver.
     *
     * @param propertySource The {@link PropertySource} to add
     * @return This {@link PropertySourcePropertyResolver}
     */
    public PropertySourcePropertyResolver addPropertySource(@Nullable PropertySource propertySource) {
        if (propertySource != null) {
            processPropertySource(propertySource, propertySource.getConvention());
        }
        return this;
    }

    /**
     * Add a property source for the given map.
     *
     * @param name   The name of the property source
     * @param values The values
     * @return This environment
     */
    public PropertySourcePropertyResolver addPropertySource(String name, @Nullable Map<String, ? super Object> values) {
        if (CollectionUtils.isNotEmpty(values)) {
            return addPropertySource(PropertySource.of(name, values));
        }
        return this;
    }

    @Override
    public boolean containsProperty(@Nullable String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        Boolean result = containsCache.get(name);
        if (result == null) {
            for (PropertyCatalog convention : CONVENTIONS) {
                Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, convention);
                if (entries != null) {
                    if (entries.containsKey(name)) {
                        result = true;
                        break;
                    }
                }
            }
            if (result == null) {
                result = false;
            }
            containsCache.put(name, result);
        }
        return result;
    }

    @Override
    public boolean containsProperties(@Nullable String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        for (PropertyCatalog propertyCatalog : CONVENTIONS) {
            Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, propertyCatalog);
            if (entries != null) {
                if (entries.containsKey(name)) {
                    return true;
                } else {
                    String finalName = name + ".";
                    for (String key : entries.keySet()) {
                        if (key.startsWith(finalName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Collection<String> getPropertyEntries(@NonNull String name) {
        return getPropertyEntries(name, io.micronaut.core.value.PropertyCatalog.NORMALIZED);
    }

    @NonNull
    @Override
    public Collection<String> getPropertyEntries(@NonNull String name, @NonNull io.micronaut.core.value.PropertyCatalog propertyCatalog) {
        if (StringUtils.isEmpty(name)) {
            return Collections.emptySet();
        }
        Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, PropertyCatalog.valueOf(propertyCatalog.name()));
        if (entries == null) {
            return Collections.emptySet();
        }
        String prefix = name + '.';
        Set<String> strings = entries.keySet();
        Set<String> result = CollectionUtils.newHashSet(strings.size());
        for (String k : strings) {
            if (k.startsWith(prefix)) {
                String withoutPrefix = k.substring(prefix.length());
                int i = withoutPrefix.indexOf('.');
                String s;
                if (i > -1) {
                    s = withoutPrefix.substring(0, i);
                } else {
                    s = withoutPrefix;
                }
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public Set<List<String>> getPropertyPathMatches(String pathPattern) {
        if (StringUtils.isEmpty(pathPattern)) {
            return Collections.emptySet();
        }
        Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(pathPattern, false, null);
        if (entries == null) {
            return Collections.emptySet();
        }
        boolean endsWithWildCard = pathPattern.endsWith(WILD_CARD_SUFFIX);
        String resolvedPattern = pathPattern
            .replace("[*]", "\\[([\\w\\d-]+?)\\]")
            .replace(".*.", "\\.([\\w\\d-]+?)\\.");
        if (endsWithWildCard) {
            resolvedPattern = resolvedPattern.replace(WILD_CARD_SUFFIX, "\\S*");
        } else {
            resolvedPattern += "\\S*";
        }
        Pattern pattern = Pattern.compile(resolvedPattern);
        Set<String> keys = entries.keySet();
        Set<List<String>> results = CollectionUtils.newHashSet(keys.size());
        for (String key : keys) {
            Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                int i = matcher.groupCount();
                if (i > 0) {
                    if (i == 1) {
                        results.add(Collections.singletonList(matcher.group(1)));
                    } else {
                        List<String> resolved = new ArrayList<>(i);
                        for (int j = 0; j < i; j++) {
                            resolved.add(matcher.group(j + 1));
                        }
                        results.add(CollectionUtils.unmodifiableList(resolved));
                    }
                }
            }
        }
        return Collections.unmodifiableSet(results);
    }

    @Override
    public @NonNull Map<String, Object> getProperties(String name, StringConvention keyFormat) {
        if (StringUtils.isEmpty(name)) {
            return Collections.emptyMap();
        }
        Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, keyFormat == StringConvention.RAW ? PropertyCatalog.RAW : PropertyCatalog.GENERATED);
        if (entries != null) {
            if (keyFormat == null) {
                keyFormat = StringConvention.RAW;
            }
            return resolveSubMap(
                    name,
                    entries,
                    ConversionContext.MAP,
                    keyFormat,
                    MapFormat.MapTransformation.FLAT
            );
        } else {
            entries = resolveEntriesForKey(name, false, PropertyCatalog.GENERATED);
            if (keyFormat == null) {
                keyFormat = StringConvention.RAW;
            }
            if (entries == null) {
                return Collections.emptyMap();
            }
            return resolveSubMap(
                    name,
                    entries,
                    ConversionContext.MAP,
                    keyFormat,
                    MapFormat.MapTransformation.FLAT
            );
        }
    }

    @Override
    public <T> Optional<T> getProperty(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        Objects.requireNonNull(conversionContext, "Conversion context should not be null");
        Class<T> requiredType = conversionContext.getArgument().getType();
        boolean cacheableType = ClassUtils.isJavaLangType(requiredType);
        ConversionCacheKey cacheKey = new ConversionCacheKey(name, requiredType);
        Object cached = cacheableType ? resolvedValueCache.get(cacheKey) : null;
        if (cached != null) {
            return cached == NO_VALUE ? Optional.empty() : Optional.of((T) cached);
        }
        Object value = placeholderResolutionCache.get(name);
        // entries map to get the value from, only populated if there's a cache miss with placeholderResolutionCache
        Map<String, DefaultPropertyEntry> entries = null;
        if (value == null) {
            entries = resolveEntriesForKey(name, false, PropertyCatalog.GENERATED);
            if (entries == null) {
                entries = resolveEntriesForKey(name, false, PropertyCatalog.RAW);
            }
        }
        if (entries != null || value != null) {
            if (value == null) {
                value = entries.getOrDefault(name, NULL_ENTRY).value();
            }
            if (value == null) {
                value = entries.getOrDefault(normalizeName(name), NULL_ENTRY).value();
                if (value == null && name.indexOf('[') == -1) {
                    // last chance lookup the raw value
                    Map<String, DefaultPropertyEntry> rawEntries = resolveEntriesForKey(name, false, PropertyCatalog.RAW);
                    value = rawEntries != null ? rawEntries.getOrDefault(name, NULL_ENTRY).value() : null;
                    if (value != null) {
                        entries = rawEntries;
                    }
                }
            }
            if (value == null) {
                int i = name.indexOf('[');
                if (i > -1 && name.endsWith("]")) {
                    String newKey = name.substring(0, i);
                    value = entries.getOrDefault(newKey, NULL_ENTRY).value();
                    String index = name.substring(i + 1, name.length() - 1);
                    if (StringUtils.isNotEmpty(index)) {
                        if (value != null) {
                            if (value instanceof List<?> list) {
                                try {
                                    value = list.get(Integer.parseInt(index));
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            } else if (value instanceof Map<?, ?> map) {
                                try {
                                    value = map.get(index);
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                        } else {
                            String subKey = newKey + '.' + index;
                            value = entries.getOrDefault(subKey, NULL_ENTRY).value();
                        }
                    }
                }
            }

            if (value != null) {
                Optional<T> converted;
                if (entries != null) {
                    // iff entries is null, the value is from placeholderResolutionCache and doesn't need this step
                    value = resolvePlaceHoldersIfNecessary(value);
                    placeholderResolutionCache.put(name, value);
                }
                if (requiredType.isInstance(value) && !CollectionUtils.isIterableOrMap(requiredType)) {
                    converted = (Optional<T>) Optional.of(value);
                } else {
                    converted = conversionService.convert(value, conversionContext);
                }

                if (log.isTraceEnabled()) {
                    if (converted.isPresent()) {
                        log.trace("Resolved value [{}] for property: {}", converted.get(), name);
                    } else {
                        log.trace("Resolved value [{}] cannot be converted to type [{}] for property: {}", value, conversionContext.getArgument(), name);
                    }
                }

                if (cacheableType) {
                    resolvedValueCache.put(cacheKey, converted.orElse((T) NO_VALUE));
                }
                return converted;
            } else if (cacheableType) {
                resolvedValueCache.put(cacheKey, NO_VALUE);
                return Optional.empty();
            } else if (Properties.class.isAssignableFrom(requiredType)) {
                Properties properties = resolveSubProperties(name, entries, conversionContext);
                return Optional.of((T) properties);
            } else if (Map.class.isAssignableFrom(requiredType)) {
                Map<String, Object> subMap = resolveSubMap(name, entries, conversionContext);
                if (!subMap.isEmpty()) {
                    return conversionService.convert(subMap, Map.class, requiredType, conversionContext);
                } else {
                    return (Optional<T>) Optional.of(subMap);
                }
            } else if (PropertyResolver.class.isAssignableFrom(requiredType)) {
                Map<String, Object> subMap = resolveSubMap(name, entries, conversionContext);
                return Optional.of((T) new MapPropertyResolver(subMap, conversionService));
            }
        }

        log.trace("No value found for property: {}", name);

        if (Properties.class.isAssignableFrom(requiredType)) {
            return Optional.of((T) new Properties());
        } else if (Map.class.isAssignableFrom(requiredType)) {
            return Optional.of((T) Collections.emptyMap());
        }
        return Optional.empty();
    }

    /**
     * Returns a combined Map of all properties in the catalog.
     *
     * @param keyConvention  The map key convention
     * @param transformation The map format
     * @return Map of all properties
     */
    public Map<String, Object> getAllProperties(StringConvention keyConvention, MapFormat.MapTransformation transformation) {
        Map<String, Object> map = new HashMap<>();
        boolean isNested = transformation == MapFormat.MapTransformation.NESTED;
        Arrays
            .stream(getCatalog(keyConvention == StringConvention.RAW ? PropertyCatalog.RAW : PropertyCatalog.GENERATED))
            .filter(Objects::nonNull)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .forEach((Map.Entry<String, DefaultPropertyEntry> entry) -> {
                String k = keyConvention.format(entry.getKey());
                Object value = resolvePlaceHoldersIfNecessary(entry.getValue().value());
                Map finalMap = map;
                int index = k.indexOf('.');
                if (index != -1 && isNested) {
                    String[] keys = DOT_PATTERN.split(k);
                    for (int i = 0; i < keys.length - 1; i++) {
                        if (!finalMap.containsKey(keys[i])) {
                            finalMap.put(keys[i], new HashMap<>());
                        }
                        Object next = finalMap.get(keys[i]);
                        if (next instanceof Map theMap) {
                            finalMap = theMap;
                        }
                    }
                    finalMap.put(keys[keys.length - 1], value);
                } else {
                    finalMap.put(k, value);
                }
            });

        return map;
    }

    /**
     * @param name              The property name
     * @param entries           The entries
     * @param conversionContext The conversion context
     * @return The subproperties
     */
    protected Properties resolveSubProperties(String name, Map<String, DefaultPropertyEntry> entries, ArgumentConversionContext<?> conversionContext) {
        // special handling for maps for resolving sub keys
        Properties properties = new Properties();
        AnnotationMetadata annotationMetadata = conversionContext.getAnnotationMetadata();
        StringConvention keyConvention = annotationMetadata.enumValue(MapFormat.class, "keyFormat", StringConvention.class)
                                                           .orElse(null);
        if (keyConvention == StringConvention.RAW) {
            entries = resolveEntriesForKey(name, false, PropertyCatalog.RAW);
        }
        String prefix = name + '.';
        entries.entrySet().stream()
            .filter(map -> map.getKey().startsWith(prefix))
            .forEach(entry -> {
                DefaultPropertyEntry propertyEntry = entry.getValue();
                Object value = propertyEntry.value();
                if (value != null) {
                    String key = entry.getKey().substring(prefix.length());
                    key = keyConvention != null ? keyConvention.format(key) : key;
                    properties.put(key, resolvePlaceHoldersIfNecessary(value.toString()));
                }
            });

        return properties;
    }

    /**
     * @param name              The property name
     * @param entries           The entries
     * @param conversionContext The conversion context
     * @return The submap
     */
    protected Map<String, Object> resolveSubMap(String name, Map<String, DefaultPropertyEntry> entries, ArgumentConversionContext<?> conversionContext) {
        // special handling for maps for resolving sub keys
        AnnotationMetadata annotationMetadata = conversionContext.getAnnotationMetadata();
        StringConvention keyConvention = annotationMetadata.enumValue(MapFormat.class, "keyFormat", StringConvention.class).orElse(null);
        if (keyConvention == StringConvention.RAW) {
            entries = resolveEntriesForKey(name, false, PropertyCatalog.RAW);
        }
        MapFormat.MapTransformation transformation = annotationMetadata.enumValue(
                MapFormat.class,
                "transformation",
                MapFormat.MapTransformation.class)
                .orElse(MapFormat.MapTransformation.NESTED);
        return resolveSubMap(name, entries, conversionContext, keyConvention, transformation);
    }

    /**
     * Resolves a submap for the given name and parameters.
     *
     * @param name The name
     * @param entries The entries
     * @param conversionContext The conversion context
     * @param keyConvention The key convention to use
     * @param transformation The map transformation to apply
     * @return The resulting map
     */
    @NonNull
    protected Map<String, Object> resolveSubMap(
            String name,
            Map<String, DefaultPropertyEntry> entries,
            ArgumentConversionContext<?> conversionContext,
            @Nullable StringConvention keyConvention,
            MapFormat.MapTransformation transformation) {
        final Argument<?> valueType = conversionContext.getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
        boolean valueTypeIsList = List.class.isAssignableFrom(valueType.getType());
        Map<String, Object> subMap = CollectionUtils.newLinkedHashMap(entries.size());

        String prefix = name + '.';
        for (Map.Entry<String, DefaultPropertyEntry> entry : entries.entrySet()) {
            final String key = entry.getKey();

            if (valueTypeIsList && key.contains("[") && key.endsWith("]")) {
                continue;
            }

            if (key.startsWith(prefix)) {
                String subMapKey = key.substring(prefix.length());

                Object value = resolvePlaceHoldersIfNecessary(entry.getValue().value());

                if (transformation == MapFormat.MapTransformation.FLAT) {
                    subMapKey = keyConvention != null ? keyConvention.format(subMapKey) : subMapKey;
                    value = conversionService.convert(value, valueType).orElse(null);
                    subMap.put(subMapKey, value);
                } else {
                    processSubmapKey(
                            subMap,
                            subMapKey,
                            value,
                            keyConvention
                    );
                }
            }
        }
        return subMap;
    }

    /**
     * @param properties The property source
     * @param convention The property convention
     */
    @SuppressWarnings("MagicNumber")
    protected void processPropertySource(PropertySource properties, PropertySource.PropertyConvention convention) {
        synchronized (catalog) {
            for (String property : properties) {

                log.trace("Processing property key {}", property);

                Object value = properties.get(property);

                List<String> resolvedProperties = resolvePropertiesForConvention(property, convention);
                boolean first = true;
                for (String resolvedProperty : resolvedProperties) {
                    int i = resolvedProperty.indexOf('[');
                    if (i > -1) {
                        String propertyName = resolvedProperty.substring(0, i);
                        Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(propertyName, true, PropertyCatalog.GENERATED);
                        if (entries != null) {
                            entries.put(resolvedProperty, new DefaultPropertyEntry(
                                resolvedProperty,
                                value,
                                property,
                                properties.getOrigin()
                            ));
                            expandProperty(
                                resolvedProperty.substring(i),
                                val -> entries.put(propertyName, new DefaultPropertyEntry(
                                    propertyName,
                                    val,
                                    property,
                                    properties.getOrigin()
                                )),
                                () -> entries.getOrDefault(propertyName, NULL_ENTRY).value(),
                                value
                            );
                        }
                        if (first) {
                            Map<String, DefaultPropertyEntry> normalized = resolveEntriesForKey(resolvedProperty, true, PropertyCatalog.NORMALIZED);
                            if (normalized != null) {
                                normalized.put(propertyName, new DefaultPropertyEntry(
                                    propertyName,
                                    value,
                                    property,
                                    properties.getOrigin()
                                ));
                            }
                            first = false;
                        }
                    } else {
                        Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(resolvedProperty, true, PropertyCatalog.GENERATED);
                        if (entries != null) {
                            if (value instanceof List || value instanceof Map) {
                                collapseProperty(property, resolvedProperty, entries, value, properties.getOrigin());
                            }
                            entries.put(resolvedProperty, new DefaultPropertyEntry(
                                resolvedProperty,
                                value,
                                property,
                                properties.getOrigin()
                            ));
                        }
                        if (first) {
                            Map<String, DefaultPropertyEntry> normalized = resolveEntriesForKey(resolvedProperty, true, PropertyCatalog.NORMALIZED);
                            if (normalized != null) {
                                normalized.put(resolvedProperty, new DefaultPropertyEntry(
                                    resolvedProperty,
                                    value,
                                    property,
                                    properties.getOrigin()
                                ));
                            }
                            first = false;
                        }
                    }
                }

                final Map<String, DefaultPropertyEntry> rawEntries = resolveEntriesForKey(property, true, PropertyCatalog.RAW);
                if (rawEntries != null) {
                    rawEntries.put(property, new DefaultPropertyEntry(
                        property,
                        value,
                        property,
                        properties.getOrigin()
                    ));
                }
            }
        }
    }

    private void expandProperty(String property, Consumer<Object> containerSet, Supplier<Object> containerGet, Object actualValue) {
        if (StringUtils.isEmpty(property)) {
            containerSet.accept(actualValue);
            return;
        }
        int i = property.indexOf('[');
        int li = property.indexOf(']');
        if (i == 0 && li > -1) {
            String propertyIndex = property.substring(1, li);
            String propertyRest = property.substring(li + 1);
            Object container = containerGet.get();
            if (StringUtils.isDigits(propertyIndex)) {
                int number = Integer.parseInt(propertyIndex);
                List list;
                if (container instanceof List<?> theList) {
                    list = theList;
                } else {
                    list = new ArrayList<>(10);
                    containerSet.accept(list);
                }
                fill(list, number, null);

                expandProperty(propertyRest, val -> list.set(number, val), () -> list.get(number), actualValue);
            } else {
                Map map;
                if (container instanceof Map theMap) {
                    map = theMap;
                } else {
                    map = new LinkedHashMap(10);
                    containerSet.accept(map);
                }

                expandProperty(propertyRest, val -> map.put(propertyIndex, val), () -> map.get(propertyIndex), actualValue);
            }
        } else if (property.startsWith(".")) {
            String propertyName;
            String propertyRest;
            if (i > -1) {
                propertyName = property.substring(1, i);
                propertyRest = property.substring(i);
            } else {
                propertyName = property.substring(1);
                propertyRest = "";
            }
            Object v = containerGet.get();
            Map map;
            if (v instanceof Map theMap) {
                map = theMap;
            } else {
                map = new LinkedHashMap(10);
                containerSet.accept(map);
            }
            expandProperty(propertyRest, val -> map.put(propertyName, val), () -> map.get(propertyName), actualValue);
        }
    }

    private void collapseProperty(
        String originalProperty,
        String prefix,
        Map<String, DefaultPropertyEntry> entries,
        Object value,
        PropertySource.Origin origin) {
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item != null) {
                    collapseProperty(originalProperty, prefix + "[" + i + "]", entries, item, origin);
                }
            }
            entries.put(prefix, new DefaultPropertyEntry(
                prefix,
                value,
                originalProperty,
                origin
            ));
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry: map.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof CharSequence charSequence) {
                    collapseProperty(originalProperty, prefix + "." + charSequence, entries, entry.getValue(), origin);
                }
            }
        } else {
            entries.put(prefix, new DefaultPropertyEntry(
                prefix,
                value,
                originalProperty,
                origin
            ));
        }
    }

    /**
     * @param name        The name
     * @param allowCreate Whether allows creation
     * @param propertyCatalog The string convention
     * @return The map with the resolved entries for the name
     */
    @SuppressWarnings("MagicNumber")
    protected Map<String, DefaultPropertyEntry> resolveEntriesForKey(String name, boolean allowCreate, @Nullable PropertyCatalog propertyCatalog) {
        if (name.isEmpty()) {
            return null;
        }
        final Map<String, DefaultPropertyEntry>[] catalog = getCatalog(propertyCatalog);

        Map<String, DefaultPropertyEntry> entries = null;
        char firstChar = name.charAt(0);
        if (Character.isLetter(firstChar)) {
            int index = firstChar - 65;
            if (index < catalog.length && index >= 0) {
                entries = catalog[index];
                if (allowCreate && entries == null) {
                    entries = new LinkedHashMap<>(5);
                    catalog[index] = entries;
                }
            }
        }
        return entries;
    }

    /**
     * Obtain a property catalog.
     * @param propertyCatalog The catalog
     * @return The catalog
     */
    private Map<String, DefaultPropertyEntry>[] getCatalog(@Nullable PropertyCatalog propertyCatalog) {
        propertyCatalog = propertyCatalog != null ? propertyCatalog : PropertyCatalog.GENERATED;
        return switch (propertyCatalog) {
            case RAW -> this.rawCatalog;
            case NORMALIZED -> this.nonGenerated;
            default -> this.catalog;
        };
    }

    /**
     * Subclasses can override to reset caches.
     */
    protected void resetCaches() {
        containsCache.clear();
        resolvedValueCache.clear();
        placeholderResolutionCache.clear();
    }

    private void processSubmapKey(Map<String, Object> map, String key, Object value, @Nullable StringConvention keyConvention) {
        int index = key.indexOf('.');
        final boolean hasKeyConvention = keyConvention != null;
        if (index == -1) {
            key = hasKeyConvention ? keyConvention.format(key) : key;
            map.put(key, value);
        } else {

            String mapKey = key.substring(0, index);
            mapKey = hasKeyConvention ? keyConvention.format(mapKey) : mapKey;
            if (!map.containsKey(mapKey)) {
                map.put(mapKey, new LinkedHashMap<>());
            }
            final Object v = map.get(mapKey);
            if (v instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) v;
                String nestedKey = key.substring(index + 1);
                processSubmapKey(nestedMap, nestedKey, value, keyConvention);
            } else {
                map.put(mapKey, v);
            }
        }
    }

    private String normalizeName(String name) {
        return name.replace('-', '.');
    }

    private Object resolvePlaceHoldersIfNecessary(Object value) {
        if (value instanceof CharSequence) {
            return propertyPlaceholderResolver.resolveRequiredPlaceholdersObject(value.toString());
        } else if (value instanceof List<?> list) {
            List<?> newList = new ArrayList<>(list);
            final ListIterator i = newList.listIterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (o instanceof CharSequence) {
                    i.set(resolvePlaceHoldersIfNecessary(o));
                } else if (o instanceof Map<?,?> submap) {
                    Map<Object, Object> newMap = CollectionUtils.newLinkedHashMap(submap.size());
                    for (Map.Entry<?, ?> entry : submap.entrySet()) {
                        final Object k = entry.getKey();
                        final Object v = entry.getValue();
                        newMap.put(k, resolvePlaceHoldersIfNecessary(v));
                    }
                    i.set(newMap);
                }
            }
            value = newList;
        }
        return value;
    }

    private List<String> resolvePropertiesForConvention(String property, PropertySource.PropertyConvention convention) {
        if (convention == PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE) {
            return environmentProperties.findPropertyNamesForEnvironmentVariable(property);
        }
        return Collections.singletonList(
                NameUtils.hyphenate(property, true)
        );
    }

    private void fill(List list, int toIndex, Object value) {
        if (toIndex >= list.size()) {
            for (int i = list.size(); i <= toIndex; i++) {
                list.add(i, value);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (propertyPlaceholderResolver instanceof AutoCloseable autoCloseable) {
            autoCloseable.close();
        }
    }

    private record ConversionCacheKey(@NonNull String name, Class<?> requiredType) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null) {
                return false;
            }
            ConversionCacheKey that = (ConversionCacheKey) o;
            return Objects.equals(name, that.name) && Objects.equals(requiredType, that.requiredType);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
