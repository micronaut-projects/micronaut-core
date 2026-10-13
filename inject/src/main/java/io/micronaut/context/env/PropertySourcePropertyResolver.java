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
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String RANDOM_EXPRESSION = "random.";
    protected final ConversionService conversionService;
    protected final PropertyPlaceholderResolver propertyPlaceholderResolver;
    protected final Map<String, PropertySource> propertySources = new ConcurrentHashMap<>(10);
    // The catalogs that lookups read are held by `state`. These three arrays are kept for binary
    // compatibility with subclasses: they are no longer read by this class and mirror the published
    // catalogs, slot by slot. The `catalog` array is also still the monitor that guards catalog
    // writes, and with them the start, the stop and the refresh of an environment.
    @SuppressWarnings("MagicNumber")
    protected final Map<String, DefaultPropertyEntry>[] catalog = new Map[58];
    protected final Map<String, DefaultPropertyEntry>[] rawCatalog = new Map[58];
    protected final Map<String, DefaultPropertyEntry>[] nonGenerated = new Map[58];

    protected Logger log;

    private final Object catalogLock = catalog;
    /**
     * The catalogs and the caches derived from them, as lookups see them. A refresh replaces it as
     * a whole, so a lookup that reads this field never observes a catalog the refresh has only
     * partially rebuilt.
     */
    @SuppressWarnings("java:S3077") // ResolverState is only ever published fully initialized
    private volatile ResolverState state = new ResolverState();
    /**
     * The state that catalog writes go into. The same instance as {@link #state}, except while a
     * refresh builds a replacement that is not published yet. Guarded by {@link #catalogLock}.
     */
    private ResolverState writeState = state;
    /**
     * The thread {@link #refresh(Runnable)} is building a replacement state on, or
     * {@code null} outside a refresh. Written under {@link #catalogLock}, read without it by every
     * lookup.
     */
    @SuppressWarnings("java:S3077") // a thread reference, not a mutable object
    private volatile Thread refreshingThread;
    /**
     * The state that the lookups of the current thread are kept on while
     * {@link #onState(ResolverState, Supplier)} runs them a second time. What stays behind on a
     * thread afterwards is a holder with an empty slot, and on a thread that made a lookup while
     * another was in a second run an empty entry. The holder is a JDK type, so that a thread that
     * outlives this resolver keeps nothing of it.
     */
    private final ThreadLocal<Object[]> pinnedState = new ThreadLocal<>();
    /**
     * The number of threads whose lookups are kept on a state that way. It is zero unless a
     * refresh has just overtaken a lookup, which lets every other lookup skip the per-thread check.
     */
    private final AtomicInteger pinnedThreads = new AtomicInteger();
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

    /**
     * Drops the properties. Within {@link #refresh(Runnable)} the writes that follow go into
     * an empty replacement state, which lookups on other threads do not see until the refresh
     * publishes it. Outside a refresh the published state is replaced right away and, as stopping
     * the environment always did, only the generated catalog and the caches are dropped: the raw
     * and the normalized catalog stay, so a stopped environment answers what it used to.
     */
    void reset() {
        synchronized (catalogLock) {
            ResolverState replacement = new ResolverState();
            if (refreshingThread == null) {
                ResolverState current = state;
                System.arraycopy(current.rawCatalog, 0, replacement.rawCatalog, 0, replacement.rawCatalog.length);
                System.arraycopy(current.nonGenerated, 0, replacement.nonGenerated, 0, replacement.nonGenerated.length);
                state = replacement;
                mirrorPublishedState();
            }
            writeState = replacement;
        }
    }

    /**
     * The state a lookup reads from. Outside a refresh that is the published state. On the thread
     * that is rebuilding the catalogs it is the replacement being built, so the rebuild sees the
     * properties it has contributed so far: a {@link PropertySource} whose keys depend on what the
     * resolver already holds (micronaut-test-resources contributes only the keys the environment
     * cannot resolve) must be answered from the catalog being built, not from the one the rebuild
     * is replacing. Every other thread keeps reading the published state until the rebuild is
     * published in one step, except for a thread that {@link #onState(ResolverState, Supplier)}
     * keeps on the state a lookup has started with.
     *
     * @return The state to read the catalogs and the caches from
     */
    private ResolverState readState() {
        if (refreshingThread == Thread.currentThread()) {
            // Only the refreshing thread takes this branch, and it holds `catalogLock` for the
            // whole rebuild, so it reads a field that no other thread can be writing.
            return writeState;
        }
        // A plain read: only a thread that has counted itself in has to see the count, and a thread
        // sees what it has written. A stale count on any other thread only decides whether it makes
        // the per-thread check, which finds nothing there.
        if (pinnedThreads.getPlain() != 0) {
            Object[] pinned = pinnedState.get();
            if (pinned != null && pinned[0] != null) {
                return (ResolverState) pinned[0];
            }
        }
        return state;
    }

    /**
     * Runs lookups that come back into this resolver and have to read the given state, the one
     * their caller has read the values from. Resolving a placeholder, for one, looks the properties
     * it names up in this resolver again. Nearly always no refresh is published while the lookups
     * run, so that state is all they have read. If one was published, they may have read what it
     * published as well, and their result, or the failure they ended in, may be that of neither
     * state: they are then run again, with the lookups of the current thread kept on the given
     * state.
     *
     * @param resolverState The state the lookups have to read
     * @param lookups       The lookups, which can be run more than once
     * @param <R>           The result type
     * @return The result of the lookups
     */
    private <R> R onState(ResolverState resolverState, Supplier<R> lookups) {
        try {
            R result = lookups.get();
            if (readState() == resolverState) {
                return result;
            }
        } catch (RuntimeException e) {
            if (readState() == resolverState) {
                throw e;
            }
        }
        Object[] pinned = pinnedState.get();
        if (pinned == null) {
            pinned = new Object[1];
            pinnedState.set(pinned);
        }
        // what a second run that encloses this one keeps the thread on, nothing otherwise
        Object enclosing = pinned[0];
        boolean counted = false;
        try {
            pinnedThreads.incrementAndGet();
            counted = true;
            pinned[0] = resolverState;
            return lookups.get();
        } finally {
            // a plain store first: nothing that can fail may leave the thread on the state
            pinned[0] = enclosing;
            if (counted) {
                pinnedThreads.decrementAndGet();
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
            if (refreshingThread != null) {
                // Already inside a refresh, its outermost call publishes.
                rebuild.run();
                return;
            }
            refreshingThread = Thread.currentThread();
            try {
                rebuild.run();
                state = writeState;
                mirrorPublishedState();
            } finally {
                refreshingThread = null;
                writeState = state;
            }
        }
    }

    /**
     * @return The generated catalog as lookups on other threads currently see it
     */
    Map<String, DefaultPropertyEntry>[] publishedCatalog() {
        return state.catalog;
    }

    /**
     * Copies the published catalogs into the arrays kept for subclasses. Called with
     * {@link #catalogLock} held.
     */
    private void mirrorPublishedState() {
        ResolverState published = state;
        System.arraycopy(published.catalog, 0, catalog, 0, catalog.length);
        System.arraycopy(published.rawCatalog, 0, rawCatalog, 0, rawCatalog.length);
        System.arraycopy(published.nonGenerated, 0, nonGenerated, 0, nonGenerated.length);
    }

    @Override
    public boolean containsProperty(@Nullable String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        ResolverState currentState = readState();
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
        // one state for the entries and for the placeholders that their values hold
        return onState(readState(), () -> resolveProperties(name, keyFormat));
    }

    private Map<String, Object> resolveProperties(String name, StringConvention keyFormat) {
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
        // A single read of the state, so the catalogs and the caches this lookup consults cannot be
        // replaced underneath it by a concurrent refresh.
        ResolverState currentState = readState();
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
                    value = resolvePlaceHolders(currentState, name, value);
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
                Properties properties = resolveSubProperties(currentState, name, entries, conversionContext);
                return Optional.of((T) properties);
            } else if (Map.class.isAssignableFrom(requiredType)) {
                Map<String, Object> subMap = resolveSubMap(currentState, name, entries, conversionContext);
                if (!subMap.isEmpty()) {
                    return conversionService.convert(subMap, Map.class, requiredType, conversionContext);
                } else {
                    return (Optional<T>) Optional.of(subMap);
                }
            } else if (PropertyResolver.class.isAssignableFrom(requiredType)) {
                Map<String, Object> subMap = resolveSubMap(currentState, name, entries, conversionContext);
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
        ResolverState currentState = readState();
        Arrays
            .stream(getCatalog(currentState, keyConvention == StringConvention.RAW ? PropertyCatalog.RAW : PropertyCatalog.GENERATED))
            .filter(Objects::nonNull)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .forEach((Map.Entry<String, DefaultPropertyEntry> entry) -> {
                String k = keyConvention.format(entry.getKey());
                Object value = resolvePlaceHoldersIfNecessary(currentState, entry.getValue().value());
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
        // The state of this thread. A lookup of this class comes here through onState, which makes
        // it the state the entries were read from.
        ResolverState currentState = readState();
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
                    properties.put(key, resolvePlaceHoldersIfNecessary(currentState, value.toString()));
                }
            });

        return properties;
    }

    private Properties resolveSubProperties(ResolverState resolverState, String name, Map<String, DefaultPropertyEntry> entries, ArgumentConversionContext<?> conversionContext) {
        // through the overridable method, which reads the state of the current thread
        return onState(resolverState, () -> resolveSubProperties(name, entries, conversionContext));
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

    private Map<String, Object> resolveSubMap(ResolverState resolverState, String name, Map<String, DefaultPropertyEntry> entries, ArgumentConversionContext<?> conversionContext) {
        // through the overridable method, which reads the state of the current thread
        return onState(resolverState, () -> resolveSubMap(name, entries, conversionContext));
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
        // The state of this thread. A lookup of this class comes here through onState, which makes
        // it the state the entries were read from.
        ResolverState currentState = readState();

        String prefix = name + '.';
        for (Map.Entry<String, DefaultPropertyEntry> entry : entries.entrySet()) {
            final String key = entry.getKey();

            if (valueTypeIsList && key.contains("[") && key.endsWith("]")) {
                continue;
            }

            if (key.startsWith(prefix)) {
                String subMapKey = key.substring(prefix.length());

                Object value = resolvePlaceHoldersIfNecessary(currentState, entry.getValue().value());

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
        this.propertySources.put(properties.getName(), properties);
        synchronized (catalogLock) {
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
            // A lookup can cache a miss between reset() and catalog reinitialization, clear those
            // entries now that the catalog holds this property source. Within a refresh these are
            // the caches of the replacement, filled by the lookups the rebuild itself made, so no
            // answer computed from a half-built catalog is published. What the random texts have
            // resolved to is not a cache of that kind and stays, see resolvePlaceHolders. The caches
            // are cleared directly: resetCaches() is a hook for subclasses, and this runs from the
            // constructor.
            clearLookupCaches(writeState);
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
     * Resolves the entries of the catalog that hold the given name. Note that
     * {@link #getProperty(String, ArgumentConversionContext)} and {@link #containsProperty(String)}
     * read the catalogs and the caches of one state and do not go through this method.
     *
     * @param name        The name
     * @param allowCreate Whether allows creation
     * @param propertyCatalog The string convention
     * @return The map with the resolved entries for the name
     */
    protected Map<String, DefaultPropertyEntry> resolveEntriesForKey(String name, boolean allowCreate, @Nullable PropertyCatalog propertyCatalog) {
        if (allowCreate) {
            // Writes go to the state being built, which is the published one outside of a refresh.
            // The lock guards the read of `writeState`; every write path already holds it, and the
            // lock is reentrant, so this only ever matters to an external caller that does not.
            synchronized (catalogLock) {
                return resolveEntriesForKey(writeState, name, true, propertyCatalog);
            }
        }
        return resolveEntriesForKey(readState(), name, propertyCatalog);
    }

    private Map<String, DefaultPropertyEntry> resolveEntriesForKey(ResolverState resolverState, String name, @Nullable PropertyCatalog propertyCatalog) {
        return resolveEntriesForKey(resolverState, name, false, propertyCatalog);
    }

    @SuppressWarnings("MagicNumber")
    private Map<String, DefaultPropertyEntry> resolveEntriesForKey(ResolverState resolverState, String name, boolean allowCreate, @Nullable PropertyCatalog propertyCatalog) {
        if (name.isEmpty()) {
            return null;
        }
        final Map<String, DefaultPropertyEntry>[] catalog = getCatalog(resolverState, propertyCatalog);

        Map<String, DefaultPropertyEntry> entries = null;
        char firstChar = name.charAt(0);
        if (Character.isLetter(firstChar)) {
            int index = firstChar - 65;
            if (index < catalog.length && index >= 0) {
                entries = catalog[index];
                if (allowCreate && entries == null) {
                    entries = new LinkedHashMap<>(5);
                    catalog[index] = entries;
                    if (resolverState == state) {
                        mirrorPublishedState();
                    }
                }
            }
        }
        return entries;
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
        clearCaches(readState());
    }

    private static void clearCaches(ResolverState resolverState) {
        clearLookupCaches(resolverState);
        resolverState.randomTexts.clear();
    }

    private static void clearLookupCaches(ResolverState resolverState) {
        resolverState.containsCache.clear();
        resolverState.resolvedValueCache.clear();
        resolverState.placeholderResolutionCache.clear();
    }

    /**
     * Resolves the placeholders of the value that a lookup has found for a name.
     *
     * <p>A text made of random expressions alone, such as <code>${random.port}</code>, resolves to
     * something else every time, and the cache that keeps what it has resolved to is cleared
     * after each property source. A property source that reads such a value while the property
     * sources are being processed would be left with one that no later lookup returns. So what
     * the text has resolved to is remembered with the text, and returned again for as long as a
     * lookup by the name finds the same text.</p>
     *
     * <p>Only a text whose every placeholder is a random expression is remembered: resolving it
     * reads no property, so nothing a property source adds can make what it has resolved to out
     * of date. A text that names a property next to a random expression is resolved again by
     * every lookup that does not find it cached, and so are the random expressions in a list or
     * a map.</p>
     *
     * @param resolverState The state the value was read from
     * @param name          The name the value was looked up by
     * @param value         The value
     * @return The value with its placeholders resolved
     */
    private Object resolvePlaceHolders(ResolverState resolverState, String name, Object value) {
        if (!(value instanceof CharSequence)) {
            return resolvePlaceHoldersIfNecessary(resolverState, value);
        }
        String text = value.toString();
        if (!isRandomText(text)) {
            return resolvePlaceHoldersIfNecessary(resolverState, text);
        }
        RandomText remembered = resolverState.randomTexts.get(name);
        if (remembered == null || !remembered.text().equals(text)) {
            Object resolved = resolvePlaceHoldersIfNecessary(resolverState, text);
            // what a lookup of another thread has remembered for the text meanwhile is returned instead
            remembered = resolverState.randomTexts.compute(name, (key, earlier) ->
                earlier != null && earlier.text().equals(text) ? earlier : new RandomText(text, resolved));
        }
        return remembered.resolved();
    }

    /**
     * @param text The text of a value
     * @return Whether it holds placeholders and each of them is a random expression
     */
    private boolean isRandomText(String text) {
        String prefix = propertyPlaceholderResolver.getPrefix();
        int placeholder = text.indexOf(prefix);
        if (placeholder == -1) {
            return false;
        }
        int length = text.length();
        while (placeholder != -1) {
            int expression = placeholder + prefix.length();
            // an expression is trimmed before it is resolved
            while (expression < length && text.charAt(expression) <= ' ') {
                expression++;
            }
            if (!text.regionMatches(true, expression, RANDOM_EXPRESSION, 0, RANDOM_EXPRESSION.length())) {
                return false;
            }
            placeholder = text.indexOf(prefix, expression);
        }
        return true;
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

    /**
     * Resolves the placeholders of a value.
     *
     * @param resolverState The state the value was read from, which the properties the
     *                      placeholders name are read from too
     * @param value         The value
     * @return The value with its placeholders resolved
     */
    private Object resolvePlaceHoldersIfNecessary(ResolverState resolverState, Object value) {
        if (value instanceof CharSequence) {
            String str = value.toString();
            if (str.contains(propertyPlaceholderResolver.getPrefix())) {
                return onState(resolverState, () -> propertyPlaceholderResolver.resolveRequiredPlaceholdersObject(str));
            }
            // without a placeholder there is nothing to resolve
            return str;
        } else if (value instanceof List<?> list) {
            List<?> newList = new ArrayList<>(list);
            final ListIterator i = newList.listIterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (o instanceof CharSequence) {
                    i.set(resolvePlaceHoldersIfNecessary(resolverState, o));
                } else if (o instanceof Map<?,?> submap) {
                    Map<Object, Object> newMap = CollectionUtils.newLinkedHashMap(submap.size());
                    for (Map.Entry<?, ?> entry : submap.entrySet()) {
                        final Object k = entry.getKey();
                        final Object v = entry.getValue();
                        newMap.put(k, resolvePlaceHoldersIfNecessary(resolverState, v));
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

        private final Map<String, DefaultPropertyEntry>[] catalog = new Map[CATALOG_SIZE];
        private final Map<String, DefaultPropertyEntry>[] rawCatalog = new Map[CATALOG_SIZE];
        private final Map<String, DefaultPropertyEntry>[] nonGenerated = new Map[CATALOG_SIZE];

        private final Map<String, Boolean> containsCache = new ConcurrentHashMap<>(20);
        /**
         * Cache for values <i>before</i> conversion. This avoids recomputing placeholders, which keeps
         * random values (e.g. {@code ${random.port}} stable).
         */
        private final Map<String, Object> placeholderResolutionCache = new ConcurrentHashMap<>(20);
        /**
         * What the texts made of random expressions alone have resolved to, by the name they were
         * looked up by. Unlike the caches it is kept when a property source is processed.
         */
        private final Map<String, RandomText> randomTexts = new ConcurrentHashMap<>(4);
        /**
         * Cache for values <i>after</i> conversion.
         */
        private final Map<ConversionCacheKey, Object> resolvedValueCache = new ConcurrentHashMap<>(20);
    }

    /**
     * What a text made of random expressions alone has resolved to.
     *
     * @param text     The text
     * @param resolved What it has resolved to
     */
    private record RandomText(String text, Object resolved) {
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
