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
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
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
import java.util.HashSet;
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
@NullUnmarked
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
    private final Logger log;

    private final Object catalogLock = new Object();
    /**
     * The catalogs and the caches derived from them, as lookups see them. Replaced as a whole, so a
     * lookup that reads this field observes a state that is never partially built.
     */
    @SuppressWarnings("java:S3077") // ResolverState is only ever published fully initialized
    private volatile ResolverState state = new ResolverState();
    /**
     * The state that catalog writes go into. The same instance as {@link #state}, except while a
     * refresh builds a replacement that is not published yet. Guarded by {@link #catalogLock}.
     */
    private ResolverState writeState = state;
    /**
     * Whether {@link #refresh(Runnable)} is building a replacement state. Guarded by
     * {@link #catalogLock}.
     */
    private boolean refreshing;
    private final EnvironmentProperties environmentProperties = EnvironmentProperties.fork(CURRENT_ENV);

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver} for the given {@link ConversionService}.
     *
     * @param conversionService The {@link ConversionService}
     * @param logEnabled        flag to enable or disable logger
     */
    public PropertySourcePropertyResolver(ConversionService conversionService, boolean logEnabled) {
        this(conversionService, logEnabled, null);
    }

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver} for the given {@link ConversionService}.
     *
     * @param conversionService The {@link ConversionService}
     * @param logEnabled        flag to enable or disable logger
     * @param classLoader       The class loader to use for expression resolver service loading
     */
    public PropertySourcePropertyResolver(ConversionService conversionService, boolean logEnabled, @Nullable ClassLoader classLoader) {
        this.log = logEnabled ? LoggerFactory.getLogger(getClass()) : NOPLogger.NOP_LOGGER;
        this.conversionService = conversionService;
        this.propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(this, conversionService, classLoader);
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
    public PropertySourcePropertyResolver(PropertySource @Nullable ... propertySources) {
        this(ConversionService.SHARED);
        if (propertySources != null) {
            for (PropertySource propertySource : propertySources) {
                addPropertySource(propertySource);
            }
        }
    }

    void reset() {
        synchronized (catalogLock) {
            ResolverState empty = new ResolverState();
            writeState = empty;
            if (!refreshing) {
                state = empty;
            }
        }
    }

    /**
     * Rebuilds the catalogs. The writes performed by {@code rebuild} go into a replacement state
     * while lookups keep reading the current one, and the replacement is published in a single
     * step once {@code rebuild} completes. A concurrent lookup therefore observes the properties
     * either as they were before the rebuild or as they are after it, but never a half-built
     * catalog. If {@code rebuild} throws, the replacement is discarded and the current state stays
     * published.
     *
     * @param rebuild The rebuild to run
     */
    void refresh(Runnable rebuild) {
        synchronized (catalogLock) {
            if (refreshing) {
                // Already inside a refresh, its outermost call publishes.
                rebuild.run();
                return;
            }
            refreshing = true;
            try {
                rebuild.run();
                state = writeState;
            } finally {
                refreshing = false;
                writeState = state;
            }
        }
    }

    public Map<String, Object> diff(Runnable change) {
        Map<String, DefaultPropertyEntry>[] copiedCatalog = copyCatalog(state.catalog);
        change.run();
        return diffCatalog(copiedCatalog, state.catalog);
    }


    private Map<String, Object> diffCatalog(@Nullable Map<String, DefaultPropertyEntry>[] original, @Nullable Map<String, DefaultPropertyEntry>[] newCatalog) {
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

    private @Nullable Map<String, DefaultPropertyEntry>[] copyCatalog(@Nullable Map<String, DefaultPropertyEntry>[] catalog) {
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
        ResolverState currentState = state;
        Boolean result = currentState.containsCache.get(name);
        if (result == null) {
            for (PropertyCatalog convention : CONVENTIONS) {
                Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(currentState, name, convention);
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
            currentState.containsCache.put(name, result);
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

    @Override
    public Collection<String> getPropertyEntries(String name) {
        return getPropertyEntries(name, PropertyCatalog.NORMALIZED);
    }

    @Override
    public Collection<String> getPropertyEntries(String name, PropertyCatalog propertyCatalog) {
        if (StringUtils.isEmpty(name)) {
            return Collections.emptySet();
        }
        Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, propertyCatalog);
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
    public Map<String, Object> getProperties(@Nullable String name, @Nullable StringConvention keyFormat) {
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
    public <T> Optional<T> getProperty(String name, ArgumentConversionContext<T> conversionContext) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        Objects.requireNonNull(conversionContext, "Conversion context should not be null");
        Class<T> requiredType = conversionContext.getArgument().getType();
        boolean cacheableType = ClassUtils.isJavaLangType(requiredType);
        ConversionCacheKey cacheKey = new ConversionCacheKey(name, requiredType);
        // A single read of the state, so the catalogs and the caches this lookup consults cannot be
        // replaced underneath it by a concurrent refresh.
        ResolverState currentState = state;
        Object cached = cacheableType ? currentState.resolvedValueCache.get(cacheKey) : null;
        if (cached != null) {
            return cached == NO_VALUE ? Optional.empty() : Optional.of((T) cached);
        }
        Object value = currentState.placeholderResolutionCache.get(name);
        // entries map to get the value from, only populated if there's a cache miss with placeholderResolutionCache
        Map<String, DefaultPropertyEntry> entries = null;
        if (value == null) {
            entries = resolveEntriesForKey(currentState, name, PropertyCatalog.GENERATED);
            if (entries == null) {
                entries = resolveEntriesForKey(currentState, name, PropertyCatalog.RAW);
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
                    Map<String, DefaultPropertyEntry> rawEntries = resolveEntriesForKey(currentState, name, PropertyCatalog.RAW);
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
                    currentState.placeholderResolutionCache.put(name, value);
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
                    currentState.resolvedValueCache.put(cacheKey, converted.orElse((T) NO_VALUE));
                }
                return converted;
            } else if (cacheableType) {
                currentState.resolvedValueCache.put(cacheKey, NO_VALUE);
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
            } else if (isMapConvertible(requiredType)) {
                Map<String, Object> subMap = resolveSubMap(name, entries, conversionContext);
                if (subMap.isEmpty()) {
                    return Optional.empty();
                }
                return conversionService.convert(subMap, Map.class, requiredType, conversionContext);
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
     * Whether the given type is a map-like type that sub-properties can be resolved into.
     *
     * <p>A converter is only considered when it is registered for a {@link Map} source. Converters
     * that are merely reachable through the generic {@link Object} source (for example
     * {@code Object -> List} or {@code Object -> String}) must not divert a property lookup into
     * sub-map resolution, as that would change the meaning of unrelated {@code @Value} injection
     * points.</p>
     *
     * @param requiredType The required type
     * @return True if sub-properties should be resolved and converted into the required type
     */
    private boolean isMapConvertible(Class<?> requiredType) {
        return conversionService.canConvert(Map.class, requiredType)
            && !conversionService.canConvert(Object.class, requiredType);
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
            .stream(getCatalog(state, keyConvention == StringConvention.RAW ? PropertyCatalog.RAW : PropertyCatalog.GENERATED))
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
        synchronized (catalogLock) {
            for (String property : properties) {

                log.trace("Processing property key {}", property);

                Object value = properties.get(property);

                populateRawCatalog(property, value, convention, properties.getOrigin());

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
                            expandIndexedProperty(
                                entries,
                                propertyName,
                                resolvedProperty.substring(i),
                                value,
                                property,
                                properties.getOrigin()
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

            }
            // A lookup can cache a miss between reset() and catalog reinitialization.
            // Clear those entries after the rebuilt catalog becomes visible.
            resetCaches();
        }
    }

    /**
     * Populates the RAW catalog for a single property: the verbatim entry under its own key, plus,
     * for an indexed key, the expanded aggregate under the verbatim base name. Aggregating in RAW
     * as well as in GENERATED is what preserves the spelling of a map key nested under an indexed
     * segment, since GENERATED sees only the hyphenated key.
     *
     * <p>Skipped for {@link PropertySource.PropertyConvention#ENVIRONMENT_VARIABLE}, which has no
     * original spelling left to preserve: {@code EnvironmentPropertySource.getEnv} rewrites
     * {@code A_0__B} into the verbatim key {@code A[0]_B}, and expanding that verbatim would
     * misread the trailing {@code _B} as part of the map key rather than as a property
     * delimiter.</p>
     *
     * @param property The verbatim property key
     * @param value The property value
     * @param convention The property convention
     * @param origin The origin of the property source
     */
    private void populateRawCatalog(
        String property,
        Object value,
        PropertySource.PropertyConvention convention,
        PropertySource.Origin origin) {

        int bracket = property.indexOf('[');

        Map<String, DefaultPropertyEntry> rawEntries = resolveEntriesForKey(property, true, PropertyCatalog.RAW);
        if (rawEntries != null) {
            if (bracket < 0) {
                // Only a key without an index can be an aggregate base. The value goes in by
                // reference, so RAW no longer owns whatever it held under this name; the next
                // indexed key targeting it takes its own copy below.
                writeState.rawOwnedBases.remove(property);
            }
            rawEntries.put(property, new DefaultPropertyEntry(
                property,
                value,
                property,
                origin
            ));
        }

        if (bracket <= 0 || convention == PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE) {
            return;
        }
        String baseName = property.substring(0, bracket);
        Map<String, DefaultPropertyEntry> baseEntries = resolveEntriesForKey(baseName, true, PropertyCatalog.RAW);
        if (baseEntries == null) {
            return;
        }

        // The expansion below mutates the aggregate in place, so RAW has to own it. It does not
        // yet if a bare key last stored it by reference, so take a copy on the first indexed key
        // to target this base since then. This must happen before the GENERATED expansion for the
        // same key, which mutates its own aggregate in place and may share that very instance:
        // copied afterwards, RAW would inherit GENERATED's hyphenated spelling.
        if (writeState.rawOwnedBases.add(baseName)) {
            DefaultPropertyEntry existing = baseEntries.get(baseName);
            if (existing != null) {
                baseEntries.put(baseName, new DefaultPropertyEntry(
                    baseName,
                    deepCopyForRawExpansion(existing.value()),
                    existing.raw(),
                    existing.origin()
                ));
            }
        }

        // expandProperty stores this value into the aggregate by reference, so a container that
        // GENERATED can reach too would be mutated by a later key drilling into the same index.
        // Scalars, which are the overwhelming majority, pass through untouched.
        expandIndexedProperty(
            baseEntries,
            baseName,
            property.substring(bracket),
            deepCopyForRawExpansion(value),
            property,
            origin
        );
    }

    /**
     * Recursively duplicates the {@link List} and {@link Map} spine of a value, so that RAW can
     * expand into its own copy without mutating the property source's value or the instance the
     * GENERATED catalog serves.
     *
     * <p>Every other value is shared rather than copied, which is safe because expansion only ever
     * mutates a {@code List} by index or a {@code Map} by key: an array, a {@code Set} or a leaf
     * is never written through. Note that a copied {@code Map} becomes a {@link LinkedHashMap}, so
     * a source supplying a sorted or case-insensitive map keeps its iteration order at the point
     * of copying but not its behaviour for keys added afterwards.</p>
     *
     * @param value The value to copy
     * @return An equivalent value whose containers are independently mutable
     */
    private static Object deepCopyForRawExpansion(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepCopyForRawExpansion(element));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), deepCopyForRawExpansion(entry.getValue()));
            }
            return copy;
        }
        return value;
    }

    /**
     * Expands an indexed property (e.g. {@code foo[0].bar}) into the given entries map under its
     * base name, building the container value and writing it back to {@code entries} as it grows.
     *
     * @param entries The catalog entries map to read the existing container from and write the
     *                expanded container to, keyed by {@code baseName}
     * @param baseName The un-indexed base property name (e.g. {@code foo})
     * @param indexSuffix The indexed remainder of the property, starting with {@code [} (e.g. {@code [0].bar})
     * @param value The value to place at the indexed location
     * @param originalProperty The original, unresolved property key, recorded on the entry
     * @param origin The origin of the property source, recorded on the entry
     */
    private void expandIndexedProperty(
        Map<String, DefaultPropertyEntry> entries,
        String baseName,
        String indexSuffix,
        Object value,
        String originalProperty,
        PropertySource.Origin origin) {
        expandProperty(
            indexSuffix,
            val -> entries.put(baseName, new DefaultPropertyEntry(
                baseName,
                val,
                originalProperty,
                origin
            )),
            () -> entries.getOrDefault(baseName, NULL_ENTRY).value(),
            value
        );
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
    @Nullable
    protected final Map<String, DefaultPropertyEntry> resolveEntriesForKey(String name, boolean allowCreate, @Nullable PropertyCatalog propertyCatalog) {
        if (allowCreate) {
            // Writes go to the state being built, which is the published one outside of a refresh.
            // Callers passing `true` must hold the catalog lock, as every write path here does.
            synchronized (catalogLock) {
                return resolveEntriesForKey(writeState, name, true, propertyCatalog);
            }
        }
        return resolveEntriesForKey(state, name, propertyCatalog);
    }

    @Nullable
    private Map<String, DefaultPropertyEntry> resolveEntriesForKey(ResolverState resolverState, String name, @Nullable PropertyCatalog propertyCatalog) {
        return resolveEntriesForKey(resolverState, name, false, propertyCatalog);
    }

    @SuppressWarnings("MagicNumber")
    @Nullable
    private Map<String, DefaultPropertyEntry> resolveEntriesForKey(ResolverState resolverState, String name, boolean allowCreate, @Nullable PropertyCatalog propertyCatalog) {
        if (name.isEmpty()) {
            return null;
        }
        char firstChar = name.charAt(0);
        if (Character.isLetter(firstChar)) {
            final Map<String, DefaultPropertyEntry>[] catalog = getCatalog(resolverState, propertyCatalog);
            int index = firstChar - 65;
            if (index < catalog.length && index >= 0) {
                Map<String, DefaultPropertyEntry> entries = catalog[index];
                if (allowCreate && entries == null) {
                    entries = new LinkedHashMap<>(5);
                    catalog[index] = entries;
                }
                return entries;
            }
        }
        return null;
    }

    /**
     * Obtain a property catalog.
     * @param resolverState The state to read the catalog from
     * @param propertyCatalog The catalog
     * @return The catalog
     */
    private Map<String, DefaultPropertyEntry>[] getCatalog(ResolverState resolverState, @Nullable PropertyCatalog propertyCatalog) {
        propertyCatalog = propertyCatalog != null ? propertyCatalog : PropertyCatalog.GENERATED;
        return switch (propertyCatalog) {
            case RAW -> resolverState.rawCatalog;
            case NORMALIZED -> resolverState.nonGenerated;
            default -> resolverState.catalog;
        };
    }

    /**
     * Subclasses can override to reset caches.
     */
    protected void resetCaches() {
        ResolverState currentState = state;
        currentState.containsCache.clear();
        currentState.resolvedValueCache.clear();
        currentState.placeholderResolutionCache.clear();
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

    private void fill(List list, int toIndex, @Nullable Object value) {
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

    /**
     * The mutable state of the resolver: the property catalogs and the caches derived from them.
     * Holding them together means a refresh can build a replacement without touching what lookups
     * are reading, and publish it with a single write to {@link #state}. A lookup racing with that
     * switch keeps reading the state it started from, so a value it computes from the previous
     * catalogs is cached against those catalogs rather than leaking into the new ones.
     */
    private static final class ResolverState {

        // properties are stored in an array of maps organized by character in the alphabet
        // this allows optimization of searches by prefix
        @SuppressWarnings("MagicNumber")
        private static final int CATALOG_SIZE = 58;

        private final @Nullable Map<String, DefaultPropertyEntry>[] catalog = new Map[CATALOG_SIZE];
        private final @Nullable Map<String, DefaultPropertyEntry>[] rawCatalog = new Map[CATALOG_SIZE];
        private final @Nullable Map<String, DefaultPropertyEntry>[] nonGenerated = new Map[CATALOG_SIZE];
        // Base names whose RAW aggregate the RAW catalog owns outright and may therefore expand in
        // place. A bare key stores its value by reference and clears the name; the next indexed key
        // targeting that base copies the value once and records it here. Guarded by `catalogLock`.
        private final Set<String> rawOwnedBases = new HashSet<>();

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
    }

    private record ConversionCacheKey(String name, Class<?> requiredType) {

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
