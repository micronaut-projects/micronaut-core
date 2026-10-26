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
import io.micronaut.context.env.PropertySourcePropertyResolver.Build;
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
     * How long a start or a refresh waits for the read of the property sources in progress before
     * it asks it to read again instead.
     */
    private static final int READ_WAIT_SECONDS = 2;

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
     * The registered property sources. Its monitor also guards the generation, the read in progress
     * and the property sources the last read has read itself; only work in memory is done while it
     * is held.
     */
    private final Map<String, PropertySource> propertySources = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Collection<PropertySourcesLocator> propertySourcesLocators;

    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * The generation of the environment, which {@link #stop()} replaces. Guarded by the monitor of
     * {@link #propertySources}.
     */
    private Generation generation = new Generation();
    /**
     * The read of the property sources in progress, if any. Guarded by the monitor of
     * {@link #propertySources}.
     */
    private @Nullable Read read;
    /**
     * The property sources that the last read has loaded or located and registered itself, by name,
     * which the next read drops and reads again. A name of one that was added or removed while the
     * last read ran maps to {@code null}: the next read takes it back. Guarded by the monitor of
     * {@link #propertySources}.
     */
    private Map<String, @Nullable PropertySource> ownSources = Map.of();
    /**
     * The property source being added by each name, the latest call: an add publishes only while
     * it is still the latest change of its name. Guarded by the monitor of {@link #propertySources}.
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
     * Adds a property source. On a running environment it is processed into a change on top of the
     * published properties, on this thread and with no lock held, and then registered and published
     * in one step, unless a later add or a remove of its name has come meanwhile. A property source
     * added while a read of the property sources is in progress is only registered if the read has
     * not taken the registered property sources yet, so that it processes it in its order. Added
     * after that, it is handed over to the read as well, which publishes it in its order. If it fails
     * to be processed, nothing of it is published and it stays registered.
     *
     * <p>The property source registered by its name is processed again on top, so that adding the
     * registered property sources again in their order keeps their precedence, as a configuration
     * watch does. It is not while the same property source is being added. One that the read in
     * progress reads again itself is not handed over to it.</p>
     *
     * @param propertySource The property source
     * @return This environment
     */
    @Override
    public DefaultEnvironment addPropertySource(PropertySource propertySource) {
        String name = propertySource.getName();
        Read inRead;
        synchronized (propertySources) {
            if (addsInFlight.get(name) == propertySource) {
                // being added: what that add publishes holds it
                return this;
            }
            if (!isRunning() || (read != null && !read.cut)) {
                // the next start, or the read in progress, processes it in its order
                addsInFlight.remove(name);
                register(name, propertySource);
                return this;
            }
            addsInFlight.put(name, propertySource);
            inRead = read;
        }
        // what the expressions of the properties resolve to is kept while a read is in progress, as the read keeps it
        Build change = propertyPlaceholderResolver.startChange(inRead != null);
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
            if (inRead == null) {
                throw e;
            }
            // as when it was only registered while the property sources were read
            logFailure("Failed to process property source {} added while the property sources are read", name, e);
            return this;
        } finally {
            propertyPlaceholderResolver.endBuild(change);
        }
        synchronized (propertySources) {
            if (addsInFlight.get(name) != propertySource) {
                // a later add or a remove of the name has come meanwhile
                return this;
            }
            addsInFlight.remove(name);
            register(name, propertySource);
            if (isRunning()) {
                propertyPlaceholderResolver.publishChange(change);
                if (read != null && read.cut && !read.reads(name, propertySource)) {
                    // the latest by its name, which the read publishes in its order
                    read.handedOver.remove(name);
                    read.handedOver.put(name, change);
                }
            }
        }
        return this;
    }

    /**
     * Removes the property source registered by the name of the given one. Its properties stay
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
            if (read != null) {
                read.changedNames.add(name);
                read.handedOver.remove(name);
            }
            reset = isRunning() && read == null;
        }
        if (reset) {
            propertyPlaceholderResolver.resetCachesAndRememberedValues();
        }
        return this;
    }

    /**
     * Registers a property source that is added. Called with the monitor of
     * {@link #propertySources} held.
     *
     * @param name           The name of the property source
     * @param propertySource The property source
     */
    private void register(String name, PropertySource propertySource) {
        if (propertySources.get(name) != propertySource) {
            propertySources.put(name, propertySource);
            if (read != null) {
                read.changedNames.add(name);
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
     * The registered property sources. On the thread that reads the property sources, they are as
     * the read sees them, see {@link #view(Read)}.
     *
     * @return A copy of the property sources
     */
    @Override
    public Collection<PropertySource> getPropertySources() {
        // A copy taken under the monitor of the map: property sources are added and removed while
        // the environment runs, and iterating a view of the map would then fail
        synchronized (propertySources) {
            Read current = read;
            if (current != null && current.thread == Thread.currentThread()) {
                return List.copyOf(view(current).values());
            }
            return List.copyOf(propertySources.values());
        }
    }

    @Override
    public Environment start() {
        if (running.compareAndSet(false, true)) {
            LOG.debug("Starting environment {} for active names {}", this, getActiveNames());
            read(true);
        }
        return this;
    }

    @Override
    public Environment refresh() {
        read(false);
        return this;
    }

    /**
     * Reads the property sources, one read at a time per generation. A caller that the read in
     * progress keeps waiting longer than {@link #READ_WAIT_SECONDS} asks it to read again, and
     * returns: the read in progress reads once more before it ends, and publishes what the caller
     * asked for then. A refresh called from code that runs in the read, on its thread, asks that too,
     * once per read at most, so that code that refreshes whenever it runs cannot keep the read going.
     *
     * @param starting Whether the environment starts
     */
    private void read(boolean starting) {
        Generation readGeneration;
        synchronized (propertySources) {
            readGeneration = generation;
        }
        if (readGeneration.readLock.isHeldByCurrentThread()) {
            if (!readGeneration.askedItself) {
                readGeneration.askedItself = true;
                synchronized (propertySources) {
                    readGeneration.readAgain = true;
                }
            }
            return;
        }
        if (!lockToRead(readGeneration)) {
            return;
        }
        readGeneration.askedItself = false;
        try {
            readOnce(readGeneration, starting);
        } finally {
            readAgainAndUnlock(readGeneration);
        }
    }

    /**
     * Takes the read lock of a generation, waiting for the read in progress at most
     * {@link #READ_WAIT_SECONDS}. If it does not end in time, it is asked to read again.
     *
     * @param readGeneration The generation
     * @return Whether the lock was taken; if not, the read in progress reads again
     */
    private boolean lockToRead(Generation readGeneration) {
        ReentrantLock readLock = readGeneration.readLock;
        try {
            if (readLock.tryLock(READ_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        while (true) {
            synchronized (propertySources) {
                // checked under the monitor that the read in progress unlocks under, see readAgainAndUnlock
                if (readLock.isLocked()) {
                    readGeneration.readAgain = true;
                    return false;
                }
            }
            // the read in progress has just ended
            if (readLock.tryLock()) {
                return true;
            }
        }
    }

    /**
     * Reads the property sources again for as long as another caller has asked for it, then lets
     * the next read take the lock. A failure of a read again is logged, as its callers have
     * returned.
     *
     * @param readGeneration The generation, whose read lock the current thread holds
     */
    private void readAgainAndUnlock(Generation readGeneration) {
        while (true) {
            synchronized (propertySources) {
                if (!readGeneration.readAgain || readGeneration != generation) {
                    readGeneration.readAgain = false;
                    readGeneration.readLock.unlock();
                    return;
                }
                readGeneration.readAgain = false;
            }
            try {
                readOnce(readGeneration, false);
            } catch (Throwable e) {
                // Throwable, as a property source written in Groovy or Kotlin can throw a checked exception
                logFailure("Failed to read the property sources of {} again", this, e);
            }
        }
    }

    /**
     * Reads the property sources once, into new catalogs, and publishes them in one step: a
     * concurrent lookup sees the properties as they were before the read or as they are after it.
     * A start publishes after each property source as well. A read that a stop has overtaken
     * publishes and registers nothing, and a read that fails publishes and registers nothing more.
     *
     * @param readGeneration The generation of the read
     * @param starting       Whether the environment starts
     */
    private void readOnce(Generation readGeneration, boolean starting) {
        Build build = propertyPlaceholderResolver.startRead();
        try {
            Read current;
            synchronized (propertySources) {
                if (readGeneration != generation) {
                    return;
                }
                current = new Read(readGeneration, build, ownSources);
                read = current;
            }
            try {
                loadAndProcess(current, starting);
            } finally {
                synchronized (propertySources) {
                    if (read == current) {
                        read = null;
                    }
                }
            }
        } finally {
            propertyPlaceholderResolver.endBuild(build);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Stops the environment at once: its properties are empty and the property sources that the
     * last read has read itself are dropped when it returns. A read in progress does not publish
     * or register what it reads.
     *
     * @return This environment
     */
    @Override
    public Environment stop() {
        synchronized (propertySources) {
            running.set(false);
            generation = new Generation();
            read = null;
            ownSources.forEach(this::unregister);
            ownSources = Map.of();
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
     * Loads and locates the property sources, and processes them, with the registered ones, into
     * the read.
     *
     * @param read     The read
     * @param starting Whether the environment starts, which publishes after each property source
     */
    private void loadAndProcess(Read read, boolean starting) {
        // the configuration files and the default property sources
        List<PropertySource> loaded = new ArrayList<>(configLocations.size());
        if (configuration.isEnableDefaultPropertySources()) {
            loaded.addAll(readPropertySourceList(applicationName));
            addDefaultPropertySources(read, loaded);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                loaded.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
            }
            String propertySourcesEnv = CachedEnvironment.getenv(ENV_PROPERTY_SOURCES_KEY);
            if (propertySourcesEnv != null) {
                loaded.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
            }
        }
        read.addOwn(loaded, propertySources);
        // what the locators return; those of a bootstrap locator are registered as the files are
        List<PropertySource> located = new ArrayList<>();
        for (PropertySourcesLocator propertySourcesLocator : propertySourcesLocators) {
            Collection<PropertySource> sources = propertySourcesLocator.load(this);
            located.addAll(sources);
            if (propertySourcesLocator instanceof BootstrapLocatorMarker) {
                read.addOwn(sources, propertySources);
            }
        }

        List<PropertySource> sources = new ArrayList<>(loaded);
        for (PropertySource registered : cut(read)) {
            if (!sources.contains(registered)) {
                sources.add(registered);
            }
        }
        sources.addAll(located);
        OrderUtil.sortOrdered(sources);
        for (PropertySource propertySource : sources) {
            internalProcessPropertySource(propertySource);
            if (starting) {
                synchronized (propertySources) {
                    if (read.generation != generation) {
                        // a stop has overtaken the start
                        return;
                    }
                    publish(read);
                }
            }
        }

        synchronized (propertySources) {
            if (read.generation != generation) {
                return;
            }
            // an add or a remove of a name during the read wins over what the reads have loaded
            read.previousOwn.forEach((name, source) -> {
                if (!read.changedNames.contains(name)) {
                    unregister(name, source);
                }
            });
            Map<String, PropertySource> own = new LinkedHashMap<>();
            read.own.forEach((name, source) -> {
                boolean ownName = !PropertySource.CONTEXT.equals(name);
                if (read.changedNames.contains(name)) {
                    // What was added or removed by the name during the read stays until the next
                    // read, which takes the name back, as 5.2.x dropped it by name when it read again.
                    if (ownName) {
                        own.put(name, null);
                    }
                } else if (!propertySources.containsKey(name) || read.takesBack(name)) {
                    // A property source registered by a name the last read did not read itself stays,
                    // as 5.2.x kept a name registered before a read.
                    propertySources.put(name, source);
                    if (ownName) {
                        own.put(name, source);
                    }
                }
            });
            ownSources = own;
            publish(read);
        }
    }

    /**
     * The registered property sources as a read sees them: without those the last read has read
     * itself, and with those this read has read itself so far, as it registers them when it ends.
     * Called with the monitor of {@link #propertySources} held.
     *
     * @param read The read
     * @return The property sources, by name
     */
    private Map<String, PropertySource> view(Read read) {
        Map<String, PropertySource> view = new LinkedHashMap<>(propertySources);
        read.previousOwn.forEach((name, source) -> {
            if (view.get(name) == source) {
                view.remove(name);
            }
        });
        read.own.forEach((name, source) -> {
            if (read.takesBack(name)) {
                view.put(name, source);
            } else {
                view.putIfAbsent(name, source);
            }
        });
        return view;
    }

    /**
     * Takes the registered property sources that a read processes, the input cut: those of its view
     * that it has not read itself, see {@link #view(Read)}. A property source added from then on is
     * handed over to the read, see {@link #addPropertySource(PropertySource)}.
     *
     * @param read The read
     * @return The registered property sources
     */
    private List<PropertySource> cut(Read read) {
        synchronized (propertySources) {
            read.cut = true;
            List<PropertySource> registered = new ArrayList<>(propertySources.size());
            propertySources.forEach((name, source) -> {
                if (read.previousOwn.get(name) != source && !read.takesBack(name)) {
                    registered.add(source);
                    read.input.add(source);
                }
            });
            return registered;
        }
    }

    /**
     * Publishes what a read has built so far, with the property sources handed over to it on top.
     * Called with the monitor of {@link #propertySources} held.
     *
     * @param read The read
     */
    private void publish(Read read) {
        propertyPlaceholderResolver.publishRead(read.build, read.handedOver.values());
    }

    /**
     * Unregisters a property source, if it is the one registered by its name. Called with the
     * monitor of {@link #propertySources} held.
     *
     * @param name   The name
     * @param source The property source, {@code null} for none
     */
    private void unregister(String name, @Nullable PropertySource source) {
        if (source != null && propertySources.get(name) == source) {
            propertySources.remove(name);
        }
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
     * Adds the default property sources that are not registered, as the read sees them.
     *
     * @param read            The read
     * @param propertySources The list of property sources
     */
    private void addDefaultPropertySources(Read read, List<PropertySource> propertySources) {
        Map<String, PropertySource> registered;
        synchronized (this.propertySources) {
            registered = view(read);
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
     * A generation of the environment, from a stop to the next.
     */
    private static final class Generation {
        /**
         * Held by the read of the generation in progress, so that one runs at a time.
         */
        private final ReentrantLock readLock = new ReentrantLock();
        /**
         * Whether the read in progress is asked to read again before it ends. Guarded by the
         * monitor of {@link DefaultEnvironment#propertySources}.
         */
        private boolean readAgain;
        /**
         * Whether the thread that holds the read lock has asked itself to read again since it took
         * the lock. Only that thread uses it.
         */
        private boolean askedItself;
    }

    /**
     * A read of the property sources in progress. Its fields that are not final are guarded by the
     * monitor of {@link DefaultEnvironment#propertySources}.
     */
    private static final class Read {
        private final Generation generation;
        private final Build build;
        /**
         * The thread that reads, which sees the registered property sources through the
         * {@link DefaultEnvironment#view(Read) view} of the read.
         */
        private final Thread thread = Thread.currentThread();
        /**
         * The property sources that the last read has read itself, by name. This read reads them
         * again, so they are left out of its view and of its input, by identity.
         */
        private final Map<String, @Nullable PropertySource> previousOwn;
        /**
         * The property sources that this read has loaded or located itself so far, by name, which
         * it registers when it ends.
         */
        private final Map<String, PropertySource> own = new LinkedHashMap<>();
        /**
         * Whether the read has taken the registered property sources it processes, see
         * {@link DefaultEnvironment#cut(Read)}. A property source added before is only registered,
         * for the read to process in its order.
         */
        private boolean cut;
        /**
         * The registered property sources that the read has taken at its cut, by identity.
         */
        private final Set<PropertySource> input = Collections.newSetFromMap(new IdentityHashMap<>());
        /**
         * The property sources added since the cut, the latest one by each name, which the read
         * keeps on top of what it publishes.
         */
        private final Map<String, Build> handedOver = new LinkedHashMap<>();
        /**
         * The names of the property sources that were added or removed during the read: the read
         * does not register what it has read itself by such a name.
         */
        private final Set<String> changedNames = new HashSet<>();

        private Read(Generation generation, Build build, Map<String, @Nullable PropertySource> previousOwn) {
            this.generation = generation;
            this.build = build;
            this.previousOwn = previousOwn;
        }

        /**
         * Adds property sources that the read has loaded or located itself. Called by the thread
         * that reads, which asks for their names with no lock held, as a name is code of the
         * property source.
         *
         * @param sources The property sources
         * @param monitor The monitor that guards the read
         */
        private void addOwn(Collection<PropertySource> sources, Object monitor) {
            Map<String, PropertySource> named = new LinkedHashMap<>();
            for (PropertySource source : sources) {
                named.put(source.getName(), source);
            }
            synchronized (monitor) {
                own.putAll(named);
            }
        }

        /**
         * Whether the read processes the given property source itself: one it has taken at its
         * cut, read itself, or reads again as one the last read has read. Called with the monitor
         * of {@link DefaultEnvironment#propertySources} held.
         *
         * @param name   The name of the property source
         * @param source The property source
         * @return Whether the read processes it
         */
        private boolean reads(String name, PropertySource source) {
            return input.contains(source) || own.get(name) == source || previousOwn.get(name) == source;
        }

        /**
         * Whether the read registers what it has read itself by a name the last read owned, in
         * place of whatever is registered by it, as 5.2.x dropped such a name when it read again.
         * Called with the monitor of {@link DefaultEnvironment#propertySources} held.
         *
         * @param name The name
         * @return Whether the read takes the name back
         */
        private boolean takesBack(String name) {
            return own.containsKey(name) && previousOwn.containsKey(name) && !changedNames.contains(name);
        }
    }
}
