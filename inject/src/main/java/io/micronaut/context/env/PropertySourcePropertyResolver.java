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
import java.util.Comparator;
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
    protected final ConversionService conversionService;
    protected final PropertyPlaceholderResolver propertyPlaceholderResolver;
    protected final Map<String, PropertySource> propertySources = new ConcurrentHashMap<>(10);
    // The catalogs that lookups read are held by `state`. These three arrays are kept for binary
    // compatibility with subclasses: they are no longer read by this class and mirror the published
    // catalogs, slot by slot. The `catalog` array is also still the monitor that guards writes into
    // the catalogs and the publishing of new ones. Nothing holds it while the code of property
    // sources runs, except while one that is added is processed into the published catalogs.
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
     * The read of the property sources that the current thread makes, see {@link #beginRead(Build)}.
     */
    private final ThreadLocal<Build> reads = new ThreadLocal<>();
    /**
     * The number of threads that read the property sources. It is zero unless a start or a refresh
     * is in progress, which lets every lookup skip the per-thread check.
     */
    private final AtomicInteger readers = new AtomicInteger();
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
     * Drops the properties, as stopping the environment always did: the published state is
     * replaced right away, and only the generated catalog and the caches are dropped. The raw and
     * the normalized catalog stay, so a stopped environment answers what it used to. They are
     * copied: a start still in progress keeps writing into the state it started with, which is
     * not published any more.
     */
    void reset() {
        synchronized (catalogLock) {
            ResolverState current = state;
            ResolverState replacement = new ResolverState();
            copyEntries(current.rawCatalog, replacement.rawCatalog);
            copyEntries(current.nonGenerated, replacement.nonGenerated);
            state = replacement;
            mirrorPublishedState();
        }
    }

    private static void copyEntries(Map<String, DefaultPropertyEntry>[] from, Map<String, DefaultPropertyEntry>[] to) {
        for (int i = 0; i < from.length; i++) {
            if (from[i] != null) {
                to[i] = new LinkedHashMap<>(from[i]);
            }
        }
    }

    /**
     * Starts a read of the property sources on the current thread: once the read writes into a
     * state, see {@link #buildNewState(Build)} and {@link #buildInPlace(Build)}, the lookups of the
     * thread read that state, and the property sources it processes go into it.
     *
     * @param build What the read writes into
     */
    void beginRead(Build build) {
        reads.set(build);
        readers.incrementAndGet();
    }

    /**
     * Ends the read of the property sources on the current thread.
     */
    void endRead() {
        reads.remove();
        readers.decrementAndGet();
    }

    /**
     * @return The read of the property sources on the current thread, or {@code null}
     */
    @Nullable
    Build currentRead() {
        // A plain read: only a thread that has counted itself in has to see the count, and a thread
        // sees what it has written. A stale count on any other thread only decides whether it makes
        // the per-thread check, which finds nothing there.
        return readers.getPlain() != 0 ? reads.get() : null;
    }

    /**
     * Makes the read write into a new, empty state, which lookups on other threads do not see
     * until it is published: what a refresh reads the property sources into.
     *
     * @param build What the read writes into
     */
    void buildNewState(Build build) {
        build.state = new ResolverState();
    }

    /**
     * Makes the read write into the published state, so that lookups on other threads see each
     * property source once it is processed: what a start reads the property sources into.
     *
     * @param build What the read writes into
     */
    void buildInPlace(Build build) {
        build.state = state;
    }

    /**
     * Writes the given property sources into the state the read of the current thread writes
     * into, in their order among the property sources the read has processed. They are ones that
     * other threads added, and processed into the published state, since the read took the
     * property sources it reads. Each one goes where the read would have processed it: before the
     * first property source the read has processed of a higher order. What the read has processed
     * from there on is written again after it, in the order the read processed it. The properties
     * are those recorded as each was processed, so no code of a property source runs. Called with
     * the catalog lock held.
     *
     * @param build          What the read writes into
     * @param addedMeanwhile The property sources added meanwhile, as processed
     */
    void writeInOrder(Build build, List<Processed> addedMeanwhile) {
        List<Processed> recorded = build.recorded;
        // a stable sort: of the same order, the one added later comes later
        List<Processed> added = new ArrayList<>(addedMeanwhile);
        added.sort(Comparator.comparingInt(processed -> processed.order));
        // where each goes: the higher its order, the later, so these do not decrease
        int[] before = new int[added.size()];
        for (int a = 0; a < added.size(); a++) {
            int i = 0;
            while (i < recorded.size() && recorded.get(i).order <= added.get(a).order) {
                i++;
            }
            before[a] = i;
        }
        int next = 0;
        for (int i = before[0]; i < recorded.size(); i++) {
            for (; next < added.size() && before[next] == i; next++) {
                write(added.get(next));
            }
            write(recorded.get(i));
        }
        for (; next < added.size(); next++) {
            write(added.get(next));
        }
    }

    private void write(Processed processed) {
        writeProperties(processed.origin, processed.values, processed.convention);
    }

    /**
     * Publishes the state the read of the current thread has written into. Called with the
     * catalog lock held.
     *
     * @param build What the read has written into
     * @return The generated catalog that was published until now
     */
    Map<String, DefaultPropertyEntry>[] publishState(Build build) {
        ResolverState previous = state;
        state = build.state;
        mirrorPublishedState();
        return previous.catalog;
    }

    /**
     * The read in progress that a property source a thread other than one reading the property
     * sources has registered, and processed into the published state, is handed to: that read then
     * writes it in its order among what it publishes. Called with the catalog lock held, see
     * {@link #processPropertySource(PropertySource, PropertySource.PropertyConvention)}.
     *
     * @param propertySource The property source
     * @return The read, or {@code null} if there is none to hand it to
     */
    @Nullable
    Build handOverTo(PropertySource propertySource) {
        return null;
    }

    /**
     * The state a lookup reads from. Outside a start or a refresh that is the published state. On
     * the thread that reads the property sources it is the state it reads them into, so the read
     * sees the properties it has contributed so far: a {@link PropertySource} whose keys depend on
     * what the resolver already holds (micronaut-test-resources contributes only the keys the
     * environment cannot resolve) must be answered from the catalog being built, not from the one
     * a refresh is replacing. Every other thread keeps reading the published state until a refresh
     * publishes in one step, except for a thread that {@link #onState(ResolverState, Supplier)}
     * keeps on the state a lookup has started with.
     *
     * @return The state to read the catalogs and the caches from
     */
    private ResolverState readState() {
        Build build = currentRead();
        if (build != null && build.state != null) {
            return build.state;
        }
        // As for the readers: a stale count only decides whether the per-thread check is made.
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
                    value = resolvePlaceHoldersIfNecessary(currentState, value, name);
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
     * Registers the property source and processes it into the published catalogs. On the thread
     * of a start or a refresh it is processed into the catalogs that one reads the property
     * sources into, and registered once those are published.
     *
     * @param properties The property source
     * @param convention The property convention
     */
    protected void processPropertySource(PropertySource properties, PropertySource.PropertyConvention convention) {
        Build build = currentRead();
        int order = properties.getOrder();
        if (build != null && build.state != null) {
            // The thread that reads the property sources: it registers what it has processed once
            // it publishes it, and records it to write it again, see writeInOrder.
            build.processed.put(properties.getName(), properties);
            PropertySource.Origin origin = properties.getOrigin();
            List<PropertyValue> values = readProperties(properties);
            // Written with the lock held, once the code of the property source has run: a start
            // writes where lookups read, and an indexed property writes into the list or the map
            // a property source declares, which the published state can hold as well.
            synchronized (catalogLock) {
                writeProperties(origin, values, convention);
            }
            build.recorded.add(new Processed(order, origin, values, convention));
            return;
        }
        this.propertySources.put(properties.getName(), properties);
        synchronized (catalogLock) {
            PropertySource.Origin origin = properties.getOrigin();
            List<PropertyValue> values = readProperties(properties);
            writeProperties(origin, values, convention);
            Build read = handOverTo(properties);
            if (read != null) {
                read.addedMeanwhile.add(new Processed(properties, order, origin, values, convention));
            }
        }
    }

    /**
     * @param properties The property source
     * @return Its properties, in its order, read with its own code
     */
    private static List<PropertyValue> readProperties(PropertySource properties) {
        List<PropertyValue> values = new ArrayList<>();
        for (String property : properties) {
            values.add(new PropertyValue(property, properties.get(property)));
        }
        return values;
    }

    /**
     * Writes the properties of a property source into the state that writes of the current thread
     * go to.
     *
     * @param origin     The origin of the property source
     * @param values     Its properties
     * @param convention The property convention
     */
    @SuppressWarnings("MagicNumber")
    private void writeProperties(PropertySource.Origin origin, List<PropertyValue> values, PropertySource.PropertyConvention convention) {
        ResolverState target = writeState();
        for (PropertyValue propertyValue : values) {
            String property = propertyValue.property();

            log.trace("Processing property key {}", property);

            Object value = propertyValue.value();

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
                            origin
                        ));
                        expandProperty(
                            resolvedProperty.substring(i),
                            val -> entries.put(propertyName, new DefaultPropertyEntry(
                                propertyName,
                                val,
                                property,
                                origin
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
                                origin
                            ));
                        }
                        first = false;
                    }
                } else {
                    Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(resolvedProperty, true, PropertyCatalog.GENERATED);
                    if (entries != null) {
                        if (value instanceof List || value instanceof Map) {
                            collapseProperty(property, resolvedProperty, entries, value, origin);
                        }
                        entries.put(resolvedProperty, new DefaultPropertyEntry(
                            resolvedProperty,
                            value,
                            property,
                            origin
                        ));
                    }
                    if (first) {
                        Map<String, DefaultPropertyEntry> normalized = resolveEntriesForKey(resolvedProperty, true, PropertyCatalog.NORMALIZED);
                        if (normalized != null) {
                            normalized.put(resolvedProperty, new DefaultPropertyEntry(
                                resolvedProperty,
                                value,
                                property,
                                origin
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
                    origin
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
        clearLookupCaches(target);
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
            ResolverState target = writeState();
            if (target != state) {
                // the state a refresh builds, which only its thread writes into
                return resolveEntriesForKey(target, name, true, propertyCatalog);
            }
            // The lock guards the writes into the published state; every write path already holds
            // it, and the lock is reentrant, so this only ever matters to an external caller that
            // does not.
            synchronized (catalogLock) {
                return resolveEntriesForKey(target, name, true, propertyCatalog);
            }
        }
        return resolveEntriesForKey(readState(), name, propertyCatalog);
    }

    /**
     * @return The state that writes of the current thread go to: the one it reads the property
     * sources into, if it does, and the published one otherwise
     */
    private ResolverState writeState() {
        Build build = currentRead();
        return build != null && build.state != null ? build.state : state;
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
        // Forgotten first: a lookup between the two then remembers a new value and caches that one,
        // rather than caching the value that is forgotten right after.
        resolverState.rememberedValues.clear();
        clearLookupCaches(resolverState);
    }

    private static void clearLookupCaches(ResolverState resolverState) {
        resolverState.containsCache.clear();
        resolverState.resolvedValueCache.clear();
        resolverState.placeholderResolutionCache.clear();
    }

    /**
     * Resolves the placeholders of a property's text, with the memory of its expressions.
     *
     * @param resolverState The state the text was read from
     * @param text          The text
     * @param place         Where the text was found: the name it was looked up by, a {@link ListElement} or a {@link MapValue}
     * @return The text with its placeholders resolved
     */
    private Object resolvePlaceholders(ResolverState resolverState, String text, Object place) {
        if (propertyPlaceholderResolver instanceof DefaultPropertyPlaceholderResolver placeholderResolver) {
            return placeholderResolver.resolveRequiredPlaceholdersObject(text, (placeholder, expression) ->
                value -> remember(resolverState, place, text, new ExpressionPosition(placeholder, expression), value));
        }
        return propertyPlaceholderResolver.resolveRequiredPlaceholdersObject(text);
    }

    /**
     * What an expression of a property's text resolves to, the value remembered for it if there is one.
     *
     * <p>Some expressions, such as {@code random.port}, resolve to another value every time, and the
     * cache that keeps what a value has resolved to is cleared after each property source. A
     * property source that reads such a value while the property sources are being processed would
     * be left with one that no later lookup returns. So the resolvers of such expressions, the
     * {@link RememberingPropertyExpressionResolver}s, remember what an expression has resolved to
     * here, with the text, by the place the text was found at and the position of the expression in
     * it. It is used again for as long as a lookup finds the same text there. The other expressions
     * of the text, such as the property that {@code http://${app.host}:${random.port}/} names, are
     * resolved as before, by every lookup that does not find the value cached.</p>
     *
     * @param resolverState The state the text was read from
     * @param place         Where the text was found, see {@link #resolvePlaceholders(ResolverState, String, Object)}
     * @param text          The text
     * @param position      The position of the expression in the text
     * @param value         Resolves the expression
     * @return What the expression resolves to
     */
    private Object remember(ResolverState resolverState, Object place, String text, ExpressionPosition position, Supplier<Object> value) {
        RememberedText remembered = resolverState.rememberedValues.get(place);
        if (remembered != null && remembered.text().equals(text)) {
            Object rememberedValue = remembered.values().get(position);
            if (rememberedValue != null) {
                return rememberedValue;
            }
        }
        Object resolved = value.get();
        Object[] result = {resolved};
        resolverState.rememberedValues.compute(place, (key, earlier) -> {
            if (earlier != null && earlier.text().equals(text)) {
                // what a lookup of another thread may have remembered for the expression meanwhile
                Object other = earlier.values().putIfAbsent(position, resolved);
                if (other != null) {
                    result[0] = other;
                }
                return earlier;
            }
            Map<ExpressionPosition, Object> values = new ConcurrentHashMap<>(2);
            values.put(position, resolved);
            return new RememberedText(text, values);
        });
        return result[0];
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
        return resolvePlaceHoldersIfNecessary(resolverState, value, null);
    }

    /**
     * Resolves the placeholders of a value. The expressions that remember what they resolve to,
     * such as the random ones, keep it for the place the text was found at.
     *
     * @param resolverState The state the value was read from, which the properties the
     *                      placeholders name are read from too
     * @param value         The value
     * @param place         Where the value was found: the name it was looked up by, a {@link ListElement}
     *                      or a {@link MapValue}; {@code null} if nothing is remembered
     * @return The value with its placeholders resolved
     */
    private Object resolvePlaceHoldersIfNecessary(ResolverState resolverState, Object value, @Nullable Object place) {
        if (value instanceof CharSequence) {
            String str = value.toString();
            if (str.contains(propertyPlaceholderResolver.getPrefix())) {
                return onState(resolverState, () -> place != null
                    ? resolvePlaceholders(resolverState, str, place)
                    : propertyPlaceholderResolver.resolveRequiredPlaceholdersObject(str));
            }
            // without a placeholder there is nothing to resolve
            return str;
        } else if (value instanceof List<?> list) {
            List<?> newList = new ArrayList<>(list);
            final ListIterator i = newList.listIterator();
            while (i.hasNext()) {
                Object elementPlace = place != null ? new ListElement(place, i.nextIndex()) : null;
                final Object o = i.next();
                if (o instanceof CharSequence) {
                    i.set(resolvePlaceHoldersIfNecessary(resolverState, o, elementPlace));
                } else if (o instanceof Map<?,?> submap) {
                    Map<Object, Object> newMap = CollectionUtils.newLinkedHashMap(submap.size());
                    for (Map.Entry<?, ?> entry : submap.entrySet()) {
                        final Object k = entry.getKey();
                        final Object v = entry.getValue();
                        Object valuePlace = elementPlace != null ? new MapValue(elementPlace, k) : null;
                        newMap.put(k, resolvePlaceHoldersIfNecessary(resolverState, v, valuePlace));
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
         * What the {@link RememberingPropertyExpressionResolver}s have resolved the expressions of
         * the texts to, by the place the text was found at: the name it was looked up by, a
         * {@link ListElement} or a {@link MapValue}. Unlike the caches it is kept when a property
         * source is processed.
         */
        private final Map<Object, RememberedText> rememberedValues = new ConcurrentHashMap<>(4);
        /**
         * Cache for values <i>after</i> conversion.
         */
        private final Map<ConversionCacheKey, Object> resolvedValueCache = new ConcurrentHashMap<>(20);
    }

    /**
     * What a read of the property sources writes into and registers, see
     * {@link #beginRead(Build)}. Only the thread that reads uses it.
     */
    static class Build {
        /**
         * The property sources the read has processed, by name, which are registered once it is
         * published.
         */
        final Map<String, PropertySource> processed = new LinkedHashMap<>();
        /**
         * What the read has processed, in its order.
         */
        final List<Processed> recorded = new ArrayList<>();
        /**
         * The property sources that other threads have added, and processed into the published
         * state, since the read took the registered ones, as processed. Guarded by the catalog
         * lock.
         */
        final List<Processed> addedMeanwhile = new ArrayList<>();
        /**
         * The state the read writes into and its thread looks up, or {@code null} until it writes.
         */
        private ResolverState state;

        /**
         * @return Whether the read writes into a state
         */
        final boolean isWriting() {
            return state != null;
        }
    }

    /**
     * A property source as it was processed: its properties, read with its code once.
     */
    static final class Processed {
        /**
         * The property source, {@code null} for one that a read has processed itself.
         */
        @Nullable
        final PropertySource source;
        private final int order;
        private final PropertySource.Origin origin;
        private final List<PropertyValue> values;
        private final PropertySource.PropertyConvention convention;

        private Processed(int order, PropertySource.Origin origin, List<PropertyValue> values, PropertySource.PropertyConvention convention) {
            this(null, order, origin, values, convention);
        }

        private Processed(@Nullable PropertySource source, int order, PropertySource.Origin origin, List<PropertyValue> values,
                          PropertySource.PropertyConvention convention) {
            this.source = source;
            this.order = order;
            this.origin = origin;
            this.values = values;
            this.convention = convention;
        }
    }

    /**
     * What the expressions of a text have resolved to.
     *
     * @param text   The text
     * @param values What its expressions have resolved to, by their position in the text
     */
    private record RememberedText(String text, Map<ExpressionPosition, Object> values) {
    }

    /**
     * The position of an expression in a text.
     *
     * @param placeholder The index of its placeholder among the segments of the text
     * @param expression  Its index among the expressions of the placeholder
     */
    private record ExpressionPosition(int placeholder, int expression) {
    }

    /**
     * The place of an element of a list, the key of what its expressions have resolved to.
     *
     * @param list  The place of the list
     * @param index The index of the element
     */
    private record ListElement(Object list, int index) {
    }

    /**
     * The place of a value of a map, the key of what its expressions have resolved to.
     *
     * @param map The place of the map
     * @param key The key of the value, compared with {@code equals}
     */
    private record MapValue(Object map, @Nullable Object key) {
    }

    /**
     * A property of a property source and its value.
     *
     * @param property The name of the property
     * @param value    Its value
     */
    private record PropertyValue(String property, @Nullable Object value) {
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
