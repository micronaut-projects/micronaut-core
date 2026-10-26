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
import io.micronaut.context.env.PropertySourcePropertyResolver.PendingState;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
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
    /**
     * How long a start or a refresh waits for the refresh in progress to end. After that it asks
     * the refresh in progress to run once more, and returns.
     */
    private static final int REFRESH_WAIT_SECONDS = 2;

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

    /**
     * The registered property sources. Its monitor also guards {@link #currentRun},
     * {@link #refreshInProgress}, {@link #loadedSources} and {@link #addsInFlight}. Nothing but
     * work in memory runs while it is held: no property source, loader or locator.
     */
    private final Map<String, PropertySource> propertySources = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Collection<PropertySourcesLocator> propertySourcesLocators;

    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * The current run of the environment. {@link #stop()} replaces it, so a refresh that started
     * before the stop can tell, and publishes nothing.
     */
    private Run currentRun = new Run();
    /**
     * The start or refresh in progress, if any.
     */
    private @Nullable RefreshInProgress refreshInProgress;
    /**
     * The property sources the last refresh loaded itself (configuration files, default sources,
     * bootstrap sources) and registered, by name. The next refresh, and a stop, drop whatever is
     * registered under these names, as 5.2.x did. A name maps to {@code null} when an add or a
     * remove of that name happened during the last refresh: that refresh did not register its own
     * source, and the next one does.
     */
    private Map<String, @Nullable PropertySource> loadedSources = Map.of();
    /**
     * The property source being added under each name, by the latest call. An add publishes only
     * if it is still the latest add of its name when it is done.
     */
    private final Map<String, PropertySource> addsInFlight = new HashMap<>();

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

    /**
     * Adds a property source.
     *
     * <ul>
     * <li>On a stopped environment, or during a refresh that has not taken its snapshot of the
     * added sources yet, it is only registered: the start or the refresh processes it.</li>
     * <li>Otherwise it is processed on this thread, with no lock held, into a pending state built on
     * the published properties. It is then registered and published in one step, unless a later
     * add or a remove of the same name happened meanwhile.</li>
     * <li>During a refresh that has taken its snapshot, it is also kept as a late add: the refresh
     * applies it again, in its order, when it publishes. Not if the refresh processes that same
     * instance itself; it then replaces an earlier late add of its name.</li>
     * </ul>
     *
     * <p>Adding the property source already registered under its name processes it again on top. A
     * configuration watch relies on this: it adds the registered property sources again in their
     * order to keep their precedence. Adding a property source while that same one is being added
     * does nothing.</p>
     *
     * <p>If processing fails, nothing is published and the property source stays registered, so the
     * next refresh processes it. During a refresh the failure is logged, not thrown, as 5.2.x only
     * registered an add made during a refresh.</p>
     *
     * @param propertySource The property source
     * @return This environment
     */
    @Override
    public DefaultEnvironment addPropertySource(PropertySource propertySource) {
        String name = propertySource.getName();
        RefreshInProgress duringRefresh;
        synchronized (propertySources) {
            if (addsInFlight.get(name) == propertySource) {
                return this;
            }
            if (!isRunning() || (refreshInProgress != null && refreshInProgress.addedSnapshot == null)) {
                addsInFlight.remove(name);
                register(name, propertySource);
                return this;
            }
            addsInFlight.put(name, propertySource);
            duringRefresh = refreshInProgress;
        }
        // during a refresh, the values that expressions such as ${random.port} resolved to are kept, as the refresh keeps them
        PendingState pending = propertyPlaceholderResolver.startPendingAdd(duringRefresh != null);
        try {
            internalProcessPropertySource(propertySource);
        } catch (Throwable e) {
            // Throwable, as a property source written in Groovy or Kotlin can throw a checked exception
            synchronized (propertySources) {
                if (addsInFlight.get(name) == propertySource) {
                    addsInFlight.remove(name);
                    register(name, propertySource);
                }
            }
            if (duringRefresh == null) {
                throw e;
            }
            logFailure("Failed to process property source {} added during a refresh", name, e);
            return this;
        } finally {
            propertyPlaceholderResolver.endPending(pending);
        }
        synchronized (propertySources) {
            if (addsInFlight.get(name) != propertySource) {
                // a later add or a remove of the same name happened meanwhile
                return this;
            }
            addsInFlight.remove(name);
            register(name, propertySource);
            if (isRunning()) {
                propertyPlaceholderResolver.publishAdd(pending);
                RefreshInProgress refresh = refreshInProgress;
                if (refresh != null && refresh.addedSnapshot != null) {
                    // the latest add of a name decides; re-inserted, so the late adds stay in the order they were made
                    refresh.lateAdds.remove(name);
                    if (!refresh.processesItself(name, propertySource)) {
                        refresh.lateAdds.put(name, pending);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Removes the property source registered under the name of the given one. Its properties stay
     * until the next refresh.
     *
     * @param propertySource The property source
     * @return This environment
     */
    @Override
    public Environment removePropertySource(PropertySource propertySource) {
        String name = propertySource.getName();
        boolean reset;
        synchronized (propertySources) {
            propertySources.remove(name);
            addsInFlight.remove(name);
            RefreshInProgress refresh = refreshInProgress;
            if (refresh != null) {
                refresh.namesChanged.add(name);
                refresh.lateAdds.remove(name);
            }
            reset = isRunning() && refresh == null;
        }
        if (reset) {
            propertyPlaceholderResolver.resetCachesAndRememberedValues();
        }
        return this;
    }

    /**
     * Registers an added property source. Called with the monitor of {@link #propertySources} held.
     *
     * @param name           The name of the property source
     * @param propertySource The property source
     */
    private void register(String name, PropertySource propertySource) {
        if (propertySources.get(name) != propertySource) {
            propertySources.put(name, propertySource);
            if (refreshInProgress != null) {
                refreshInProgress.namesChanged.add(name);
            }
        }
    }

    /**
     * Logs a failure that is not thrown to the caller. A failure that cannot be described, whose
     * message fails for one, is not let out by the logging.
     *
     * @param message The message, with a placeholder for the argument
     * @param argument The argument
     * @param failure The failure
     */
    private static void logFailure(String message, Object argument, Throwable failure) {
        try {
            LOG.error(message, argument, failure);
        } catch (RuntimeException e) {
            // the failure cannot be described
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

    /**
     * The registered property sources. On the thread of a refresh in progress, they are the ones
     * that refresh sees, see {@link #sourcesSeenBy(RefreshInProgress)}.
     *
     * @return A copy of the property sources
     */
    @Override
    public Collection<PropertySource> getPropertySources() {
        // A copy taken under the monitor of the map: property sources are added and removed while
        // the environment runs, and iterating a view of the map would then fail
        synchronized (propertySources) {
            RefreshInProgress refresh = refreshInProgress;
            if (refresh != null && refresh.thread == Thread.currentThread()) {
                return List.copyOf(sourcesSeenBy(refresh).values());
            }
            return List.copyOf(propertySources.values());
        }
    }

    @Override
    public Environment start() {
        if (running.compareAndSet(false, true)) {
            LOG.debug("Starting environment {} for active names {}", this, getActiveNames());
            refreshProperties(true);
        }
        return this;
    }

    @Override
    public Environment refresh() {
        refreshProperties(false);
        return this;
    }

    /**
     * Loads and processes all property sources again. One refresh runs at a time.
     *
     * <p>If the refresh in progress does not end within {@link #REFRESH_WAIT_SECONDS}, the caller
     * asks it to run once more before it ends, and returns. That extra run publishes what the
     * caller asked for. A refresh called on the refreshing thread, by a property source or a
     * locator, asks for the same, at most once per refresh, so that code that refreshes every time
     * it runs cannot keep the refresh going forever.</p>
     *
     * @param starting Whether the environment starts
     */
    private void refreshProperties(boolean starting) {
        Run run;
        synchronized (propertySources) {
            run = currentRun;
        }
        if (run.refreshLock.isHeldByCurrentThread()) {
            if (!run.refreshedFromInside) {
                run.refreshedFromInside = true;
                synchronized (propertySources) {
                    run.refreshRequested = true;
                }
            }
            return;
        }
        if (!lockOrRequestRefresh(run)) {
            return;
        }
        run.refreshedFromInside = false;
        try {
            refreshOnce(run, starting);
        } finally {
            refreshAgainIfRequestedAndUnlock(run);
        }
    }

    /**
     * Takes the refresh lock, waiting at most {@link #REFRESH_WAIT_SECONDS}. If the refresh in
     * progress has not ended by then, it is asked to run once more instead.
     *
     * @param run The run of the environment
     * @return Whether the lock was taken
     */
    private boolean lockOrRequestRefresh(Run run) {
        ReentrantLock refreshLock = run.refreshLock;
        try {
            if (refreshLock.tryLock(REFRESH_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        while (true) {
            synchronized (propertySources) {
                // the refresh in progress checks the request and unlocks under this monitor, so the request cannot be missed
                if (refreshLock.isLocked()) {
                    run.refreshRequested = true;
                    return false;
                }
            }
            // the refresh in progress has just ended
            if (refreshLock.tryLock()) {
                return true;
            }
        }
    }

    /**
     * Refreshes again for as long as another caller has asked for it, then unlocks. A failure is
     * logged, as the callers that asked have already returned.
     *
     * @param run The run of the environment, whose refresh lock the current thread holds
     */
    private void refreshAgainIfRequestedAndUnlock(Run run) {
        while (true) {
            synchronized (propertySources) {
                if (!run.refreshRequested || run != currentRun) {
                    run.refreshRequested = false;
                    run.refreshLock.unlock();
                    return;
                }
                run.refreshRequested = false;
            }
            try {
                refreshOnce(run, false);
            } catch (Throwable e) {
                // Throwable, as a property source written in Groovy or Kotlin can throw a checked exception
                logFailure("Failed to refresh {} again", this, e);
            }
        }
    }

    /**
     * Refreshes once: processes all property sources into a pending state, off to the side, and
     * publishes it in one step. A concurrent lookup sees the properties as they were before or as
     * they are after. A start also publishes after each property source. A refresh that a stop has
     * overtaken publishes and registers nothing. A refresh that fails publishes and registers
     * nothing more.
     *
     * @param run      The run of the environment
     * @param starting Whether the environment starts
     */
    private void refreshOnce(Run run, boolean starting) {
        PendingState pending = propertyPlaceholderResolver.startPendingRefresh();
        try {
            RefreshInProgress refresh;
            synchronized (propertySources) {
                if (run != currentRun) {
                    return;
                }
                refresh = new RefreshInProgress(run, pending, loadedSources);
                refreshInProgress = refresh;
            }
            try {
                loadAndProcess(refresh, starting);
            } finally {
                synchronized (propertySources) {
                    if (refreshInProgress == refresh) {
                        refreshInProgress = null;
                    }
                }
            }
        } finally {
            propertyPlaceholderResolver.endPending(pending);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Stops the environment without waiting for a refresh in progress. When it returns, the
     * properties are empty, and whatever is registered under the names the last refresh loaded
     * itself is unregistered, as 5.2.x did. A refresh in progress publishes and registers nothing.
     *
     * @return This environment
     */
    @Override
    public Environment stop() {
        synchronized (propertySources) {
            running.set(false);
            currentRun = new Run();
            refreshInProgress = null;
            // by name: what an add registered in place of a loaded source goes too, so the next start loads that name again
            loadedSources.keySet().forEach(propertySources::remove);
            loadedSources = Map.of();
            propertyPlaceholderResolver.publishEmpty();
        }
        if (mutableConversionService instanceof DefaultMutableConversionService defaultMutableConversionService) {
            defaultMutableConversionService.reset();
        }
        return this;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        return propertyPlaceholderResolver.diff(this::refresh);
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

    /**
     * Loads the configuration files and the default property sources, runs the locators, takes the
     * snapshot of the added property sources, and processes them all, in their order, into the
     * pending state of the refresh. Then registers what the refresh loaded itself and publishes.
     *
     * @param refresh  The refresh
     * @param starting Whether the environment starts, which publishes after each property source
     */
    private void loadAndProcess(RefreshInProgress refresh, boolean starting) {
        List<PropertySource> loaded = new ArrayList<>(configLocations.size());
        if (configuration.isEnableDefaultPropertySources()) {
            loaded.addAll(readPropertySourceList(applicationName));
            addDefaultPropertySources(refresh, loaded);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                loaded.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
            }
            String propertySourcesEnv = CachedEnvironment.getenv(ENV_PROPERTY_SOURCES_KEY);
            if (propertySourcesEnv != null) {
                loaded.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
            }
        }
        refresh.addLoaded(loaded, propertySources);
        // what a bootstrap locator returns is registered like the configuration files; what other locators return is not
        List<PropertySource> located = new ArrayList<>();
        for (PropertySourcesLocator propertySourcesLocator : propertySourcesLocators) {
            Collection<PropertySource> sources = propertySourcesLocator.load(this);
            located.addAll(sources);
            if (propertySourcesLocator instanceof BootstrapLocatorMarker) {
                refresh.addLoaded(sources, propertySources);
            }
        }

        List<PropertySource> sources = new ArrayList<>(loaded);
        for (PropertySource added : snapshotAdded(refresh)) {
            if (!sources.contains(added)) {
                sources.add(added);
            }
        }
        sources.addAll(located);
        OrderUtil.sortOrdered(sources);
        for (PropertySource propertySource : sources) {
            internalProcessPropertySource(propertySource);
            if (starting) {
                synchronized (propertySources) {
                    if (refresh.run != currentRun) {
                        // stopped during the start
                        return;
                    }
                    publish(refresh);
                }
            }
        }

        synchronized (propertySources) {
            if (refresh.run != currentRun) {
                return;
            }
            // Whatever is registered under a name the last refresh loaded goes, by name, as 5.2.x
            // dropped such names on every refresh, unless it was added or removed during this one.
            refresh.previouslyLoaded.keySet().forEach(name -> {
                if (refresh.reloads(name)) {
                    propertySources.remove(name);
                }
            });
            Map<String, PropertySource> registeredNow = new LinkedHashMap<>();
            refresh.loaded.forEach((name, source) -> {
                boolean remembered = !PropertySource.CONTEXT.equals(name);
                if (refresh.namesChanged.contains(name)) {
                    // What was added or removed under the name during the refresh stays until the
                    // next refresh, which replaces it, as 5.2.x dropped such a name on its next refresh.
                    if (remembered) {
                        registeredNow.put(name, null);
                    }
                } else if (!propertySources.containsKey(name)) {
                    // A property source registered under a name that no refresh loaded stays,
                    // as 5.2.x kept a name registered before a refresh.
                    propertySources.put(name, source);
                    if (remembered) {
                        registeredNow.put(name, source);
                    }
                }
            });
            loadedSources = registeredNow;
            publish(refresh);
        }
    }

    /**
     * The registered property sources as a refresh sees them: without those it replaces, see
     * {@link RefreshInProgress#replaces(String, PropertySource)}, and with those this refresh has
     * loaded so far, which it registers when it ends. Called with the monitor of
     * {@link #propertySources} held.
     *
     * @param refresh The refresh
     * @return The property sources, by name
     */
    private Map<String, PropertySource> sourcesSeenBy(RefreshInProgress refresh) {
        Map<String, PropertySource> seen = new LinkedHashMap<>(propertySources);
        seen.entrySet().removeIf(registered -> refresh.replaces(registered.getKey(), registered.getValue()));
        refresh.loaded.forEach(seen::putIfAbsent);
        return seen;
    }

    /**
     * Takes the snapshot of the added property sources that a refresh processes: the registered
     * ones it does not replace, see {@link RefreshInProgress#replaces(String, PropertySource)}. A
     * property source added after this is a late add, see {@link #addPropertySource(PropertySource)}.
     *
     * @param refresh The refresh
     * @return The added property sources, in the order they are registered
     */
    private List<PropertySource> snapshotAdded(RefreshInProgress refresh) {
        synchronized (propertySources) {
            List<PropertySource> added = new ArrayList<>(propertySources.size());
            propertySources.forEach((name, source) -> {
                if (!refresh.replaces(name, source)) {
                    added.add(source);
                }
            });
            Set<PropertySource> snapshot = Collections.newSetFromMap(new IdentityHashMap<>());
            snapshot.addAll(added);
            refresh.addedSnapshot = snapshot;
            return added;
        }
    }

    /**
     * Publishes what a refresh has processed so far, with its late adds, in their order. Called
     * with the monitor of {@link #propertySources} held.
     *
     * @param refresh The refresh
     */
    private void publish(RefreshInProgress refresh) {
        propertyPlaceholderResolver.publishRefresh(refresh.pending, refresh.lateAdds.values());
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
     * Adds the default property sources that are not registered, as the refresh sees them.
     *
     * @param refresh         The refresh
     * @param propertySources The list of property sources
     */
    private void addDefaultPropertySources(RefreshInProgress refresh, List<PropertySource> propertySources) {
        Map<String, PropertySource> registered;
        synchronized (this.propertySources) {
            registered = sourcesSeenBy(refresh);
        }
        if (!registered.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if (configuration.isEnvironmentPropertySource() && !registered.containsKey(EnvironmentPropertySource.NAME)) {
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
     * Drops the property source loaders, so that the next refresh creates new instances. The
     * bootstrap context, which refreshes its environment rather than being created again, calls
     * this before each refresh: a loader then sees a refresh as it did when every read of the
     * bootstrap configuration had a new environment, and one that returns its property source only
     * once keeps returning it.
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
        // the first failure is thrown at the end, with the later ones suppressed by it. It is stopped
        // first, so that a refresh in progress reads nothing more from a locator closed after.
        RuntimeException failure = null;
        try {
            stop();
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            propertyPlaceholderResolver.close();
        } catch (Exception e) {
            failure = addFailure(failure, new RuntimeException("Failed to close property placeholder resolver: " + e.getMessage(), e));
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
     * One run of the environment, from a start to the stop. A refresh that started in an earlier
     * run publishes and registers nothing.
     */
    private static final class Run {
        /**
         * Held by the refresh in progress, so that one runs at a time.
         */
        private final ReentrantLock refreshLock = new ReentrantLock();
        /**
         * Whether another caller asked the refresh in progress to run once more before it ends.
         * Guarded by the monitor of {@link DefaultEnvironment#propertySources}.
         */
        private boolean refreshRequested;
        /**
         * Whether the refreshing thread has asked for a refresh itself since it took the lock. Only
         * that thread uses it.
         */
        private boolean refreshedFromInside;
    }

    /**
     * A start or a refresh in progress. Its fields that are not final are guarded by the monitor of
     * {@link DefaultEnvironment#propertySources}.
     */
    private static final class RefreshInProgress {
        private final Run run;
        /**
         * The properties being built, which are published when the refresh ends.
         */
        private final PendingState pending;
        /**
         * The refreshing thread. It sees the property sources returned by
         * {@link DefaultEnvironment#sourcesSeenBy(RefreshInProgress)}.
         */
        private final Thread thread = Thread.currentThread();
        /**
         * What the last refresh loaded itself, by name, see {@link DefaultEnvironment#loadedSources}.
         * This refresh drops their names, see {@link #reloads(String)}.
         */
        private final Map<String, @Nullable PropertySource> previouslyLoaded;
        /**
         * What this refresh has loaded itself so far, by name: configuration files, default
         * property sources and what a bootstrap locator returned. Registered when it ends.
         */
        private final Map<String, PropertySource> loaded = new LinkedHashMap<>();
        /**
         * The added property sources that this refresh processes, by identity. {@code null} until
         * the refresh takes this snapshot, see {@link DefaultEnvironment#snapshotAdded(RefreshInProgress)}.
         * Before that, an added property source is only registered, and lands in the snapshot.
         */
        private @Nullable Set<PropertySource> addedSnapshot;
        /**
         * The property sources added after the snapshot, the latest by each name, in the order
         * they were added. The refresh applies them again, in their order, when it publishes.
         */
        private final Map<String, PendingState> lateAdds = new LinkedHashMap<>();
        /**
         * The names under which a property source was added or removed during the refresh. The
         * refresh does not register what it loaded itself under such a name.
         */
        private final Set<String> namesChanged = new HashSet<>();

        private RefreshInProgress(Run run, PendingState pending, Map<String, @Nullable PropertySource> previouslyLoaded) {
            this.run = run;
            this.pending = pending;
            this.previouslyLoaded = previouslyLoaded;
        }

        /**
         * Adds property sources that this refresh has loaded itself. Their names are taken with no
         * lock held, as {@link PropertySource#getName()} is code of the property source.
         *
         * @param sources The property sources
         * @param monitor The monitor that guards the refresh
         */
        private void addLoaded(Collection<PropertySource> sources, Object monitor) {
            Map<String, PropertySource> named = new LinkedHashMap<>();
            for (PropertySource source : sources) {
                named.put(source.getName(), source);
            }
            synchronized (monitor) {
                loaded.putAll(named);
            }
        }

        /**
         * Whether this refresh processes the given property source itself: it is in the snapshot,
         * this refresh loaded it, or the last refresh loaded it and this one loads it again. Called
         * with the monitor of {@link DefaultEnvironment#propertySources} held.
         *
         * @param name   The name of the property source
         * @param source The property source
         * @return Whether the refresh processes it
         */
        private boolean processesItself(String name, PropertySource source) {
            return (addedSnapshot != null && addedSnapshot.contains(source))
                || loaded.get(name) == source
                || previouslyLoaded.get(name) == source;
        }

        /**
         * Whether this refresh drops, when it ends, whatever is registered under the given name:
         * the last refresh loaded that name, and nobody added or removed it during this one. 5.2.x
         * dropped such names on every refresh. Called with the monitor of
         * {@link DefaultEnvironment#propertySources} held.
         *
         * @param name The name
         * @return Whether the refresh drops the name
         */
        private boolean reloads(String name) {
            return previouslyLoaded.containsKey(name) && !namesChanged.contains(name);
        }

        /**
         * Whether this refresh replaces the given registered property source, so it neither sees
         * it nor processes it: it drops its name, see {@link #reloads(String)}, or it is the very
         * instance the last refresh loaded, which this one loads again. Called with the monitor of
         * {@link DefaultEnvironment#propertySources} held.
         *
         * @param name       The name
         * @param registered The property source registered under the name
         * @return Whether the refresh replaces it
         */
        private boolean replaces(String name, PropertySource registered) {
            return reloads(name) || previouslyLoaded.get(name) == registered;
        }
    }
}
