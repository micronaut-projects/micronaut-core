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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import java.util.concurrent.atomic.AtomicLong;
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

    /**
     * Held while a state is published. Nothing but work in memory runs while it is held: no
     * property source, converter or other application code.
     */
    private final Object publishLock = new Object();
    /**
     * The published state: the catalogs and their caches, as lookups see them. An add or a refresh
     * prepares a {@link PendingState} off to the side and publishes it by replacing this field.
     * A published state never changes, so a lookup never sees a half-built one.
     */
    @SuppressWarnings("java:S3077") // ResolverState is only ever published fully built
    private volatile ResolverState state = new ResolverState();
    /**
     * The pending state of the current thread, if it is preparing one. That thread's lookups read
     * it, and the property sources it processes go into it.
     */
    private final ThreadLocal<PendingState> pendingStates = new ThreadLocal<>();
    /**
     * How many threads are preparing a pending state. While it is zero, a lookup skips the
     * per-thread check.
     */
    private final AtomicInteger pendingThreads = new AtomicInteger();
    /**
     * The publishes of the current thread while {@link #diff(Runnable)} runs a change. Removed from
     * the thread when the change ends.
     */
    private final ThreadLocal<List<Publish>> publishes = new ThreadLocal<>();
    /**
     * The state that the lookups of the current thread are kept on while
     * {@link #onState(ResolverState, Supplier)} runs them a second time. The outermost second run
     * removes it from the thread when it ends. A thread that made a lookup while another was in a
     * second run keeps an entry without a value, as {@link ThreadLocal#get()} leaves one. The
     * holder is a JDK type, so that a thread that outlives this resolver keeps nothing of it.
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

    /**
     * Empties the catalogs: those of the current thread's pending state, or else the published ones.
     */
    void reset() {
        PendingState pending = currentPending();
        if (pending != null) {
            pending.state = new ResolverState();
        } else {
            publishEmpty();
        }
    }

    /**
     * The state a lookup reads. That is the published state, except on a thread that prepares a
     * pending state: it reads the pending one, with what it has processed so far. A
     * {@link PropertySource} whose keys depend on what the resolver already holds
     * (micronaut-test-resources only contributes the keys the environment cannot resolve) must be
     * answered from the catalogs being built, not from the ones they replace. Every other thread
     * keeps reading the published state, except for a thread that
     * {@link #onState(ResolverState, Supplier)} keeps on the state a lookup has started with.
     *
     * @return The state to read the catalogs and the caches from
     */
    private ResolverState readState() {
        PendingState pending = currentPending();
        if (pending != null) {
            return pending.state;
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
            if (enclosing == null) {
                // the outermost second run leaves no holder on the thread
                pinnedState.remove();
            }
            if (counted) {
                pinnedThreads.decrementAndGet();
            }
        }
    }

    /**
     * The pending state of the current thread.
     *
     * @return The pending state, {@code null} if the thread prepares none
     */
    private @Nullable PendingState currentPending() {
        // A plain read: a thread that has counted itself in sees its own write. On any other thread a
        // stale count only decides whether it makes the per-thread check, which finds nothing there.
        return pendingThreads.getPlain() != 0 ? pendingStates.get() : null;
    }

    /**
     * Starts a pending state for an add on the current thread, as a copy of the published state.
     * Until {@link #endPending(PendingState)}, the thread's lookups read it and the property
     * sources it processes go into it.
     *
     * @param keepRememberedValues Whether to keep the values that expressions such as
     *                             {@code ${random.port}} resolved to in the published state,
     *                             rather than resolving them again
     * @return The pending state
     */
    PendingState startPendingAdd(boolean keepRememberedValues) {
        ResolverState base = state;
        ResolverState copy = new ResolverState(base, keepRememberedValues ? base.rememberedValues : new ConcurrentHashMap<>(4));
        return startPending(new PendingState(base, copy, keepRememberedValues, currentPending()));
    }

    /**
     * Starts a pending state for a refresh on the current thread, empty. Until
     * {@link #endPending(PendingState)}, the thread's lookups read it and the property sources it
     * processes go into it.
     *
     * @return The pending state
     */
    PendingState startPendingRefresh() {
        return startPending(new PendingState(null, new ResolverState(), false, currentPending()));
    }

    private PendingState startPending(PendingState pending) {
        pendingThreads.incrementAndGet();
        pendingStates.set(pending);
        return pending;
    }

    /**
     * Ends a pending state of the current thread. The thread goes back to the pending state it
     * had before, if it started this one inside it, or else to the published state.
     *
     * @param pending The pending state
     */
    void endPending(PendingState pending) {
        if (pending.outer != null) {
            pendingStates.set(pending.outer);
        } else {
            pendingStates.remove();
        }
        pendingThreads.decrementAndGet();
    }

    /**
     * Publishes the pending state of an add. If another state was published since the add
     * started, the add is replayed on top of that state, from what it processed, with no property
     * source called.
     *
     * @param pending The pending state of the add, which has ended
     */
    void publishAdd(PendingState pending) {
        synchronized (publishLock) {
            ResolverState current = state;
            if (current == pending.base) {
                publish(pending.state);
            } else {
                ResolverState replayed = new ResolverState(current, pending.keepsRememberedValues ? current.rememberedValues : pending.state.rememberedValues);
                replay(replayed, pending);
                publish(replayed);
            }
        }
        PendingState outer = pending.outer;
        if (outer != null) {
            // An add made while the same thread prepares another pending state, by a property source
            // that adds one, for example: the thread sees it in what it goes on preparing. An outer add
            // keeps it with what it processed; a refresh gets it as a late add, see publishRefresh.
            replay(outer.state, pending);
            if (outer.base != null) {
                outer.processed.addAll(pending.processed);
            }
        }
    }

    /**
     * Publishes what a refresh has processed so far, with its late adds. The refresh can go on
     * processing. The late adds keep their order: what the refresh processed and the late adds are
     * replayed together, sorted by order, as the next refresh would process them.
     *
     * @param refresh  The pending state of the refresh
     * @param lateAdds The pending states of the adds made after the refresh took its snapshot
     */
    void publishRefresh(PendingState refresh, Collection<PendingState> lateAdds) {
        synchronized (publishLock) {
            ResolverState published;
            if (lateAdds.isEmpty()) {
                published = new ResolverState(refresh.state, refresh.state.rememberedValues);
                // the published state shares the refresh's maps, so the refresh copies a map before it next writes into it
                refresh.state.owned.clear();
            } else {
                published = new ResolverState(refresh.state.rememberedValues);
                List<ProcessedSource> inOrder = new ArrayList<>(refresh.processed);
                for (PendingState add : lateAdds) {
                    inOrder.addAll(add.processed);
                }
                // a stable sort: a late add comes after what the refresh processed with the same order
                inOrder.sort(Comparator.comparingInt(ProcessedSource::order));
                for (ProcessedSource source : inOrder) {
                    for (RawEntry entry : source.entries()) {
                        processEntry(published, entry);
                    }
                }
            }
            publish(published);
        }
    }

    /**
     * Publishes empty catalogs, as a stopped environment has.
     */
    void publishEmpty() {
        synchronized (publishLock) {
            publish(new ResolverState());
        }
    }

    /**
     * Publishes the published catalogs again, with empty caches and with the expressions of the
     * properties resolved again, as a property source added to or removed from a running
     * environment does.
     */
    void resetCachesAndRememberedValues() {
        synchronized (publishLock) {
            publish(new ResolverState(state, new ConcurrentHashMap<>(4)));
        }
        resetCaches();
    }

    /**
     * Processes what a pending state processed again, into another state that is not published.
     * Only work in memory: no property source is called.
     *
     * @param target  The state to process into
     * @param pending The pending state to replay
     */
    private void replay(ResolverState target, PendingState pending) {
        for (ProcessedSource source : pending.processed) {
            for (RawEntry entry : source.entries()) {
                processEntry(target, entry);
            }
        }
    }

    /**
     * Publishes a state. Called with {@link #publishLock} held.
     *
     * @param published The state
     */
    private void publish(ResolverState published) {
        ResolverState before = state;
        published.published = true;
        state = published;
        List<Publish> made = publishes.get();
        if (made != null) {
            made.add(new Publish(before, published));
        }
    }

    /**
     * Runs a change of the catalogs, such as a refresh, and returns what it has changed: the
     * difference between each state the current thread has published while the change ran and the
     * state published just before it. What another thread publishes meanwhile, a property source it
     * adds for one, is not part of it. The states are compared once the change has ended, with no
     * lock held, as comparing values runs their {@code equals}.
     *
     * @param change The change to run
     * @return The properties that the change has changed, with the value each had before
     */
    public Map<String, Object> diff(Runnable change) {
        List<Publish> enclosing = publishes.get();
        List<Publish> made = new ArrayList<>(2);
        publishes.set(made);
        try {
            change.run();
        } finally {
            if (enclosing != null) {
                enclosing.addAll(made);
                publishes.set(enclosing);
            } else {
                publishes.remove();
            }
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Publish publish : made) {
            // a property that more than one publish has changed had the value of the first before
            diffCatalog(publish.before().catalog, publish.after().catalog).forEach(changes::putIfAbsent);
        }
        return changes;
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
        ResolverState currentState = readState();
        Boolean result = cached(currentState, currentState.containsCache, name);
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
            cache(currentState, currentState.containsCache, name, result);
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
                    String indexedName = name + "[";
                    for (String key : entries.keySet()) {
                        if (key.startsWith(finalName) || key.startsWith(indexedName)) {
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
        // one state for the entries and for the placeholders that their values hold
        return onState(readState(), () -> resolveProperties(name, keyFormat));
    }

    private Map<String, Object> resolveProperties(String name, @Nullable StringConvention keyFormat) {
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
        ResolverState currentState = readState();
        Object cached = cacheableType ? cached(currentState, currentState.resolvedValueCache, cacheKey) : null;
        if (cached != null) {
            return cached == NO_VALUE ? Optional.empty() : Optional.of((T) cached);
        }
        Object value = cached(currentState, currentState.placeholderResolutionCache, name);
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
                if (i > -1 && name.endsWith("]") && name.indexOf('[', i + 1) == -1) {
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
                } else if (i > -1) {
                    // an index in the middle of the name, e.g. foo[0].bar or foo[0].bar[1].baz
                    value = resolveNestedIndexedValue(entries, name, i);
                }
            }

            if (value != null) {
                Optional<T> converted;
                if (entries != null) {
                    // iff entries is null, the value is from placeholderResolutionCache and doesn't need this step
                    value = resolvePlaceHoldersIfNecessary(currentState, value, name);
                    cache(currentState, currentState.placeholderResolutionCache, name, value);
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
                    cache(currentState, currentState.resolvedValueCache, cacheKey, converted.orElse((T) NO_VALUE));
                }
                return converted;
            } else if (cacheableType) {
                cache(currentState, currentState.resolvedValueCache, cacheKey, NO_VALUE);
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
            } else if (isMapConvertible(requiredType)) {
                Map<String, Object> subMap = resolveSubMap(currentState, name, entries, conversionContext);
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
     * Resolves a value for a name that contains an index which is followed by further segments,
     * for example {@code foo[0].bar} or {@code foo[0].bar[1]}, by walking the expanded container
     * stored under the un-indexed base name.
     *
     * @param entries The entries
     * @param name The property name
     * @param firstBracket The index of the first {@code [} in the name
     * @return The value or {@code null}
     */
    private static @Nullable Object resolveNestedIndexedValue(Map<String, DefaultPropertyEntry> entries, String name, int firstBracket) {
        Object current = entries.getOrDefault(name.substring(0, firstBracket), NULL_ENTRY).value();
        int pos = firstBracket;
        int length = name.length();
        while (current != null && pos < length) {
            char c = name.charAt(pos);
            if (c == '[') {
                int end = name.indexOf(']', pos);
                if (end == -1) {
                    return null;
                }
                String index = name.substring(pos + 1, end);
                if (current instanceof List<?> list) {
                    if (!StringUtils.isDigits(index)) {
                        return null;
                    }
                    int number = Integer.parseInt(index);
                    current = number < list.size() ? list.get(number) : null;
                } else if (current instanceof Map<?, ?> map) {
                    current = map.get(index);
                } else {
                    return null;
                }
                pos = end + 1;
            } else if (c == '.') {
                if (!(current instanceof Map<?, ?> map)) {
                    return null;
                }
                // the expanded container keys a map entry by everything up to the next index,
                // e.g. foo[0].bar.baz[0] is stored as {"bar.baz": [...]}, fall back to a single segment
                int nextBracket = name.indexOf('[', pos);
                int end = nextBracket == -1 ? length : nextBracket;
                Object next = map.get(name.substring(pos + 1, end));
                if (next == null) {
                    int nextDot = name.indexOf('.', pos + 1);
                    if (nextDot != -1 && nextDot < end) {
                        end = nextDot;
                        next = map.get(name.substring(pos + 1, end));
                    }
                }
                current = next;
                pos = end;
            } else {
                return null;
            }
        }
        return current;
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
     * Processes a property source: into the current thread's pending state, or else as an add of
     * its own, which is published.
     *
     * @param properties The property source
     * @param convention The property convention
     */
    protected void processPropertySource(PropertySource properties, PropertySource.PropertyConvention convention) {
        PendingState pending = currentPending();
        if (pending != null) {
            processInto(pending, properties, convention);
            return;
        }
        PendingState add = startPendingAdd(true);
        try {
            processInto(add, properties, convention);
        } finally {
            endPending(add);
        }
        publishAdd(add);
    }

    private void processInto(PendingState pending, PropertySource properties, PropertySource.PropertyConvention convention) {
        ResolverState target = pending.state;
        List<RawEntry> entries = new ArrayList<>();
        pending.processed.add(new ProcessedSource(properties.getOrder(), entries));
        for (String property : properties) {

            log.trace("Processing property key {}", property);

            RawEntry entry = new RawEntry(property, properties.get(property), convention, properties.getOrigin());
            entries.add(entry);
            processEntry(target, entry);
        }
        resetCaches();
    }

    @SuppressWarnings("MagicNumber")
    private void processEntry(ResolverState target, RawEntry rawEntry) {
        String property = rawEntry.property();
        Object value = rawEntry.value();
        PropertySource.PropertyConvention convention = rawEntry.convention();
        PropertySource.Origin origin = rawEntry.origin();

        populateRawCatalog(target, property, value, convention, origin);

        List<String> resolvedProperties = resolvePropertiesForConvention(property, convention);
        boolean first = true;
        for (String resolvedProperty : resolvedProperties) {
            int i = resolvedProperty.indexOf('[');
            if (i > -1) {
                String propertyName = resolvedProperty.substring(0, i);
                Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(target, propertyName, true, PropertyCatalog.GENERATED);
                if (entries != null) {
                    entries.put(resolvedProperty, new DefaultPropertyEntry(
                        resolvedProperty,
                        value,
                        property,
                        origin
                    ));
                    expandIndexedProperty(
                        target,
                        entries,
                        propertyName,
                        resolvedProperty.substring(i),
                        value,
                        property,
                        origin
                    );
                }
                if (first) {
                    Map<String, DefaultPropertyEntry> normalized = resolveEntriesForKey(target, resolvedProperty, true, PropertyCatalog.NORMALIZED);
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
                Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(target, resolvedProperty, true, PropertyCatalog.GENERATED);
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
                    Map<String, DefaultPropertyEntry> normalized = resolveEntriesForKey(target, resolvedProperty, true, PropertyCatalog.NORMALIZED);
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
     * @param target The state the property is processed into
     * @param property The verbatim property key
     * @param value The property value
     * @param convention The property convention
     * @param origin The origin of the property source
     */
    private void populateRawCatalog(
        ResolverState target,
        String property,
        Object value,
        PropertySource.PropertyConvention convention,
        PropertySource.Origin origin) {

        Map<String, DefaultPropertyEntry> rawEntries = resolveEntriesForKey(target, property, true, PropertyCatalog.RAW);
        if (rawEntries != null) {
            rawEntries.put(property, new DefaultPropertyEntry(
                property,
                value,
                property,
                origin
            ));
        }

        int bracket = property.indexOf('[');
        if (bracket <= 0 || convention == PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE) {
            return;
        }
        String baseName = property.substring(0, bracket);
        Map<String, DefaultPropertyEntry> baseEntries = resolveEntriesForKey(target, baseName, true, PropertyCatalog.RAW);
        if (baseEntries != null) {
            expandIndexedProperty(
                target,
                baseEntries,
                baseName,
                property.substring(bracket),
                value,
                property,
                origin
            );
        }
    }

    /**
     * Expands an indexed property (e.g. {@code foo[0].bar}) into the given entries map under its
     * base name, building the container value and writing it back to {@code entries} as it grows.
     *
     * <p>The expansion writes into a container in place only when the state owns it, that is when
     * the state has created it or copied it. A container of a property source, which an entry
     * holds by reference, and one of the state the state was built from are copied before the
     * first write into them. So a property source's own value never changes, an immutable one can
     * be expanded, and what the RAW and the GENERATED catalog expand stays apart. The entries that
     * held a copied container, the aggregate and what collapsing a structured value has put under
     * the base name, are given the copy, so they see the expansion as before.</p>
     *
     * @param target The state the property is processed into
     * @param entries The catalog entries map to read the existing container from and write the
     *                expanded container to, keyed by {@code baseName}
     * @param baseName The un-indexed base property name (e.g. {@code foo})
     * @param indexSuffix The indexed remainder of the property, starting with {@code [} (e.g. {@code [0].bar})
     * @param value The value to place at the indexed location
     * @param originalProperty The original, unresolved property key, recorded on the entry
     * @param origin The origin of the property source, recorded on the entry
     */
    private void expandIndexedProperty(
        ResolverState target,
        Map<String, DefaultPropertyEntry> entries,
        String baseName,
        String indexSuffix,
        Object value,
        String originalProperty,
        PropertySource.Origin origin) {
        IdentityHashMap<Object, Object> copies = new IdentityHashMap<>(4);
        DefaultPropertyEntry aggregate = entries.get(baseName);
        expandProperty(
            new Expansion(target, copies),
            indexSuffix,
            val -> {
                // a copy of the aggregate keeps what it was set from
                boolean copy = aggregate != null && copies.get(aggregate.value()) == val;
                entries.put(baseName, new DefaultPropertyEntry(
                    baseName,
                    val,
                    copy ? aggregate.raw() : originalProperty,
                    copy ? aggregate.origin() : origin
                ));
            },
            () -> entries.getOrDefault(baseName, NULL_ENTRY).value(),
            value
        );
        if (!copies.isEmpty()) {
            for (Map.Entry<String, DefaultPropertyEntry> entry : entries.entrySet()) {
                DefaultPropertyEntry held = entry.getValue();
                Object copy = copies.get(held.value());
                if (copy != null && isUnder(entry.getKey(), baseName)) {
                    entry.setValue(new DefaultPropertyEntry(held.property(), copy, held.raw(), held.origin()));
                }
            }
        }
    }

    /**
     * Whether a key is the base name or a key under it, such as {@code foo[0]} or {@code foo.bar}
     * for {@code foo}.
     *
     * @param key The key
     * @param baseName The base name
     * @return Whether the key is under the base name
     */
    private static boolean isUnder(String key, String baseName) {
        if (!key.startsWith(baseName)) {
            return false;
        }
        if (key.length() == baseName.length()) {
            return true;
        }
        char next = key.charAt(baseName.length());
        return next == '[' || next == '.';
    }

    private void expandProperty(Expansion expansion, String property, Consumer<Object> containerSet, Supplier<Object> containerGet, Object actualValue) {
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
                List<Object> list = expansion.writableList(container, containerSet);
                fill(list, number, null);

                expandProperty(expansion, propertyRest, val -> list.set(number, val), () -> list.get(number), actualValue);
            } else {
                Map<Object, Object> map = expansion.writableMap(container, containerSet);

                expandProperty(expansion, propertyRest, val -> map.put(propertyIndex, val), () -> map.get(propertyIndex), actualValue);
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
            Map<Object, Object> map = expansion.writableMap(containerGet.get(), containerSet);
            expandProperty(expansion, propertyRest, val -> map.put(propertyName, val), () -> map.get(propertyName), actualValue);
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
            PendingState pending = currentPending();
            if (pending != null) {
                return resolveEntriesForKey(pending.state, name, true, propertyCatalog);
            }
            // a published state is never written into
            Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(state, name, false, propertyCatalog);
            return entries != null ? Collections.unmodifiableMap(entries) : null;
        }
        return resolveEntriesForKey(readState(), name, propertyCatalog);
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
                if (allowCreate && (entries == null || !resolverState.owned.contains(entries))) {
                    // the map of the state it was built from, which may be published, is copied before the first write
                    entries = entries == null ? new LinkedHashMap<>(5) : new LinkedHashMap<>(entries);
                    resolverState.owned.add(entries);
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
        ResolverState currentState = readState();
        currentState.containsCache.clear();
        currentState.resolvedValueCache.clear();
        currentState.placeholderResolutionCache.clear();
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
     * cache of resolved values is not used after a property source is processed. A property source
     * that reads such a value while the property sources are being processed would be left with one
     * that no later lookup returns. So the resolvers of such expressions, the
     * {@link RememberingPropertyExpressionResolver}s, remember what an expression has resolved to
     * here, with the text, by the place the text was found at and the position of the expression in
     * it. It is used again for as long as a lookup finds the same text there. The other expressions
     * of the text, such as the property that {@code http://${app.host}:${random.port}/} names, are
     * resolved as before, by every lookup that does not find the value cached.</p>
     *
     * <p>A state built on top of another shares what that one remembers, unless it resolves the
     * expressions again. A lookup that read the text from an older state can then finish after a
     * lookup of a newer one has remembered another text at the place. So the memory only replaces
     * a text with the text of a state at least as new as the one it was remembered from, or with
     * that of the current thread's pending state. It also takes it when nothing is remembered at
     * the place: what is remembered for a text is only used by a lookup that finds the same text.</p>
     *
     * @param resolverState The state the text was read from
     * @param place         Where the text was found, see {@link #resolvePlaceholders(ResolverState, String, Object)}
     * @param text          The text
     * @param position      The position of the expression in the text
     * @param value         Resolves the expression
     * @return What the expression resolves to
     */
    private Object remember(ResolverState resolverState, Object place, String text, ExpressionPosition position,
                            Supplier<Object> value) {
        RememberedText remembered = resolverState.rememberedValues.get(place);
        if (remembered != null && remembered.text().equals(text)) {
            Object rememberedValue = remembered.values().get(position);
            if (rememberedValue != null) {
                return rememberedValue;
            }
        }
        Object resolved = value.get();
        PendingState pending = currentPending();
        boolean preparing = pending != null && pending.state == resolverState;
        Object[] result = {resolved};
        // Checked within compute, which a lookup at the place by another thread waits for.
        resolverState.rememberedValues.compute(place, (key, earlier) -> {
            if (earlier != null && earlier.text().equals(text)) {
                // what a lookup of another thread may have remembered for the expression meanwhile
                Object other = earlier.values().putIfAbsent(position, resolved);
                if (other != null) {
                    result[0] = other;
                }
                return earlier;
            }
            // States share what they remember when one is built on top of another: a lookup of an
            // older state does not replace what a newer one has remembered.
            boolean current = earlier == null
                || preparing
                || resolverState.sequence >= earlier.sequence();
            if (!current) {
                return earlier;
            }
            Map<ExpressionPosition, Object> values = new ConcurrentHashMap<>(2);
            values.put(position, resolved);
            return new RememberedText(text, values, resolverState.sequence);
        });
        return result[0];
    }

    /**
     * Reads a cached value. Only a published state, which never changes, uses its caches.
     *
     * @param resolverState The state the cache belongs to
     * @param cache         The cache
     * @param key           The key
     * @param <K>           The key type
     * @param <V>           The value type
     * @return The cached value, or {@code null}
     */
    private static <K, V> @Nullable V cached(ResolverState resolverState, Map<K, V> cache, K key) {
        return resolverState.published ? cache.get(key) : null;
    }

    /**
     * Caches a value read from the catalogs of a state, if it is published.
     *
     * @param resolverState The state the cache belongs to
     * @param cache         The cache
     * @param key           The key
     * @param value         The value
     * @param <K>           The key type
     * @param <V>           The value type
     */
    private static <K, V> void cache(ResolverState resolverState, Map<K, V> cache, K key, V value) {
        if (resolverState.published) {
            cache.put(key, value);
        }
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
     * The state of the resolver: the property catalogs and the caches derived from them. Holding
     * them together means an add or a refresh can prepare a new state without touching what lookups
     * are reading, and publish it with a single write to {@link #state}. A lookup racing with that
     * switch keeps reading the state it started from, so a value it computes from the previous
     * catalogs is cached against those catalogs rather than leaking into the new ones.
     *
     * <p>A state copied from another starts with the maps of that one, and copies a map before it
     * first writes into it, see {@link #owned}. Only the thread that prepares a state writes into
     * it, and only until it is published.</p>
     */
    private static final class ResolverState {

        /**
         * Orders the states by when they were created, see {@link #sequence}.
         */
        private static final AtomicLong SEQUENCE = new AtomicLong();

        /**
         * Where the state comes in the order the states were created: one built on top of another
         * comes after it.
         */
        private final long sequence = SEQUENCE.incrementAndGet();

        /**
         * Whether the state is published. A published state never changes, so the values cached
         * from it stay valid; a state being built is not cached from.
         */
        private boolean published;

        // properties are stored in an array of maps organized by character in the alphabet
        // this allows optimization of searches by prefix
        @SuppressWarnings("MagicNumber")
        private static final int CATALOG_SIZE = 58;

        private final @Nullable Map<String, DefaultPropertyEntry>[] catalog;
        private final @Nullable Map<String, DefaultPropertyEntry>[] rawCatalog;
        private final @Nullable Map<String, DefaultPropertyEntry>[] nonGenerated;
        /**
         * The maps of the catalogs, and the lists and maps of the values, that this state may write
         * into, by identity: those it has created or copied. Every other one is shared with the state
         * it was built from, or with a property source.
         */
        private final Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());

        private final Map<String, Boolean> containsCache = new ConcurrentHashMap<>(20);
        /**
         * Cache for values <i>before</i> conversion. This avoids recomputing placeholders. What
         * expressions such as {@code ${random.port}} resolve to is kept in
         * {@link #rememberedValues} as well, which keeps them stable for the lookups of a property.
         */
        private final Map<String, Object> placeholderResolutionCache = new ConcurrentHashMap<>(20);
        /**
         * What the {@link RememberingPropertyExpressionResolver}s have resolved the expressions of
         * the texts to, by the place the text was found at: the name it was looked up by, a
         * {@link ListElement} or a {@link MapValue}. Unlike the caches it is kept when a property
         * source is processed. It holds one entry per looked-up name, or place in a list, whose text
         * holds, or held, such an expression. A state copied from another can share it, as the
         * states that a refresh publishes do. A refresh starts with a new one, so the expressions
         * are resolved again after a refresh.
         */
        private final Map<Object, RememberedText> rememberedValues;
        /**
         * Cache for values <i>after</i> conversion.
         */
        private final Map<ConversionCacheKey, Object> resolvedValueCache = new ConcurrentHashMap<>(20);

        /**
         * An empty state.
         */
        ResolverState() {
            this(new ConcurrentHashMap<>(4));
        }

        /**
         * An empty state.
         *
         * @param rememberedValues What the expressions resolve to
         */
        ResolverState(Map<Object, RememberedText> rememberedValues) {
            catalog = new Map[CATALOG_SIZE];
            rawCatalog = new Map[CATALOG_SIZE];
            nonGenerated = new Map[CATALOG_SIZE];
            this.rememberedValues = rememberedValues;
        }

        /**
         * A state built on top of another, with its catalogs and empty caches.
         *
         * @param base             The state it is built on
         * @param rememberedValues What the expressions resolve to, the memory of the base or a new one
         */
        ResolverState(ResolverState base, Map<Object, RememberedText> rememberedValues) {
            catalog = base.catalog.clone();
            rawCatalog = base.rawCatalog.clone();
            nonGenerated = base.nonGenerated.clone();
            this.rememberedValues = rememberedValues;
        }
    }

    /**
     * A state that one thread prepares off to the side, for an add or a refresh. Lookups on other
     * threads don't see it until it is published.
     */
    static final class PendingState {
        /**
         * The published state an add started from, {@code null} for a refresh, which starts empty.
         */
        private final @Nullable ResolverState base;
        /**
         * Whether an add keeps the values that expressions resolved to in its base.
         */
        private final boolean keepsRememberedValues;
        /**
         * What was processed, by property source, in order. Kept so that an add can be replayed on
         * a newer published state, and a late add with a refresh, without calling the property
         * sources again.
         */
        private final List<ProcessedSource> processed = new ArrayList<>();
        /**
         * The pending state the thread had before this one, which it goes back to when this one
         * ends.
         */
        private final @Nullable PendingState outer;
        /**
         * The state being prepared.
         */
        private ResolverState state;

        private PendingState(@Nullable ResolverState base, ResolverState state, boolean keepsRememberedValues, @Nullable PendingState outer) {
            this.base = base;
            this.state = state;
            this.keepsRememberedValues = keepsRememberedValues;
            this.outer = outer;
        }
    }

    /**
     * The properties a pending state processed from one property source.
     *
     * @param order   The order of the property source
     * @param entries Its properties, in the order they were processed
     */
    private record ProcessedSource(int order, List<RawEntry> entries) {
    }

    /**
     * A property as a property source has given it.
     *
     * @param property   The property key, as the property source gives it
     * @param value      The value
     * @param convention The convention of the property source
     * @param origin     The origin of the property source
     */
    private record RawEntry(String property, Object value, PropertySource.PropertyConvention convention, PropertySource.Origin origin) {
    }

    /**
     * A state published, and the state published before it.
     *
     * @param before The state published before
     * @param after  The state published
     */
    private record Publish(ResolverState before, ResolverState after) {
    }

    /**
     * The containers that an expansion writes into: those the state owns, and copies of the others.
     *
     * @param target The state the property is processed into, which owns what the expansion creates and copies
     * @param copies The containers the expansion has copied, and their copies
     */
    private record Expansion(ResolverState target, IdentityHashMap<Object, Object> copies) {

        @SuppressWarnings("unchecked")
        List<Object> writableList(@Nullable Object container, Consumer<Object> containerSet) {
            List<Object> list;
            if (container instanceof List<?> existing) {
                if (target.owned.contains(existing)) {
                    return (List<Object>) existing;
                }
                list = new ArrayList<>(existing);
                copies.put(existing, list);
            } else {
                list = new ArrayList<>(10);
            }
            target.owned.add(list);
            containerSet.accept(list);
            return list;
        }

        @SuppressWarnings("unchecked")
        Map<Object, Object> writableMap(@Nullable Object container, Consumer<Object> containerSet) {
            Map<Object, Object> map;
            if (container instanceof Map<?, ?> existing) {
                if (target.owned.contains(existing)) {
                    return (Map<Object, Object>) existing;
                }
                map = new LinkedHashMap<>(existing);
                copies.put(existing, map);
            } else {
                map = new LinkedHashMap<>(10);
            }
            target.owned.add(map);
            containerSet.accept(map);
            return map;
        }
    }

    /**
     * What the expressions of a text have resolved to.
     *
     * @param text     The text
     * @param values   What its expressions have resolved to, by their position in the text
     * @param sequence The sequence of the state the text was read from
     */
    private record RememberedText(String text, Map<ExpressionPosition, Object> values, long sequence) {
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

    private record ConversionCacheKey(String name, Class<?> requiredType) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConversionCacheKey that)) {
                return false;
            }
            return Objects.equals(name, that.name) && Objects.equals(requiredType, that.requiredType);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
