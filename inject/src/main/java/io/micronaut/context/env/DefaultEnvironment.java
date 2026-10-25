/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.PropertyResolverDelegate;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.ResourceConflictException;
import io.micronaut.core.io.ResourceDuplicateException;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.core.io.ResourceLoadStrategyType;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.AnnotationScanner;
import io.micronaut.core.io.scan.BeanIntrospectionScanner;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <p>The default implementation of the {@link Environment} interface. Configures a named environment.</p>
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
@Internal
final class DefaultEnvironment implements Environment, PropertyResolverDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEnvironment.class);
    private static final String FILE_SEPARATOR = ",";
    private static final List<String> DEFAULT_CONFIG_LOCATIONS = Arrays.asList("classpath:/", "file:config/");
    private static final String ENV_PROPERTY_SOURCES_KEY = "MICRONAUT_CONFIG_FILES";
    private static final List<PropertySourceLoader> DEFAULT_PROPERTY_SOURCE_LOADERS = List.of(
        new YamlPropertySourceLoader(),
        new PropertiesPropertySourceLoader(),
        new ConstantPropertySourceLoader()
    );
    private static final String FILE_PREFIX = "file:";
    /** How long a refresh waits at a time for the threads that are processing a property source they added. */
    private static final long ANNOUNCED_WAIT_MILLIS = 50;
    /** How long a refresh waits for them at most, before it goes on as it did, without waiting. */
    private static final long ANNOUNCED_WAIT_LIMIT_MILLIS = 5_000;

    private final ClassPathResourceLoader resourceLoader;
    private final MutableConversionService mutableConversionService;
    private final String applicationName;
    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = Collections.synchronizedSet(CollectionUtils.newLinkedHashSet(10));
    private final AnnotationScanner annotationScanner;
    private final Collection<String> configurationIncludes = new HashSet<>(3);
    private final Collection<String> configurationExcludes = new HashSet<>(3);
    @Nullable
    @SuppressWarnings("java:S3077") // evaluatePropertySourceLoaders returns an immutable List.copyOf, published once under double checked locking
    private volatile Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final ApplicationContextConfiguration configuration;
    private final Collection<String> configLocations;
    private final PropertySourcePropertyResolver propertyPlaceholderResolver;
    private final PropertyResolver propertyResolver;

    private final List<PropertySource> refreshablePropertySources = new ArrayList<>(10);
    private final Map<String, PropertySource> propertySources = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Collection<PropertySourcesLocator> propertySourcesLocators;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reading = new AtomicBoolean(false);
    /**
     * While a refresh runs, what {@link #addPropertySource(PropertySource)} and
     * {@link #removePropertySource(PropertySource)} have changed since it started. A refresh that
     * fails puts back the property sources it started with, and then these changes. Guarded by
     * the monitor of {@link #propertySources}.
     */
    private @Nullable RefreshChanges changedWhileRefreshing;
    /**
     * How many refreshes are about to take the lock of the catalogs and have not started to read the
     * property sources, see {@link #announceRefresh(RefreshAnnouncement)}. Guarded by the monitor of
     * {@link #propertySources}, as are the three fields below.
     */
    private int refreshesBeforeRead;
    /**
     * The property sources added outside a read that the threads adding them have left to a refresh
     * about to read, in the order they were added, rather than waiting for that refresh.
     */
    private List<Added> deferredToRefresh = new ArrayList<>(4);
    /**
     * While a read of the property sources is in progress, those it reads as registered that the
     * threads adding them would have processed: processed on top if the read fails.
     */
    private @Nullable List<Added> leftToRead;
    /** How many threads are processing a property source they added outside a read; a refresh about to start waits for them. */
    private int processing;

    // For configuration maps, sets with low access - it's enough to use Collections.synchronized

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     */
    public DefaultEnvironment(ApplicationContextConfiguration configuration) {
        MutableConversionService conversionService = configuration.getConversionService().orElseGet(MutableConversionService::create);
        this.propertyPlaceholderResolver = new PropertySourcePropertyResolver(
            conversionService,
            true,
            configuration.getClassLoader()
        );
        this.applicationName = configuration.getApplicationName();
        if (applicationName.isBlank()) {
            throw new ConfigurationException("Application name cannot be blank");
        }
        this.propertyResolver = propertyPlaceholderResolver;
        this.mutableConversionService = conversionService;
        this.configuration = configuration;
        this.resourceLoader = configuration.getResourceLoader();
        this.classLoader = configuration.getClassLoader();
        this.annotationScanner = new BeanIntrospectionScanner(classLoader);
        DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LOG, configuration);
        EnvironmentNamesDeducer environmentNamesDeducer = configuration.getEnvironmentNamesDeducer();
        if (environmentNamesDeducer == null) {
            environmentNamesDeducer = deducer;
        }
        EnvironmentPackagesDeducer packagesDeducer = configuration.getPackageDeducer();
        if (packagesDeducer == null) {
            packagesDeducer = deducer;
        }
        this.names = environmentNamesDeducer.deduceEnvironmentNames();
        if (!names.isEmpty()) {
            LOG.info("Established active environments: {}", names);
        }
        this.packages.addAll(packagesDeducer.deducePackages());
        List<String> configLocations = configuration.getOverrideConfigLocations() == null
            ? new ArrayList<>(DEFAULT_CONFIG_LOCATIONS)
            : new ArrayList<>(configuration.getOverrideConfigLocations());
        // Search config locations in reverse order
        Collections.reverse(configLocations);
        this.configLocations = configLocations;
        this.propertySourcesLocators = configuration.getPropertySourcesLocators();
    }

    @Override
    public PropertyResolver delegate() {
        return propertyResolver;
    }

    @Override
    public boolean isPresent(String className) {
        return presenceCache.computeIfAbsent(className, s -> ClassUtils.isPresent(className, classLoader));
    }

    @Override
    public PropertyPlaceholderResolver getPlaceholderResolver() {
        return propertyPlaceholderResolver.propertyPlaceholderResolver;
    }

    @Override
    public Stream<Class<?>> scan(Class<? extends Annotation> annotation) {
        return annotationScanner.scan(annotation, getPackages());
    }

    @Override
    public Stream<Class<?>> scan(Class<? extends Annotation> annotation, String... packages) {
        return annotationScanner.scan(annotation, packages);
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public boolean isActive(BeanConfiguration configuration) {
        String name = configuration.getName();
        return !configurationExcludes.contains(name) && (configurationIncludes.isEmpty() || configurationIncludes.contains(name));
    }

    @Override
    public DefaultEnvironment addPropertySource(PropertySource propertySource) {
        String name = propertySource.getName();
        // a thread that holds the lock of the catalogs, as one whose property source adds another
        // while it is processed does, processes it at once, as it did: it waits for no refresh
        boolean holdsCatalogLock = propertyPlaceholderResolver.holdsCatalogLock();
        boolean process;
        synchronized (propertySources) {
            propertySources.put(name, propertySource);
            noteChangeWhileRefreshing(name, propertySource);
            // a refresh about to read reads it as registered, without this thread waiting for it
            process = isRunning() && !reading.get() && (holdsCatalogLock || !deferToRefresh(name, propertySource));
        }
        if (process) {
            processAdded(name, propertySource, holdsCatalogLock);
        }
        return this;
    }

    /**
     * Processes a property source added outside a read on top of the properties, as it was, if it
     * is still registered. If a read has started since it was registered, or a refresh is about to
     * read, this thread leaves it to that read, which reads it as registered, rather than waiting for
     * the refresh and processing it once more after it.
     *
     * @param name             The name of the property source
     * @param propertySource   The property source
     * @param holdsCatalogLock Whether this thread holds the lock of the catalogs, and processes it at once
     */
    private void processAdded(String name, PropertySource propertySource, boolean holdsCatalogLock) {
        // asked for before this thread counts itself as processing, as it was asked for before the
        // lock of the catalogs was taken
        PropertySource.PropertyConvention convention = propertySource.getConvention();
        synchronized (propertySources) {
            if (propertySources.get(name) != propertySource) {
                // replaced or removed meanwhile, by a thread that processes, or leaves to a read, what replaced it
                return;
            }
            if (!holdsCatalogLock && handOver(new Added(name, propertySource))) {
                return;
            }
            processing++;
        }
        try {
            propertyPlaceholderResolver.resetCachesAndRememberedValues();
            LOG.debug("Processing property source: {} convention: {}", name, convention);
            propertyPlaceholderResolver.processPropertySource(propertySource, convention);
        } finally {
            synchronized (propertySources) {
                processing--;
                // a refresh that waits checks again in a while, also if this does not get to notify it
                propertySources.notifyAll();
            }
        }
    }

    /**
     * Leaves a property source added outside a read, which the thread adding it would process, to a
     * refresh that is about to read, if there is one. Called with the monitor of
     * {@link #propertySources} held.
     *
     * @param name           The name of the property source
     * @param propertySource The property source
     * @return Whether it is left to a refresh
     */
    private boolean deferToRefresh(String name, PropertySource propertySource) {
        if (refreshesBeforeRead == 0) {
            return false;
        }
        addLast(deferredToRefresh, new Added(name, propertySource));
        return true;
    }

    /**
     * Adds a property source left to a read last, in the place of one left by the same name: the
     * thread that added that one would have processed it before this one, which replaces it.
     *
     * @param left  The property sources left to a read
     * @param added The property source
     */
    private static void addLast(List<Added> left, Added added) {
        left.removeIf(earlier -> earlier.name().equals(added.name()));
        left.add(added);
    }

    /**
     * Leaves a property source added outside a read, which this thread would process, to the read
     * in progress, which has started since it was registered and reads it as registered, or else to
     * a refresh that is about to read. Either processes it on top if it does not publish what it
     * read. One that is no longer registered as that instance is not left to any read: it must not
     * take the place of what replaced it, which is processed, or left to a read, by the thread that
     * added it. Called with the monitor of {@link #propertySources} held.
     *
     * @param added The property source
     * @return Whether it is left to a read, or is no longer registered
     */
    private boolean handOver(Added added) {
        if (propertySources.get(added.name()) != added.source()) {
            return true;
        }
        List<Added> read = leftToRead;
        if (read != null) {
            addLast(read, added);
            return true;
        }
        return deferToRefresh(added.name(), added.source());
    }

    @Override
    public Environment removePropertySource(PropertySource propertySource) {
        synchronized (propertySources) {
            propertySources.remove(propertySource.getName());
            noteChangeWhileRefreshing(propertySource.getName(), null);
        }
        if (isRunning() && !reading.get()) {
            propertyPlaceholderResolver.resetCachesAndRememberedValues();
        }
        return this;
    }

    /**
     * Notes, while a refresh runs, what a name is registered with after a change. Called with the
     * monitor of {@link #propertySources} held.
     *
     * @param name   The name of the property source
     * @param source The property source registered by that name, {@code null} when it was removed
     */
    private void noteChangeWhileRefreshing(String name, @Nullable PropertySource source) {
        RefreshChanges changes = changedWhileRefreshing;
        if (changes != null) {
            changes.note(name, source);
        }
    }

    @Override
    public Environment addPackage(String pkg) {
        if (!this.packages.contains(pkg)) {
            this.packages.add(pkg);
        }
        return this;
    }

    @Override
    public Environment addConfigurationExcludes(@Nullable String... names) {
        if (names != null) {
            configurationExcludes.addAll(Arrays.asList(names));
        }
        return this;
    }

    @Override
    public Environment addConfigurationIncludes(String... names) {
        if (names != null) {
            configurationIncludes.addAll(Arrays.asList(names));
        }
        return this;
    }

    @Override
    public Collection<String> getPackages() {
        return Collections.unmodifiableCollection(packages);
    }

    @Override
    public Set<String> getActiveNames() {
        return this.names;
    }

    @Override
    public Collection<PropertySource> getPropertySources() {
        // A copy taken under the monitor of the map: property sources are added and removed while
        // the environment runs, and iterating a view of the map would then fail
        synchronized (propertySources) {
            return List.copyOf(propertySources.values());
        }
    }

    @Override
    public Environment start() {
        if (running.compareAndSet(false, true)) {
            LOG.debug("Starting environment {} for active names {}", this, getActiveNames());
            readProperties(null);
        }
        return this;
    }

    /**
     * Reads the property sources, unless a read is in progress, as it did. In one step with starting
     * to read, a refresh stops counting as about to read, and what was left to it is read as
     * registered. Property sources left to a start that fails are processed on top, as the threads
     * adding them would have; a refresh that fails does so once it has failed.
     *
     * @param refresh The refresh that reads, {@code null} for a start
     */
    private void readProperties(@Nullable RefreshAnnouncement refresh) {
        List<Added> left;
        List<Added> none = new ArrayList<>(4);
        synchronized (propertySources) {
            if (refresh != null) {
                opened(refresh);
            }
            left = deferredToRefresh;
            // the last call before the try: nothing that could fail comes between the two
            if (!reading.compareAndSet(false, true)) {
                return;
            }
            deferredToRefresh = none;
            leftToRead = left;
            if (refresh != null) {
                refresh.leftToRead = left;
            }
        }
        boolean completed = false;
        try {
            loadProperties();
            completed = true;
        } finally {
            synchronized (propertySources) {
                // first, and without a call that could fail on a stack that is nearly used up
                leftToRead = null;
                reading.set(false);
            }
            if (!completed && refresh == null) {
                processLeftOver(left, false);
            }
        }
    }

    private void dropProperties() {
        for (PropertySource refreshable : refreshablePropertySources) {
            propertySources.remove(refreshable.getName());
        }
        propertyPlaceholderResolver.reset();
    }

    private void refreshProperties(RefreshAnnouncement refresh) {
        // Rebuild into a replacement catalog and publish it in one step, so a concurrent lookup
        // sees the properties either as they were before the refresh or as they are after it.
        propertyPlaceholderResolver.refresh(() -> {
            Map<String, PropertySource> previousSources;
            RefreshChanges changes;
            synchronized (propertySources) {
                previousSources = new LinkedHashMap<>(propertySources);
                changes = new RefreshChanges(previousSources);
                changedWhileRefreshing = changes;
            }
            List<PropertySource> previousRefreshable = new ArrayList<>(refreshablePropertySources);
            boolean completed = false;
            try {
                dropProperties();
                readProperties(refresh);
                completed = true;
            } finally {
                synchronized (propertySources) {
                    changedWhileRefreshing = null;
                    if (!completed) {
                        // The resolver keeps the catalog it published when the rebuild fails. Put back the
                        // property sources that catalog was built from, so the next refresh drops and reads
                        // the same sources again, and then what addPropertySource and removePropertySource
                        // changed while the failed refresh ran, so that those changes are not undone.
                        propertySources.clear();
                        propertySources.putAll(previousSources);
                        changes.applyTo(propertySources);
                    }
                }
                if (!completed) {
                    refreshablePropertySources.clear();
                    refreshablePropertySources.addAll(previousRefreshable);
                }
            }
        });
        refresh.published = true;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Environment stop() {
        running.set(false);
        reading.set(false);
        dropProperties();
        if (mutableConversionService instanceof DefaultMutableConversionService defaultMutableConversionService) {
            defaultMutableConversionService.reset();
        }
        return this;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        RefreshAnnouncement refresh = new RefreshAnnouncement();
        try {
            announceRefresh(refresh);
            return propertyPlaceholderResolver.diff(() -> refreshProperties(refresh));
        } finally {
            synchronized (propertySources) {
                // first, and without a call that could fail on a stack that is nearly used up
                if (refresh.announced && !refresh.opened) {
                    refresh.opened = true;
                    refreshesBeforeRead--;
                }
            }
            endRefresh(refresh);
        }
    }

    @Override
    public Environment refresh() {
        RefreshAnnouncement refresh = new RefreshAnnouncement();
        try {
            announceRefresh(refresh);
            refreshProperties(refresh);
        } finally {
            synchronized (propertySources) {
                // first, and without a call that could fail on a stack that is nearly used up
                if (refresh.announced && !refresh.opened) {
                    refresh.opened = true;
                    refreshesBeforeRead--;
                }
            }
            endRefresh(refresh);
        }
        return this;
    }

    /**
     * Announces a refresh before it takes the lock of the catalogs: from now on, a property source
     * added outside a read is left to it, so no thread that adds one waits for the refresh. It first
     * waits for the threads that are processing one they added outside a read, which hold or are
     * about to take that lock, unless this thread holds it already, as one whose property source
     * refreshes the environment while it is processed does.
     *
     * @param refresh The refresh
     */
    private void announceRefresh(RefreshAnnouncement refresh) {
        synchronized (propertySources) {
            refreshesBeforeRead++;
            refresh.announced = true;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ANNOUNCED_WAIT_LIMIT_MILLIS);
            while (processing > 0 && !propertyPlaceholderResolver.holdsCatalogLock()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    // goes on as it did: a thread still processing then waits for this refresh
                    break;
                }
                try {
                    // bounded, so that a notification a thread could not send is not waited for
                    propertySources.wait(Math.max(1, Math.min(ANNOUNCED_WAIT_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining))));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Stops counting a refresh as about to read, once it has started to read or has ended without
     * reading. Called with the monitor of {@link #propertySources} held.
     *
     * @param refresh The refresh
     */
    private void opened(RefreshAnnouncement refresh) {
        if (refresh.announced && !refresh.opened) {
            refresh.opened = true;
            refreshesBeforeRead--;
        }
    }

    /**
     * Ends a refresh, once it holds no lock and no longer counts as about to read. What was left to
     * it is processed on top, as the threads that added it would have, if it did not publish what it
     * read, and so is what was left to refreshes that ended without reading, once none is about to.
     *
     * @param refresh The refresh
     */
    private void endRefresh(RefreshAnnouncement refresh) {
        List<Added> left = refresh.published ? null : refresh.leftToRead;
        processLeftOver(left != null ? left : List.of(), true);
    }

    /**
     * Processes on top of the properties, in the order they were added, property sources added
     * outside a read that the threads adding them left to a read which has not published them, as
     * those threads would have, and, if asked to and no refresh is about to read, those left to
     * refreshes that ended without reading. If a refresh is about to read, or a read is in progress,
     * they are left to that read instead, which reads them as registered. This throws nothing that
     * would replace the failure of the read: one that fails is left out with a warning, and to the
     * next refresh.
     *
     * @param left         The property sources left to a read that has not published them
     * @param withDeferred Whether to take those left to refreshes that ended without reading
     */
    private void processLeftOver(List<Added> left, boolean withDeferred) {
        List<Added> sources = new ArrayList<>(left);
        if (withDeferred) {
            List<Added> none = new ArrayList<>(4);
            synchronized (propertySources) {
                if (refreshesBeforeRead == 0 && !deferredToRefresh.isEmpty()) {
                    // swapped in one step, so that a failure from here on loses nothing that is left
                    List<Added> deferred = deferredToRefresh;
                    deferredToRefresh = none;
                    sources.addAll(deferred);
                }
            }
        }
        if (sources.isEmpty()) {
            return;
        }
        // asked for before this thread counts itself as processing, as the threads adding them did
        List<PropertySource.@Nullable PropertyConvention> conventions = new ArrayList<>(sources.size());
        for (Added added : sources) {
            conventions.add(conventionOf(added));
        }
        boolean[] counted = new boolean[1];
        try {
            synchronized (propertySources) {
                if (leftToRead != null || refreshesBeforeRead > 0) {
                    for (Added added : sources) {
                        handOver(added);
                    }
                    return;
                }
                processing++;
                counted[0] = true;
            }
            for (int i = 0; i < sources.size(); i++) {
                PropertySource.PropertyConvention convention = conventions.get(i);
                if (convention != null) {
                    processOnTop(sources.get(i), convention);
                }
            }
        } finally {
            synchronized (propertySources) {
                // first, and without a call that could fail on a stack that is nearly used up
                if (counted[0]) {
                    counted[0] = false;
                    processing--;
                }
                propertySources.notifyAll();
            }
        }
    }

    /**
     * @param added A property source added outside a read
     * @return Its convention, {@code null} if asking for it failed, which is logged
     */
    private PropertySource.@Nullable PropertyConvention conventionOf(Added added) {
        try {
            return added.source().getConvention();
        } catch (Throwable e) {
            leftOut(added, e);
            return null;
        }
    }

    /**
     * Processes a property source on top of the properties, as the thread that added it would have,
     * if it is still registered when this thread holds the lock of the catalogs. One replaced since
     * is not: what replaced it is processed by the thread that added it, or by the next refresh,
     * and its values must not be overwritten with older ones.
     *
     * @param added      The property source
     * @param convention Its convention
     */
    private void processOnTop(Added added, PropertySource.PropertyConvention convention) {
        try {
            propertyPlaceholderResolver.withCatalogLock(() -> {
                synchronized (propertySources) {
                    if (propertySources.get(added.name()) != added.source()) {
                        return;
                    }
                }
                propertyPlaceholderResolver.resetCachesAndRememberedValues();
                LOG.debug("Processing property source: {} convention: {}", added.name(), convention);
                propertyPlaceholderResolver.processPropertySource(added.source(), convention);
            });
        } catch (Throwable e) {
            leftOut(added, e);
        }
    }

    /**
     * Logs that a property source left to a read failed to be processed after it, without letting
     * the logging fail in its place: the message of the failure is the code of the property source.
     *
     * @param added The property source
     * @param e     The failure
     */
    private static void leftOut(Added added, Throwable e) {
        if (e instanceof VirtualMachineError error && !(e instanceof StackOverflowError)) {
            throw error;
        }
        try {
            LOG.warn("Property source [{}] failed to be processed; the next refresh reads it again", added.name(), e);
        } catch (Throwable ignored) {
            // the failure of the property source is not described, rather than fail in its place
        }
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        return resourceLoader.getResourceAsStream(path);
    }

    @Override
    public Optional<URL> getResource(String path) {
        return resourceLoader.getResource(path);
    }

    @Override
    public Stream<URL> getResources(String path) {
        return resourceLoader.getResources(path);
    }

    @Override
    public boolean supportsPrefix(String path) {
        return resourceLoader.supportsPrefix(path);
    }

    @Override
    public ResourceLoader forBase(String basePath) {
        return resourceLoader.forBase(basePath);
    }

    private void loadProperties() {
        refreshablePropertySources.clear();
        List<PropertySource> propertySources = new ArrayList<>(configLocations.size());
        Set<String> preExistingSourceNames;
        synchronized (this.propertySources) {
            // copied under the monitor: another thread can add or remove a property source meanwhile
            preExistingSourceNames = new LinkedHashSet<>(this.propertySources.keySet());
        }
        if (configuration.isEnableDefaultPropertySources()) {
            propertySources.addAll(readPropertySourceList(applicationName));
            List<PropertySource> defaultPropertySources = new ArrayList<>(4);
            addDefaultPropertySources(defaultPropertySources);
            propertySources.addAll(defaultPropertySources);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
            }
            String propertySourcesEnv = CachedEnvironment.getenv(ENV_PROPERTY_SOURCES_KEY);
            if (propertySourcesEnv != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
            }

        } else {
            propertySources = new ArrayList<>(this.propertySources.size());
        }
        for (PropertySource propertySource : getPropertySources()) {
            if (!propertySources.contains(propertySource)) {
                propertySources.add(propertySource);
            }
        }

        for (PropertySource propertySource : propertySources) {
            internalAddPropertySource(propertySource);
        }

        if (!propertySourcesLocators.isEmpty()) {
            for (PropertySourcesLocator propertySourcesLocator : propertySourcesLocators) {
                boolean isBootstrapLocator = propertySourcesLocator instanceof BootstrapLocatorMarker;
                Collection<PropertySource> sources = propertySourcesLocator.load(this);
                propertySources.addAll(sources);
                if (isBootstrapLocator) {
                    for (PropertySource source : sources) {
                        internalAddPropertySource(source);
                    }
                }
            }
        }

        for (PropertySource propertySource : propertySources) {
            if (!PropertySource.CONTEXT.equals(propertySource.getName()) && !preExistingSourceNames.contains(propertySource.getName())) {
                refreshablePropertySources.add(propertySource);
            }
        }

        OrderUtil.sortOrdered(propertySources);
        for (PropertySource propertySource : propertySources) {
            internalProcessPropertySource(propertySource);
        }
    }

    private void internalAddPropertySource(PropertySource propertySource) {
        propertySources.put(propertySource.getName(), propertySource);
    }

    private void internalProcessPropertySource(PropertySource propertySource) {
        LOG.debug("Processing property source: {} convention: {}", propertySource.getName(), propertySource.getConvention());
        propertyPlaceholderResolver.processPropertySource(propertySource, propertySource.getConvention());
    }

    /**
     * Resolve the property sources for files passed via system property and system env.
     *
     * @param files The comma separated list of files
     * @return The list of property sources for each file
     */
    private List<PropertySource> readPropertySourceListFromFiles(@Nullable String files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<String> list = List.of(files.split(FILE_SEPARATOR));
        if (list.isEmpty()) {
            return List.of();
        }
        getPropertySourceLoaders();
        int order = AbstractPropertySourceLoader.DEFAULT_POSITION + 50;
        List<PropertySource> propertySources = new ArrayList<>(list.size());
        for (String filePath : list) {
            String extension = NameUtils.extension(filePath);
            String fileName = NameUtils.filename(filePath);
            PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
            if (propertySourceLoader != null) {
                LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                Optional<Map<String, Object>> properties = readPropertiesFromLoader(fileName, filePath, propertySourceLoader);
                if (properties.isPresent()) {
                    PropertySource.Origin sourceOrigin;
                    if (filePath.startsWith("classpath:") || filePath.startsWith(FILE_PREFIX)) {
                        sourceOrigin = PropertySource.Origin.of(filePath);
                    } else {
                        sourceOrigin = PropertySource.Origin.of(FILE_PREFIX + filePath);
                    }
                    propertySources.add(PropertySource.of(filePath, properties.get(), sourceOrigin, order));
                }
                order++;
            } else {
                throw new ConfigurationException("Unsupported properties file format while reading " + fileName + "." + extension + " from " + filePath);
            }
        }
        return propertySources;
    }

    /**
     * @param name The name to resolver property sources
     * @return The list of property sources
     */
    private List<PropertySource> readPropertySourceList(String name) {
        List<PropertySource> propertySources = new ArrayList<>(configLocations.size());
        for (String configLocation : configLocations) {
            ResourceLoader resourceLoader;
            if (configLocation.equals("classpath:/")) {
                resourceLoader = this;
            } else if (configLocation.startsWith("classpath:")) {
                resourceLoader = this.forBase(configLocation);
            } else  if (configLocation.startsWith(FILE_PREFIX)) {
                configLocation = configLocation.substring(FILE_PREFIX.length());
                Path configLocationPath = Paths.get(configLocation);
                if (Files.exists(configLocationPath) && Files.isDirectory(configLocationPath) && Files.isReadable(configLocationPath)) {
                    resourceLoader = new DefaultFileSystemResourceLoader(configLocationPath);
                } else {
                    continue; // Skip not existing config location
                }
            } else {
                throw new ConfigurationException("Unsupported config location format: " + configLocation);
            }
            readPropertySourceList(name, resourceLoader, propertySources);
        }
        return propertySources;
    }

    private void readPropertySourceList(String name, ResourceLoader resourceLoader, List<PropertySource> propertySources) {
        Collection<PropertySourceLoader> propertySourceLoaders = getPropertySourceLoaders();
        if (propertySourceLoaders.isEmpty()) {
            loadPropertySourceFromLoader(name, new PropertiesPropertySourceLoader(), propertySources, resourceLoader);
        } else {
            for (PropertySourceLoader propertySourceLoader : propertySourceLoaders) {
                LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources, resourceLoader);
            }
        }
    }

    /**
     * Adds default property sources.
     *
     * @param propertySources The list of property sources
     */
    private void addDefaultPropertySources(List<PropertySource> propertySources) {
        if (!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if (configuration.isEnvironmentPropertySource() && !this.propertySources.containsKey(EnvironmentPropertySource.NAME)) {
            List<String> includes = configuration.getEnvironmentVariableIncludes();
            List<String> excludes = configuration.getEnvironmentVariableExcludes();
            if (this.names.contains(Environment.KUBERNETES)) {
                propertySources.add(new KubernetesEnvironmentPropertySource(includes, excludes));
            } else {
                propertySources.add(new EnvironmentPropertySource(includes, excludes));
            }
        }
    }

    @Override
    public Optional<PropertyEntry> getPropertyEntry(String name) {
        for (PropertyCatalog propertyCatalog : PropertyCatalog.values()) {
            Map<String, DefaultPropertyEntry> entries = propertyPlaceholderResolver.resolveEntriesForKey(name, false, propertyCatalog);
            if (entries != null) {
                DefaultPropertyEntry entry = entries.get(name);
                if (entry != null) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Obtains the {@link PropertySourceLoader} instances.
     *
     * @return A collection of {@link PropertySourceLoader}
     */
    @Override
    public Collection<PropertySourceLoader> getPropertySourceLoaders() {
        Collection<PropertySourceLoader> currentPropertySourceLoaders = this.propertySourceLoaderList;
        if (currentPropertySourceLoaders == null) {
            synchronized (this) { // double check
                currentPropertySourceLoaders = this.propertySourceLoaderList;
                if (currentPropertySourceLoaders == null) {
                    currentPropertySourceLoaders = evaluatePropertySourceLoaders();
                    this.propertySourceLoaderList = currentPropertySourceLoaders;
                }
            }
        }
        return currentPropertySourceLoaders;
    }

    /**
     * Drops the property source loaders, so that the next read of the property sources creates new
     * instances. The bootstrap context, which refreshes its environment rather than being created
     * again, calls this before each refresh: a loader then sees a refresh as it did when every read
     * of the bootstrap configuration had a new environment, and one that returns its property source
     * only once keeps returning it.
     */
    void resetPropertySourceLoaders() {
        synchronized (this) {
            propertySourceLoaderList = null;
            loaderByFormatMap.clear();
        }
    }

    private Collection<PropertySourceLoader> evaluatePropertySourceLoaders() {
        SoftServiceLoader<PropertySourceLoader> definitions = SoftServiceLoader.load(PropertySourceLoader.class, getClassLoader());
        List<PropertySourceLoader> propertySourceLoaders = definitions.collectAll();
        Collection<PropertySourceLoader> allLoaders = CollectionUtils.concat(DEFAULT_PROPERTY_SOURCE_LOADERS, propertySourceLoaders);
        for (Iterator<PropertySourceLoader> iterator = allLoaders.iterator(); iterator.hasNext(); ) {
            PropertySourceLoader propertySourceLoader = iterator.next();
            if (!propertySourceLoader.isEnabled()) {
                iterator.remove();
                continue;
            }
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String extension : extensions) {
                loaderByFormatMap.put(extension, propertySourceLoader);
            }
        }
        return List.copyOf(allLoaders);
    }

    Optional<PropertySource> loadImportedPropertySource(ResourceLoader resourceLoader,
                                                        String resourcePath,
                                                        String sourceName,
                                                        PropertySource.Origin origin) {
        String extension = NameUtils.extension(resourcePath);
        String resourceName = NameUtils.filename(resourcePath);
        if (!extension.isEmpty()) {
            getPropertySourceLoaders();
            PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
            if (propertySourceLoader == null) {
                throw new ConfigurationException("Unsupported properties file format while reading " + resourceName + " from " + resourcePath);
            }
            return readImportedPropertySource(resourceLoader, resourcePath, sourceName, origin, propertySourceLoader);
        }

        Collection<PropertySourceLoader> loaders = getPropertySourceLoaders();
        if (loaders.isEmpty()) {
            loaders = List.of(new PropertiesPropertySourceLoader());
        }
        for (PropertySourceLoader propertySourceLoader : loaders) {
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String candidateExtension : extensions) {
                String candidatePath = resourcePath + "." + candidateExtension;
                Optional<PropertySource> imported = readImportedPropertySource(resourceLoader, candidatePath, sourceName, origin, propertySourceLoader);
                if (imported.isPresent()) {
                    return imported;
                }
            }
        }
        return Optional.empty();
    }

    Optional<PropertySource> loadImportedClasspathPropertySource(String resourcePath,
                                                                 String sourceName,
                                                                 PropertySource.Origin origin,
                                                                 boolean allowMultiple) {
        String extension = NameUtils.extension(resourcePath);
        String resourceName = NameUtils.filename(resourcePath);
        if (!extension.isEmpty()) {
            getPropertySourceLoaders();
            PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
            if (propertySourceLoader == null) {
                throw new ConfigurationException("Unsupported properties file format while reading " + resourceName + " from " + resourcePath);
            }
            return readImportedClasspathPropertySource(resourcePath, sourceName, origin, propertySourceLoader, allowMultiple);
        }

        Collection<PropertySourceLoader> loaders = getPropertySourceLoaders();
        if (loaders.isEmpty()) {
            loaders = List.of(new PropertiesPropertySourceLoader());
        }
        for (PropertySourceLoader propertySourceLoader : loaders) {
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String candidateExtension : extensions) {
                String candidatePath = resourcePath + "." + candidateExtension;
                Optional<PropertySource> imported = readImportedClasspathPropertySource(candidatePath, sourceName, origin, propertySourceLoader, allowMultiple);
                if (imported.isPresent()) {
                    return imported;
                }
            }
        }
        return Optional.empty();
    }

    Optional<PropertySource> loadImportedPropertySourceFromContent(String content,
                                                                   String sourceName,
                                                                   String extension,
                                                                   PropertySource.Origin origin) {
        getPropertySourceLoaders();
        PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
        if (propertySourceLoader == null) {
            if (!extension.isEmpty()) {
                throw new ConfigurationException("Unsupported properties content format while reading " + sourceName + "." + extension);
            }
            return Optional.empty();
        }
        try (InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            Map<String, Object> values = propertySourceLoader.read(sourceName, in);
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(PropertySource.of(sourceName, values, origin));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read properties content for source: " + sourceName + "." + extension, e);
        }
    }

    private Optional<PropertySource> readImportedPropertySource(ResourceLoader resourceLoader,
                                                                String resourcePath,
                                                                String sourceName,
                                                                PropertySource.Origin origin,
                                                                PropertySourceLoader propertySourceLoader) {
        try {
            Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(resourcePath);
            if (inputStream.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = inputStream.get()) {
                Map<String, Object> values = propertySourceLoader.read(sourceName, in);
                if (values.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(PropertySource.of(sourceName, values, origin));
            }
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read imported config: " + resourcePath, e);
        }
    }

    private Optional<PropertySource> readImportedClasspathPropertySource(String resourcePath,
                                                                         String sourceName,
                                                                         PropertySource.Origin origin,
                                                                         PropertySourceLoader propertySourceLoader,
                                                                         boolean allowMultiple) {
        ResourceLoadStrategy strategy = ResourceLoadStrategy.builder()
            .type(allowMultiple ? ResourceLoadStrategyType.MERGE_ALL : ResourceLoadStrategyType.FAIL_ON_DUPLICATE)
            .build();
        List<URL> resources;
        try {
            resources = ClassPathResourceLoader.resolveResources(this, resourcePath, strategy);
        } catch (ResourceDuplicateException e) {
            throw new ConfigurationException(buildDuplicateClasspathImportMessage(resourcePath, e.getResources()));
        } catch (ResourceConflictException e) {
            if (!allowMultiple) {
                throw new ConfigurationException(buildDuplicateClasspathImportMessage(resourcePath, e.getResources()));
            }
            resources = e.getResources();
        }
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        if (!allowMultiple && resources.size() > 1) {
            throw new ConfigurationException(buildDuplicateClasspathImportMessage(resourcePath, resources));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (URL resource : resources) {
            try (InputStream in = resource.openStream()) {
                values.putAll(propertySourceLoader.read(sourceName, in));
            } catch (IOException e) {
                throw new ConfigurationException("Failed to read imported config: " + resourcePath, e);
            }
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(sourceName, values, origin));
    }

    private static String buildDuplicateClasspathImportMessage(String resourcePath, List<URL> resources) {
        StringBuilder message = new StringBuilder("Multiple classpath resources found for import '")
            .append(resourcePath)
            .append("'. Use classpath*:// to load all matches.\nFound:");
        for (URL resource : resources) {
            message.append("\n - ").append(resource.toExternalForm());
        }
        return message.toString();
    }

    private void loadPropertySourceFromLoader(String name, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources, ResourceLoader resourceLoader) {
        ResourceLoadStrategy strategy = configuration.getConfigurationLoadingStrategy();

        Optional<PropertySource> defaultPropertySource = loadPropertySourceFromLoader(name, propertySourceLoader, resourceLoader, strategy);
        defaultPropertySource.ifPresent(propertySources::add);

        Set<String> activeNames = getActiveNames();
        int i = 0;
        for (String activeName : activeNames) {
            ActiveEnvironment activeEnvironment = ActiveEnvironment.of(activeName, i);
            Optional<PropertySource> propertySource = loadPropertySourceFromLoader(name, propertySourceLoader, resourceLoader, activeEnvironment, strategy);
            propertySource.ifPresent(propertySources::add);
            i++;
        }
    }

    private Optional<PropertySource> loadPropertySourceFromLoader(String name,
                                                                   PropertySourceLoader propertySourceLoader,
                                                                   ResourceLoader resourceLoader,
                                                                   ResourceLoadStrategy strategy) {
        if (propertySourceLoader instanceof AbstractPropertySourceLoader abstractPropertySourceLoader) {
            return loadPropertySourceFromAbstractLoader(name, abstractPropertySourceLoader, resourceLoader, abstractPropertySourceLoader.getOrder(), strategy);
        }
        return propertySourceLoader.load(name, resourceLoader);
    }

    private Optional<PropertySource> loadPropertySourceFromLoader(String name,
                                                                   PropertySourceLoader propertySourceLoader,
                                                                   ResourceLoader resourceLoader,
                                                                   ActiveEnvironment activeEnvironment,
                                                                   ResourceLoadStrategy strategy) {
        if (propertySourceLoader instanceof AbstractPropertySourceLoader abstractPropertySourceLoader) {
            String envName = name + "-" + activeEnvironment.getName();
            int order = abstractPropertySourceLoader.getOrder() + 1 + activeEnvironment.getPriority();
            return loadPropertySourceFromAbstractLoader(envName, abstractPropertySourceLoader, resourceLoader, order, strategy);
        }
        return propertySourceLoader.loadEnv(name, resourceLoader, activeEnvironment);
    }

    private Optional<PropertySource> loadPropertySourceFromAbstractLoader(String fileName,
                                                                          AbstractPropertySourceLoader propertySourceLoader,
                                                                          ResourceLoader resourceLoader,
                                                                          int order,
                                                                          ResourceLoadStrategy strategy) {
        if (!propertySourceLoader.isEnabled()) {
            return Optional.empty();
        }

        final boolean needsAllResources = strategy.type() == ResourceLoadStrategyType.MERGE_ALL
            || strategy.type() == ResourceLoadStrategyType.FAIL_ON_DUPLICATE
            || (strategy.type() == ResourceLoadStrategyType.FIRST_MATCH && strategy.warnOnDuplicates());

        String[] extensions = propertySourceLoader.getExtensions().toArray(new String[0]);
        if (!needsAllResources) {
            // FIRST_MATCH with warnOnDuplicates=false: use first resource without enumerating all
            for (String ext : extensions) {
                String fileExt = fileName + "." + ext;
                Optional<InputStream> config = propertySourceLoader.readInput(resourceLoader, fileExt);
                if (config.isPresent()) {
                    try (InputStream input = config.get()) {
                        Map<String, Object> merged = propertySourceLoader.read(fileName, input);
                        if (!merged.isEmpty()) {
                            return Optional.of(propertySourceLoader.createPropertySource(fileName, merged, order, PropertySource.Origin.of(fileExt)));
                        }
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "]: " + e.getMessage(), e);
                    }
                }
            }
            return Optional.empty();
        }

        Map<String, List<URL>> extensionResources = new LinkedHashMap<>(extensions.length);
        Set<String> mergeExtensions = new HashSet<>();
        int extensionsWithResources = 0;

        for (String ext : extensions) {
            String fileExt = fileName + "." + ext;
            List<URL> urls;
            try {
                urls = ClassPathResourceLoader.resolveResources(resourceLoader, fileExt, strategy);
            } catch (ResourceDuplicateException e) {
                throw new ConfigurationException(buildDuplicateConfigurationMessage(e.getResourceName(), e.getResources()));
            } catch (ResourceConflictException e) {
                urls = e.getResources();
                mergeExtensions.add(ext);
            }
            extensionResources.put(ext, urls);
            if (!urls.isEmpty()) {
                extensionsWithResources++;
            }
        }

        if (extensionsWithResources > 1 && strategy.type() != ResourceLoadStrategyType.MERGE_ALL) {
            List<URL> allUrls = new ArrayList<>();
            for (List<URL> urls : extensionResources.values()) {
                allUrls.addAll(urls);
            }
            if (!allUrls.isEmpty() && strategy.type() == ResourceLoadStrategyType.FAIL_ON_DUPLICATE) {
                throw new ConfigurationException(buildDuplicateConfigurationMessage(fileName, allUrls));
            }
        }

        for (String ext : extensions) {
            String fileExt = fileName + "." + ext;
            List<URL> urls = Objects.requireNonNullElse(extensionResources.get(ext), Collections.emptyList());
            Map<String, Object> merged = Collections.emptyMap();

            if (urls.isEmpty()) {
                Optional<InputStream> config = propertySourceLoader.readInput(resourceLoader, fileExt);
                if (config.isPresent()) {
                    try (InputStream input = config.get()) {
                        merged = propertySourceLoader.read(fileName, input);
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "]: " + e.getMessage(), e);
                    }
                }
            } else if (mergeExtensions.contains(ext) && urls.size() > 1) {
                List<URL> orderedUrls = urls;
                if (!strategy.mergeOrder().isEmpty()) {
                    orderedUrls = orderByArtifactPatterns(urls, strategy.mergeOrder());
                }

                if (LOG.isInfoEnabled()) {
                    LOG.info("Merging configuration resources '{}' in order: {}", fileExt, orderedUrls);
                }

                Map<String, Object> mergedMap = new LinkedHashMap<>(64);
                for (URL url : orderedUrls) {
                    try (InputStream input = url.openStream()) {
                        mergedMap.putAll(propertySourceLoader.read(fileName, input));
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "] from [" + url + "]: " + e.getMessage(), e);
                    }
                }
                merged = mergedMap;
            } else {
                Optional<InputStream> config = propertySourceLoader.readInput(resourceLoader, fileExt);
                if (config.isPresent()) {
                    try (InputStream input = config.get()) {
                        merged = propertySourceLoader.read(fileName, input);
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "]: " + e.getMessage(), e);
                    }
                }
            }

            if (!merged.isEmpty()) {
                return Optional.of(propertySourceLoader.createPropertySource(fileName, merged, order, PropertySource.Origin.of(fileExt)));
            }
        }

        return Optional.empty();
    }

    private static String buildDuplicateConfigurationMessage(String resourceName, List<URL> urls) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("Duplicate configuration resource '").append(resourceName).append("' found on the classpath:");
        for (URL url : urls) {
            sb.append("\n - ").append(url.toExternalForm());
        }
        return sb.toString();
    }

    private static List<URL> orderByArtifactPatterns(List<URL> urls, List<String> artifactPatterns) {
        final List<Pattern> patterns = new ArrayList<>(artifactPatterns.size());
        for (String p : artifactPatterns) {
            try {
                patterns.add(Pattern.compile(p));
            } catch (Exception e) {
                throw new ConfigurationException("Invalid mergeOrder regex pattern: " + p, e);
            }
        }

        record IndexedUrl(int originalIndex, int patternIndex, URL url) {
        }

        List<IndexedUrl> indexed = new ArrayList<>(urls.size());
        for (int i = 0; i < urls.size(); i++) {
            URL url = urls.get(i);
            String artifactName = artifactName(url);
            int index = patterns.size();
            for (int p = 0; p < patterns.size(); p++) {
                if (patterns.get(p).matcher(artifactName).matches()) {
                    index = p;
                    break;
                }
            }
            indexed.add(new IndexedUrl(i, index, url));
        }

        indexed.sort(Comparator
            .comparingInt(IndexedUrl::patternIndex)
            .thenComparingInt(IndexedUrl::originalIndex));

        List<URL> ordered = new ArrayList<>(urls.size());
        for (IndexedUrl idx : indexed) {
            ordered.add(idx.url());
        }
        return ordered;
    }

    private static String artifactName(URL url) {
        String external = url.toExternalForm();

        int bangIndex = external.indexOf("!/");
        String withoutEntry = bangIndex > -1 ? external.substring(0, bangIndex) : external;
        if (withoutEntry.startsWith("jar:")) {
            withoutEntry = withoutEntry.substring(4);
        }
        if (withoutEntry.startsWith(FILE_PREFIX)) {
            withoutEntry = withoutEntry.substring(FILE_PREFIX.length());
        }

        int slashIndex = withoutEntry.lastIndexOf('/');
        if (slashIndex > -1 && slashIndex < withoutEntry.length() - 1) {
            return withoutEntry.substring(slashIndex + 1);
        }
        return withoutEntry;
    }

    /**
     * Read the property source.
     *
     * @param fileName             Name of the file to be used as property source name
     * @param filePath             Absolute file path
     * @param propertySourceLoader The appropriate property source loader
     * @throws ConfigurationException If unable to find the appropriate property source loader for the given file
     */
    private Optional<Map<String, Object>> readPropertiesFromLoader(String fileName, String filePath, PropertySourceLoader propertySourceLoader) throws ConfigurationException {
        ResourceLoader loader = new ResourceResolver().getSupportingLoader(filePath)
                .orElse(FileSystemResourceLoader.defaultLoader());
        try {
            Optional<InputStream> inputStream = loader.getResourceAsStream(filePath);
            if (inputStream.isPresent()) {
                return Optional.of(propertySourceLoader.read(fileName, inputStream.get()));
            } else {
                throw new ConfigurationException("Failed to read configuration file: " + filePath);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Unsupported properties file: " + fileName);
        }
    }

    @Override
    public void close() {
        // Everything is closed and the environment is stopped even when something fails to close:
        // the first failure is thrown at the end, with the later ones suppressed by it.
        RuntimeException failure = null;
        try {
            propertyPlaceholderResolver.close();
        } catch (Exception e) {
            failure = new RuntimeException("Failed to close property placeholder resolver: " + e.getMessage(), e);
        }
        for (PropertySourcesLocator propertySourcesLocator : propertySourcesLocators) {
            if (propertySourcesLocator instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    failure = addFailure(failure, new RuntimeException("Failed to close property sources locator " + propertySourcesLocator.getClass() + ": " + e.getMessage(), e));
                }
            }
        }
        try {
            stop();
        } catch (RuntimeException e) {
            failure = addFailure(failure, e);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException addFailure(@Nullable RuntimeException failure, RuntimeException next) {
        if (failure == null) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }

    @Override
    public MutableConversionService getConversionService() {
        return mutableConversionService;
    }

    /**
     * A property source added outside a read, by the name it was added by.
     *
     * @param name   The name
     * @param source The property source
     */
    private record Added(String name, PropertySource source) {
    }

    /**
     * A refresh, from {@link #announceRefresh(RefreshAnnouncement)} until it ends. Guarded by the
     * monitor of the registered property sources.
     */
    private static final class RefreshAnnouncement {
        /** Whether it is counted in {@link #refreshesBeforeRead}. */
        private boolean announced;
        /** Whether it has started to read, or ended without reading, and is no longer counted. */
        private boolean opened;
        /** What was left to it that its read reads as registered, {@code null} if it has not read. */
        private @Nullable List<Added> leftToRead;
        /** Whether it has published what it read. */
        private boolean published;
    }

    /**
     * The property sources added and removed while a refresh runs, which a refresh that fails
     * applies to the property sources it puts back. They end as if each call had been made on
     * those property sources, in the order the calls were made: a property source that replaces
     * one still registered by its name takes its place, and one added under a name that is not
     * registered goes last. It holds one entry per name.
     */
    private static final class RefreshChanges {
        /**
         * The property sources registered when the refresh started.
         */
        private final Map<String, PropertySource> before;
        /**
         * By name, the property source registered after the last change, {@code null} once it was
         * removed. A name added while it was not registered is moved last, so those names are in
         * the order in which they go last.
         */
        private final Map<String, @Nullable PropertySource> changes = new LinkedHashMap<>();
        /**
         * The names added while they were not registered, which go last rather than where they were.
         */
        private final Set<String> appended = new HashSet<>();

        RefreshChanges(Map<String, PropertySource> before) {
            this.before = before;
        }

        /**
         * Notes a change.
         *
         * @param name   The name of the property source
         * @param source The property source registered by that name, {@code null} when it was removed
         */
        void note(String name, @Nullable PropertySource source) {
            boolean registered = changes.containsKey(name) ? changes.get(name) != null : before.containsKey(name);
            if (source == null) {
                changes.put(name, null);
                appended.remove(name);
            } else if (registered) {
                // takes the place of the property source it replaces
                changes.put(name, source);
            } else {
                // goes last
                changes.remove(name);
                changes.put(name, source);
                appended.add(name);
            }
        }

        /**
         * Applies the changes to the property sources the refresh started with.
         *
         * @param propertySources The property sources, as they were when the refresh started
         */
        void applyTo(Map<String, PropertySource> propertySources) {
            changes.forEach((name, source) -> {
                if (source == null) {
                    propertySources.remove(name);
                } else if (!appended.contains(name)) {
                    propertySources.put(name, source);
                }
            });
            changes.forEach((name, source) -> {
                if (source != null && appended.contains(name)) {
                    propertySources.remove(name);
                    propertySources.put(name, source);
                }
            });
        }
    }
}
