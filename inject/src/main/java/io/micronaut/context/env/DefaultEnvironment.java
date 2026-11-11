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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.BeanIntrospectionScanner;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.inject.BeanConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * <p>The default implementation of the {@link Environment} interface. Configures a named environment.</p>
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
public class DefaultEnvironment extends PropertySourcePropertyResolver implements Environment, AtomicRefresh<Environment> {

    private static final List<PropertySource> CONSTANT_PROPERTY_SOURCES = StaticOptimizations.get(ConstantPropertySources.class)
            .map(ConstantPropertySources::getSources)
            .orElse(Collections.emptyList());

    private static final String EC2_LINUX_HYPERVISOR_FILE = "/sys/hypervisor/uuid";
    private static final String EC2_LINUX_BIOS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/bios_vendor";
    private static final String EC2_WINDOWS_HYPERVISOR_CMD = "wmic path win32_computersystemproduct get uuid";
    private static final String FILE_SEPARATOR = ",";
    private static final String AWS_LAMBDA_FUNCTION_NAME_ENV = "AWS_LAMBDA_FUNCTION_NAME";
    private static final String K8S_ENV = "KUBERNETES_SERVICE_HOST";
    private static final String PCF_ENV = "VCAP_SERVICES";
    private static final String HEROKU_DYNO = "DYNO";
    private static final String GOOGLE_APPENGINE_ENVIRONMENT = "GAE_ENV";
    private static final int DEFAULT_READ_TIMEOUT = 500;
    private static final int DEFAULT_CONNECT_TIMEOUT = 500;
    // CHECKSTYLE:OFF
    private static final String GOOGLE_COMPUTE_METADATA = "metadata.google.internal";
    // CHECKSTYLE:ON
    private static final String ORACLE_CLOUD_ASSET_TAG_FILE = "/sys/devices/virtual/dmi/id/chassis_asset_tag";
    private static final String ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD = "wmic systemenclosure get smbiosassettag";
    private static final String DO_SYS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/sys_vendor";
    private static final Boolean DEDUCE_ENVIRONMENT_DEFAULT = true;
    private static final List<String> DEFAULT_CONFIG_LOCATIONS = Arrays.asList("classpath:/", "file:config/");
    /**
     * How long a refresh waits for the active refresh of another thread to end, in nanoseconds: two
     * seconds. After that, it asks the active refresh to read once more, and returns.
     */
    private static final long ACTIVE_REFRESH_WAIT_NANOS = 2_000_000_000L;
    protected final ClassPathResourceLoader resourceLoader;
    protected final List<PropertySource> refreshablePropertySources = new ArrayList<>(10);
    protected final MutableConversionService mutableConversionService;
    private EnvironmentsAndPackage environmentsAndPackage;
    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final BeanIntrospectionScanner annotationScanner;
    private final Collection<String> configurationIncludes = new HashSet<>(3);
    private final Collection<String> configurationExcludes = new HashSet<>(3);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean reading = new AtomicBoolean(false);
    /**
     * Held while a property source is registered by an add and the count of snapshots read, and
     * while a start or a refresh counts and takes its snapshot, so that one comes before the other.
     * It also guards {@link #activeRefresh} and the requests made to it. Nothing else runs with it
     * held.
     */
    private final Object registrationLock = new Object();
    /**
     * The thread whose refresh is active, or {@code null}. Guarded by the registration lock.
     *
     * <p>Why a refresh is made active: it holds the catalog lock from the moment it stops this
     * environment until it has published what it read, which can take long: remote configuration,
     * and the code of every property source. A thread that waited for the catalog lock meanwhile
     * would wait for all of that, and would deadlock if the refresh needed a lock that the thread
     * holds. So a refresh first becomes the active refresh, and only then takes the catalog lock.
     * Other threads look at this field rather than wait for that lock:</p>
     * <ul>
     * <li>stop(), start() and addPropertySource queue the call in {@link #queuedCalls} and return at
     * once. addPropertySource first registers the property source, as it did before it waited.</li>
     * <li>refresh() waits up to two seconds for the active refresh to end. If it does not end, it
     * queues the call too, and returns.</li>
     * <li>A thread that has a call queued has any further call that changes this environment queued
     * whole: addPropertySource (registering included), removePropertySource, addPackage,
     * addConfigurationIncludes, addConfigurationExcludes, addConverter and close. Had it waited, it
     * would make that call only once its queued call was carried out. Its lookups, and what it does
     * through objects it got from this environment, such as the conversion service, take effect at
     * once, before its queued calls. EnvironmentMethodsSpec checks that every public method is one of
     * these, a lookup, or a start, stop or refresh.</li>
     * </ul>
     * <p>The active refresh carries out the queued calls once it has read and published, in the
     * order they were made, each as the thread that made it would have once it got the catalog lock
     * after the refresh: stop() and start() as they are, an add by processing the property source
     * if the environment runs, and a refresh by reading once more, once at most. The one exception:
     * if the last stop(), start() or refresh() queued when the read ends is a stop(), the read is
     * not published, as that stop() is carried out next. The refresh reads a property source added
     * before it takes the registered ones in their order, as before.</p>
     * <p>A refresh stays active for as long as it runs code of property sources or subclasses: while
     * it reads, and while it carries out the queued calls. That code could need a lock that another
     * thread holds, and that thread must then not be waiting for the catalog lock. The refresh stops
     * being active in the same step as it finds no call left, and runs no such code after that. A
     * thread that already holds the catalog lock, such as code that the refresh runs, skips all of
     * this and goes on as before: a stop() made by such code stops the environment at once.</p>
     * <p>Locks: the catalog lock is always taken before the registration lock, and nothing waits on
     * the registration lock while it holds the catalog lock. The code of property sources and
     * subclasses runs with the same locks held as it did before the active refresh was added.</p>
     */
    private Thread activeRefresh;
    /**
     * How many threads found no active refresh and are taking the catalog lock. A refresh that
     * becomes active waits until this is zero before it takes the catalog lock itself: otherwise it
     * could take the lock just before such a thread, and make it wait for the whole refresh.
     * Guarded by the registration lock.
     */
    private int threadsTakingLock;
    /**
     * The stop(), start(), addPropertySource and refresh() calls that other threads made while a
     * refresh was active, in the order they were made, which the active refresh carries out before
     * it ends, see {@link #activeRefresh}. Guarded by the registration lock.
     */
    private ArrayDeque<QueuedCall> queuedCalls;
    /**
     * The threads that have queued a call for the active refresh, see {@link #hasQueuedCall()}.
     * Guarded by the registration lock.
     */
    private Set<Thread> threadsWithQueuedCalls;
    /**
     * Used only by the active refresh: whether the last stop(), start() or refresh() queued when the
     * read ended is a stop(), so that the read is not published. Guarded by the catalog lock.
     */
    private boolean discardRead;
    /**
     * Where the refresh that a {@link #refreshAndDiff()} makes records the difference.
     */
    private final ThreadLocal<Difference> differences = new ThreadLocal<>();
    /**
     * How many snapshots of the registered property sources starts and refreshes have taken, see
     * {@link #alreadyProcessed(PropertySource)}. Guarded by the registration lock.
     */
    private long registrySnapshots;
    /**
     * The snapshot that the start or the refresh in progress took. Guarded by the catalog lock: the
     * monitor of {@link #catalog}, which {@link #start()}, {@link #stop()}, a refresh and the
     * processing of a property source all hold.
     */
    private RegistrySnapshot pendingSnapshot;
    /**
     * The snapshot that the last start or refresh whose properties were published took. Guarded by
     * the catalog lock, the monitor of {@link #catalog}.
     */
    private RegistrySnapshot publishedSnapshot;
    /**
     * For the property source the current thread adds: how many snapshots had been taken when it
     * was registered.
     */
    private final ThreadLocal<Long> addedAfterSnapshot = new ThreadLocal<>();
    private final Boolean deduceEnvironments;
    private final ApplicationContextConfiguration configuration;
    private final Collection<String> configLocations;

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     */
    public DefaultEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        this(configuration, true);
    }

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     * @param logEnabled flag to enable or disable logger
     */
    public DefaultEnvironment(@NonNull ApplicationContextConfiguration configuration, boolean logEnabled) {
        super(configuration.getConversionService().orElseGet(MutableConversionService::create), logEnabled);
        this.mutableConversionService = (MutableConversionService) conversionService;
        this.configuration = configuration;
        this.resourceLoader = configuration.getResourceLoader();

        List<String> specifiedNames = new ArrayList<>(configuration.getEnvironments());

        specifiedNames.addAll(0, Stream.of(CachedEnvironment.getProperty(ENVIRONMENTS_PROPERTY),
                CachedEnvironment.getenv(ENVIRONMENTS_ENV))
                .filter(StringUtils::isNotEmpty)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .toList());

        this.deduceEnvironments = configuration.getDeduceEnvironments().orElse(null);
        EnvironmentsAndPackage environmentsAndPackage = getEnvironmentsAndPackage(specifiedNames);
        if (environmentsAndPackage.enviroments.isEmpty() && specifiedNames.isEmpty()) {
            specifiedNames = configuration.getDefaultEnvironments();
        }
        Set<String> environments = new LinkedHashSet<>(environmentsAndPackage.enviroments);
        String aPackage = environmentsAndPackage.aPackage;
        if (aPackage != null) {
            packages.add(aPackage);
        }

        specifiedNames.forEach(environments::remove);
        environments.addAll(specifiedNames);
        this.classLoader = configuration.getClassLoader();
        this.annotationScanner = createAnnotationScanner(classLoader);
        this.names = environments;
        if (!environments.isEmpty()) {
            log.info("Established active environments: {}", environments);
        }
        List<String> configLocations = configuration.getOverrideConfigLocations() == null
            ? new ArrayList<>(DEFAULT_CONFIG_LOCATIONS)
            : new ArrayList<>(configuration.getOverrideConfigLocations());
        // Search config locations in reverse order
        Collections.reverse(configLocations);
        this.configLocations = configLocations;
    }

    @Override
    public boolean isPresent(String className) {
        return presenceCache.computeIfAbsent(className, s -> ClassUtils.isPresent(className, getClassLoader()));
    }

    @Override
    public PropertyPlaceholderResolver getPlaceholderResolver() {
        return this.propertyPlaceholderResolver;
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
        long snapshotsWhenRegistered;
        synchronized (registrationLock) {
            if (hasQueuedCall()) {
                // This thread has a call queued for the refresh of another thread. Had it waited for
                // that call, it would make this add only once that call was carried out: queue the
                // add whole, so that not even the registration comes before it.
                queue(new QueuedChange(() -> addNow(propertySource, name)));
                return this;
            }
            snapshotsWhenRegistered = register(propertySource, name);
        }
        processIfRunning(propertySource, snapshotsWhenRegistered);
        return this;
    }

    /**
     * Adds a property source as {@link #addPropertySource(PropertySource)} does once it takes effect:
     * registers it, and processes it if the environment runs.
     *
     * @param propertySource The property source
     * @param name           Its name, asked for with no lock held
     */
    private void addNow(PropertySource propertySource, String name) {
        long snapshotsWhenRegistered;
        synchronized (registrationLock) {
            snapshotsWhenRegistered = register(propertySource, name);
        }
        processIfRunning(propertySource, snapshotsWhenRegistered);
    }

    /**
     * Registers a property source by its name, the first thing an add does. Called with the
     * registration lock held.
     *
     * @param propertySource The property source
     * @param name           Its name, asked for with no lock held
     * @return How many snapshots of the registered property sources had been taken when it was
     * registered
     */
    private long register(PropertySource propertySource, String name) {
        propertySources.put(name, propertySource);
        return registrySnapshots;
    }

    /**
     * Processes a property source that an add has just registered, if the environment runs and is
     * not reading its property sources: through
     * {@link #processPropertySource(PropertySource, PropertySource.PropertyConvention)}, so that an
     * override of it decides, as for any add.
     *
     * @param propertySource          The property source
     * @param snapshotsWhenRegistered How many snapshots had been taken when it was registered
     */
    private void processIfRunning(PropertySource propertySource, long snapshotsWhenRegistered) {
        if (isRunning() && !reading.get()) {
            Long outer = addedAfterSnapshot.get();
            addedAfterSnapshot.set(snapshotsWhenRegistered);
            try {
                resetCaches();
                processPropertySource(propertySource, PropertySource.PropertyConvention.JAVA_PROPERTIES);
            } finally {
                if (outer == null) {
                    addedAfterSnapshot.remove();
                } else {
                    addedAfterSnapshot.set(outer);
                }
            }
        }
    }

    /**
     * Whether a start or a refresh has processed the given property source, which the current
     * thread adds, since it was registered: one that took its snapshot of the registered property
     * sources after that, and whose properties are published. The add has then waited for it, or
     * left the property source to it, and it is not processed a second time, on top of the others.
     * Called with the catalog lock held.
     *
     * @param propertySource The property source
     * @return Whether it was processed already
     */
    @Override
    boolean alreadyProcessed(PropertySource propertySource) {
        Long snapshotsWhenRegistered = addedAfterSnapshot.get();
        RegistrySnapshot snapshot = publishedSnapshot;
        return snapshotsWhenRegistered != null && !reading.get() && snapshot != null
            && snapshot.number() > snapshotsWhenRegistered && snapshot.propertySources().contains(propertySource);
    }

    @Override
    void propertiesPublished() {
        publishedSnapshot = pendingSnapshot;
    }

    @Override
    protected void processPropertySource(PropertySource properties, PropertySource.PropertyConvention convention) {
        Long snapshotsWhenRegistered = addedAfterSnapshot.get();
        if (snapshotsWhenRegistered == null || Thread.holdsLock(catalog)) {
            // Not called for addPropertySource, or called by a thread that already holds the catalog
            // lock, such as a refresh that reads the property sources: process it as before.
            super.processPropertySource(properties, convention);
            return;
        }
        // Register the property source by its name first, with no lock held, as before: getName() is
        // code of the property source, and could wait for a thread that waits for the catalog lock.
        String name = properties.getName();
        propertySources.put(name, properties);
        synchronized (registrationLock) {
            if (activeRefresh != null) {
                // Another thread is refreshing: do not wait for it. Queue the add; the refresh reads
                // the property source, or processes it on top once it has published.
                queue(new QueuedAdd(properties, name, convention, snapshotsWhenRegistered));
                return;
            }
            // No refresh is active: process it as before. Counted until this thread holds the
            // catalog lock, so that a refresh becoming active now waits for it, see threadsTakingLock.
            threadsTakingLock++;
        }
        synchronized (catalog) {
            // Holding the lock now: stop counting. A refresh waiting for the count then waits for the
            // catalog lock instead, as it did before.
            countOutLockTaker();
            processRegistered(properties, convention);
        }
    }

    /**
     * Processes a property source that another thread added while this refresh was active, on top
     * of what is published, as the add would have done itself once it got the catalog lock: the add
     * registered the property source and found the environment running before it would have waited.
     * Skipped is only one that the refresh has read already, see
     * {@link #alreadyProcessed(PropertySource)}. The name is the one the add registered it by, so
     * that getName() is not called again with the lock held. A failure is only logged: the thread
     * that added the property source has returned already. Called with the catalog lock held, while
     * the refresh is still active.
     *
     * @param add The queued add
     */
    private void processQueuedAdd(QueuedAdd add) {
        PropertySource propertySource = add.propertySource();
        Long outer = addedAfterSnapshot.get();
        addedAfterSnapshot.set(add.snapshotsWhenRegistered());
        try {
            processRegistered(propertySource, add.convention());
        } catch (RuntimeException e) {
            log.error("Error processing property source [{}], added while the environment refreshed", add.name(), e);
        } finally {
            if (outer == null) {
                addedAfterSnapshot.remove();
            } else {
                addedAfterSnapshot.set(outer);
            }
        }
    }

    /**
     * Queues a change of this environment whole, if the current thread has a call queued for the
     * refresh of another thread, see {@link #hasQueuedCall()}: the refresh then makes the change in
     * its turn.
     *
     * @param change The change
     * @return Whether it was queued; if not, the caller makes the change now
     */
    private boolean queueIfCallQueued(Runnable change) {
        synchronized (registrationLock) {
            if (hasQueuedCall()) {
                queue(new QueuedChange(change));
                return true;
            }
            return false;
        }
    }

    /**
     * Queues a call of the current thread for the active refresh to carry out, see
     * {@link #queuedCalls}. Called with the registration lock held.
     *
     * @param call The call
     */
    private void queue(QueuedCall call) {
        if (queuedCalls == null) {
            queuedCalls = new ArrayDeque<>();
            threadsWithQueuedCalls = new HashSet<>();
        }
        queuedCalls.add(call);
        threadsWithQueuedCalls.add(Thread.currentThread());
    }

    /**
     * Whether the current thread has a call queued for the active refresh. Had it waited for that
     * call, it would make any further call only once that call was carried out; so the further
     * calls are queued whole, and carried out in their turn. Called with the registration lock held.
     *
     * @return Whether the current thread has a call queued
     */
    private boolean hasQueuedCall() {
        return threadsWithQueuedCalls != null && threadsWithQueuedCalls.contains(Thread.currentThread());
    }

    /**
     * The last stop(), start() or refresh() queued for the active refresh and not carried out yet,
     * or {@code null}. Called with the registration lock held.
     *
     * @return The call, or {@code null}
     */
    @Nullable
    private QueuedLifecycleCall lastQueuedLifecycleCall() {
        if (queuedCalls != null) {
            Iterator<QueuedCall> calls = queuedCalls.descendingIterator();
            while (calls.hasNext()) {
                if (calls.next() instanceof QueuedLifecycleCall call) {
                    return call;
                }
            }
        }
        return null;
    }

    @Override
    boolean processingAdd() {
        return addedAfterSnapshot.get() != null;
    }

    @Override
    public Environment removePropertySource(PropertySource propertySource) {
        String name = propertySource.getName();
        // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
        if (!queueIfCallQueued(() -> removeNow(name))) {
            removeNow(name);
        }
        return this;
    }

    /**
     * Removes a property source as {@link #removePropertySource(PropertySource)} does once it takes
     * effect.
     *
     * @param name Its name, asked for with no lock held
     */
    private void removeNow(String name) {
        propertySources.remove(name);
        if (isRunning() && !reading.get()) {
            resetCaches();
        }
    }

    @Override
    public DefaultEnvironment addPropertySource(String name, Map<String, ? super Object> values) {
        return (DefaultEnvironment) super.addPropertySource(name, values);
    }

    @Override
    public Environment addPackage(String pkg) {
        // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
        if (!queueIfCallQueued(() -> addPackageNow(pkg))) {
            addPackageNow(pkg);
        }
        return this;
    }

    private void addPackageNow(String pkg) {
        if (!this.packages.contains(pkg)) {
            this.packages.add(pkg);
        }
    }

    @Override
    public Environment addConfigurationExcludes(@Nullable String... names) {
        if (names != null) {
            List<String> excludes = Arrays.asList(names.clone());
            // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
            if (!queueIfCallQueued(() -> configurationExcludes.addAll(excludes))) {
                configurationExcludes.addAll(excludes);
            }
        }
        return this;
    }

    @Override
    public Environment addConfigurationIncludes(String... names) {
        if (names != null) {
            List<String> includes = Arrays.asList(names.clone());
            // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
            if (!queueIfCallQueued(() -> configurationIncludes.addAll(includes))) {
                configurationIncludes.addAll(includes);
            }
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
        return Collections.unmodifiableCollection(this.propertySources.values());
    }

    @Override
    public Environment start() {
        // If another thread is refreshing, return at once: that refresh starts this environment
        // before it ends, and taking the catalog lock would mean waiting for the whole refresh. See
        // beginStartOrStop.
        if (!beginStartOrStop(false)) {
            return this;
        }
        try {
            synchronized (catalog) {
                if (running.compareAndSet(false, true)) {
                    log.debug("Starting environment {} for active names {}", this, getActiveNames());
                    if (reading.compareAndSet(false, true)) {
                        try {
                            readIntoCopy(() -> readPropertySources(getPropertySourceRootName()));
                            if (!isRefreshing()) {
                                // what the start read is published, or is with the add or the start it runs in
                                propertiesPublished();
                            }
                        } finally {
                            // also when the read fails, or a property source added later is never processed
                            reading.set(false);
                        }
                    }
                }
            }
        } finally {
            endStartOrStop();
        }
        return this;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Environment stop() {
        // If another thread is refreshing, return at once: that refresh then stops this environment
        // before it ends, and publishes nothing if it has not published yet. See beginStartOrStop.
        if (!beginStartOrStop(true)) {
            return this;
        }
        try {
            synchronized (catalog) {
                running.set(false);
                reading.set(false);
                this.propertySources.values().removeAll(refreshablePropertySources);
                reset();
                if (!isRefreshing()) {
                    // what it processed is published no more; within a refresh it stays published
                    // until the refresh publishes, and if the refresh fails it stays so
                    publishedSnapshot = null;
                }
                resetCaches();
            }
        } finally {
            endStartOrStop();
        }
        return this;
    }

    /**
     * Decides whether a {@link #start()} or a {@link #stop()} takes the catalog lock, or is handed
     * to the refresh that another thread is running, see {@link #activeRefresh}. It is called before
     * that lock is taken, by start() and stop(), and by an override of them that takes the lock
     * itself.
     * <ul>
     * <li>{@code true}: go on and take the lock, and call {@link #endStartOrStop()} once it is let
     * go of.</li>
     * <li>{@code false}: another thread is refreshing, and this call has been handed to that
     * refresh. Return at once.</li>
     * </ul>
     *
     * @param stop Whether the caller stops the environment, rather than starting it
     * @return Whether the caller goes on and takes the lock
     * @since 4.10.36
     */
    @Internal
    protected final boolean beginStartOrStop(boolean stop) {
        if (Thread.holdsLock(catalog)) {
            // Already holding the catalog lock: this is the stop() or start() of a refresh, or a call
            // from code that runs under the lock. Go on as before.
            return true;
        }
        synchronized (registrationLock) {
            if (activeRefresh != null) {
                // Another thread is refreshing: do not wait for it. Queue the call: the refresh
                // carries it out before it ends, in the order the calls were made.
                queue(stop ? QueuedLifecycleCall.STOP : QueuedLifecycleCall.START);
                return false;
            }
            // No refresh is active: take the lock as before. Counted, so that a refresh becoming
            // active now waits for this thread, see threadsTakingLock.
            threadsTakingLock++;
            return true;
        }
    }

    /**
     * Called once a start or a stop for which {@link #beginStartOrStop(boolean)} returned
     * {@code true} has let go of the catalog lock: stops counting the thread in
     * {@link #threadsTakingLock}.
     *
     * @since 4.10.36
     */
    @Internal
    protected final void endStartOrStop() {
        if (!Thread.holdsLock(catalog)) {
            // beginStartOrStop counted this thread only if it did not hold the lock already; it does
            // not hold it now either, so it was counted
            countOutLockTaker();
        }
    }

    /**
     * Stops counting a thread in {@link #threadsTakingLock}, and wakes a refresh that waits for the
     * count to reach zero.
     */
    private void countOutLockTaker() {
        synchronized (registrationLock) {
            if (--threadsTakingLock == 0 && activeRefresh != null) {
                registrationLock.notifyAll();
            }
        }
    }

    /**
     * Refreshes the environment, see {@link AtomicRefresh#refresh()}: stops and starts it, which
     * reads the property sources into a new catalog that is then published in one step. How this
     * keeps other threads from waiting for it is described at {@link #activeRefresh}.
     */
    void refreshProperties() {
        // A refreshAndDiff() hands its refresh, through a ThreadLocal, the place to record the
        // difference. Take it off the thread while this refresh runs, so that a refresh started by
        // code that this one runs, such as a property source it reads or a queued add it processes,
        // does not record into it.
        Difference difference = differences.get();
        if (difference == null) {
            refreshProperties(null);
            return;
        }
        differences.remove();
        try {
            refreshProperties(difference);
        } finally {
            differences.set(difference);
        }
    }

    /**
     * Refreshes the environment, see {@link #refreshProperties()}.
     *
     * @param difference Where a refreshAndDiff() records the difference, or {@code null}
     */
    private void refreshProperties(@Nullable Difference difference) {
        if (Thread.holdsLock(catalog)) {
            // Already holding the catalog lock, for example a property source whose code refreshes:
            // refresh as before, inside what holds the lock.
            refreshComparing(this::restart, difference);
            return;
        }
        if (!becomeActiveRefresh()) {
            // The refresh of another thread stayed active for two seconds, and reads once more for
            // this one.
            return;
        }
        boolean refreshNotCarriedOut = false;
        try {
            synchronized (catalog) {
                try {
                    refreshComparing(this::restartAsActiveRefresh, difference);
                } finally {
                    // also when the read failed: the calls queued meanwhile are carried out anyway
                    refreshNotCarriedOut = finishActiveRefresh(difference);
                }
            }
        } finally {
            if (refreshNotCarriedOut) {
                // logged once the lock is let go, also when the read failed
                log.warn("A refresh called during another refresh gave up waiting for it, and was not carried out, as the refresh in progress had read once more already. The next refresh reads its changes.");
            }
        }
    }

    /**
     * Carries out the calls that other threads made while this refresh was active, one at a time, in
     * the order they were made, still holding the catalog lock and still active, and then ends the
     * active refresh. Each call is carried out as the thread that made it would have once it got the
     * catalog lock after the refresh:
     * <ul>
     * <li>stop() and start(): called as they are.</li>
     * <li>An add: processes the property source on top of what is published, if the environment
     * runs, see {@link #processQueuedAdd}.</li>
     * <li>A refresh that gave up waiting: reads once more, also when the first read failed. Only
     * once, so that refreshes which keep coming cannot keep it reading.</li>
     * </ul>
     * The refresh stays active until no call is left: carrying them out runs code of property sources
     * and subclasses, which could need a lock that another thread holds, and that thread must then
     * not be waiting for the catalog lock. It ends in the same step as it finds no call left, so that
     * none is left undone, and runs no such code after that.
     *
     * @param difference Where a refreshAndDiff() records the difference, or {@code null}
     * @return Whether a refresh that gave up waiting was not carried out because one more read had
     * been made already
     */
    private boolean finishActiveRefresh(@Nullable Difference difference) {
        // the read is published, or not, by now
        discardRead = false;
        boolean mayReadAgain = true;
        boolean refreshNotCarriedOut = false;
        try {
            while (true) {
                QueuedCall call;
                synchronized (registrationLock) {
                    call = queuedCalls == null ? null : queuedCalls.pollFirst();
                    if (call == null) {
                        // no call left: end, in the same step as finding that
                        endActiveRefresh();
                        break;
                    }
                }
                try {
                    if (call instanceof QueuedAdd add) {
                        processQueuedAdd(add);
                    } else if (call instanceof QueuedChange change) {
                        change.change().run();
                    } else if (call == QueuedLifecycleCall.REFRESH) {
                        if (mayReadAgain) {
                            mayReadAgain = false;
                            readOnceMore(difference);
                        } else {
                            refreshNotCarriedOut = true;
                        }
                    } else {
                        startOrStopAsAsked(call == QueuedLifecycleCall.STOP);
                    }
                } catch (RuntimeException e) {
                    // the thread that made the call has returned already: log, and go on with the next call
                    log.error("Error carrying out a call that another thread made while the environment refreshed", e);
                }
            }
        } finally {
            // Ended already, unless an Error was thrown: end it then too, so that it does not stay
            // active for good, and so that the next refresh publishes. The calls not carried out yet
            // are then dropped.
            discardRead = false;
            synchronized (registrationLock) {
                endActiveRefresh();
            }
        }
        return refreshNotCarriedOut;
    }

    /**
     * Stops or starts the environment as another thread asked while this refresh was active, still
     * holding the catalog lock: as that thread would have once it got the lock, so a start() of a
     * running environment does nothing. A failure is only logged: that thread has returned already.
     *
     * @param stop Whether to stop the environment, rather than start it
     */
    private void startOrStopAsAsked(boolean stop) {
        try {
            if (stop) {
                stop();
            } else {
                start();
            }
        } catch (RuntimeException e) {
            log.error("Error {} the environment, as another thread asked while it refreshed", stop ? "stopping" : "starting", e);
        }
    }

    /**
     * Reads the property sources once more, for a refresh of another thread that gave up waiting for
     * this one, see {@link #becomeActiveRefresh()}. If this read fails, the failure is only logged,
     * and what was published before stays: the refresh that asked for it has returned already.
     * Called with the catalog lock held, while the refresh is still active.
     *
     * @param difference Where a refreshAndDiff() records the difference, or {@code null}
     */
    private void readOnceMore(@Nullable Difference difference) {
        try {
            refreshComparing(this::restartAsActiveRefresh, difference);
        } catch (RuntimeException e) {
            log.error("Error reading the property sources once more, for a refresh that could not wait for this one", e);
        } finally {
            // the read is published, or not, by now
            discardRead = false;
        }
    }

    /**
     * Makes the refresh of the current thread the active refresh, see {@link #activeRefresh}.
     * <ul>
     * <li>If the refresh of another thread is active, waits at most two seconds for it to end. If it
     * has not ended by then, or this thread is interrupted, queues this refresh, which that refresh
     * carries out by reading once more, and returns {@code false}: this refresh then has nothing more
     * to do.</li>
     * <li>Once this refresh is active, waits until no thread is counted as taking the catalog lock,
     * see {@link #threadsTakingLock}, and returns {@code true}.</li>
     * </ul>
     *
     * @return Whether the refresh of the current thread is now the active refresh
     */
    private boolean becomeActiveRefresh() {
        boolean interrupted = false;
        try {
            synchronized (registrationLock) {
                long deadline = System.nanoTime() + ACTIVE_REFRESH_WAIT_NANOS;
                while (activeRefresh != null) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0 || interrupted) {
                        queue(QueuedLifecycleCall.REFRESH);
                        return false;
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(registrationLock, left);
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                // active now; threads already on their way to the catalog lock go first
                activeRefresh = Thread.currentThread();
                while (threadsTakingLock > 0) {
                    try {
                        registrationLock.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                return true;
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Ends the active refresh, if it is the refresh of the current thread, and wakes the threads that
     * wait for it to end. No call is left queued when the refresh ends normally; after an Error, the
     * calls left are dropped, and the property sources of adds among them stay registered, for the
     * next start or refresh to read. Called with the registration lock and the catalog lock held.
     */
    private void endActiveRefresh() {
        if (activeRefresh != Thread.currentThread()) {
            return;
        }
        activeRefresh = null;
        queuedCalls = null;
        threadsWithQueuedCalls = null;
        registrationLock.notifyAll();
    }

    /**
     * How a refresh made by code that holds the catalog lock rebuilds: stop and start, as a refresh
     * always did.
     */
    private void restart() {
        stop();
        start();
    }

    /**
     * How the active refresh rebuilds: stop and start, which reads the property sources. If the last
     * stop(), start() or refresh() that another thread queued by then, and that is not carried out
     * yet, is a stop(), the refresh then publishes nothing: {@link #finishActiveRefresh} carries that
     * stop() out next, and the stopped environment answers with what it had before the refresh.
     */
    private void restartAsActiveRefresh() {
        stop();
        start();
        synchronized (registrationLock) {
            discardRead = lastQueuedLifecycleCall() == QueuedLifecycleCall.STOP;
        }
    }

    /**
     * Runs one refresh: the rebuild, then publishing. For a {@link #refreshAndDiff()}, it also records
     * the difference: it copies the catalog before the first read, and compares right after
     * publishing, before queued adds or anything else change the catalog. Called with the catalog
     * lock held.
     *
     * @param rebuild    The rebuild
     * @param difference Where a refreshAndDiff() records the difference, or {@code null}
     */
    private void refreshComparing(Runnable rebuild, @Nullable Difference difference) {
        if (difference != null && difference.before == null) {
            difference.before = copyCatalog(publishedCatalog());
        }
        refresh(rebuild);
        if (difference != null && !discardRead) {
            difference.changes = diffCatalog(difference.before, publishedCatalog());
        }
    }

    @Override
    boolean publishesRefresh() {
        // asked before the read is published: nothing is published when a stop() is carried out next
        return !discardRead;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        // No lock needed: the Difference is used by this thread only, handed to its refresh through a
        // ThreadLocal as refresh() takes no argument. The refresh fills it in with the catalog lock
        // held, see refreshComparing.
        Difference outer = differences.get();
        Difference difference = new Difference();
        differences.set(difference);
        try {
            refresh();
        } finally {
            if (outer == null) {
                differences.remove();
            } else {
                differences.set(outer);
            }
        }
        if (difference.changes == null) {
            // It published nothing: stop() was called meanwhile, or the refresh gave its read to the
            // refresh of another thread, which reports the change itself.
            return new LinkedHashMap<>();
        }
        return difference.changes;
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        return mutableConversionService.convert(object, targetType, context);
    }

    @Override
    public <S, T> Optional<T> convert(S object, Class<? super S> sourceType, Class<T> targetType, ConversionContext context) {
        return mutableConversionService.convert(object, sourceType, targetType, context);
    }

    @Override
    public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
        return mutableConversionService.canConvert(sourceType, targetType);
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
        if (!queueIfCallQueued(() -> mutableConversionService.addConverter(sourceType, targetType, typeConverter))) {
            mutableConversionService.addConverter(sourceType, targetType, typeConverter);
        }
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter) {
        // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
        if (!queueIfCallQueued(() -> mutableConversionService.addConverter(sourceType, targetType, typeConverter))) {
            mutableConversionService.addConverter(sourceType, targetType, typeConverter);
        }
    }

    /**
     * @return The mutable conversion service.
     */
    @Internal
    public MutableConversionService getMutableConversionService() {
        return mutableConversionService;
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
     * @return Whether environment names and packages should be deduced
     */
    protected boolean shouldDeduceEnvironments() {
        if (deduceEnvironments != null) {
            log.debug("Environment deduction was set explicitly via builder to: {}", deduceEnvironments);

            return deduceEnvironments;
        } else if (configuration.isEnableDefaultPropertySources()) {
            String deduceProperty = CachedEnvironment.getProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY);
            String deduceEnv = CachedEnvironment.getenv(Environment.DEDUCE_ENVIRONMENT_ENV);

            if (StringUtils.isNotEmpty(deduceEnv)) {
                boolean deduce = Boolean.parseBoolean(deduceEnv);
                log.debug("Environment deduction was set via environment variable to: {}", deduce);
                return deduce;
            } else if (StringUtils.isNotEmpty(deduceProperty)) {
                boolean deduce = Boolean.parseBoolean(deduceProperty);
                log.debug("Environment deduction was set via system property to: {}", deduce);
                return deduce;
            } else {
                boolean deduceDefault = DEDUCE_ENVIRONMENT_DEFAULT;
                log.debug("Environment deduction is using the default of: {}", deduceDefault);
                return deduceDefault;
            }
        } else {
            return false;
        }
    }

    /**
     * @return Whether cloud environment should be deduced based on environment variable, system property or configuration
     */
    protected boolean shouldDeduceCloudEnvironment() {
        String deduceEnv = CachedEnvironment.getenv(Environment.DEDUCE_CLOUD_ENVIRONMENT_ENV);
        if (StringUtils.isNotEmpty(deduceEnv)) {
            boolean deduce = Boolean.parseBoolean(deduceEnv);
            log.debug("Cloud environment deduction was set via environment variable to: {}", deduce);
            return deduce;
        }
        String deduceProperty = CachedEnvironment.getProperty(Environment.DEDUCE_CLOUD_ENVIRONMENT_PROPERTY);
        if (StringUtils.isNotEmpty(deduceProperty)) {
            boolean deduce = Boolean.parseBoolean(deduceProperty);
            log.debug("Cloud environment deduction was set via system property to: {}", deduce);
            return deduce;
        }
        return configuration.isDeduceCloudEnvironment();
    }

    /**
     * Creates the default annotation scanner.
     *
     * @param classLoader The class loader
     * @return The scanner
     */
    protected BeanIntrospectionScanner createAnnotationScanner(ClassLoader classLoader) {
        return new BeanIntrospectionScanner();
    }

    /**
     * @return The property source root name
     */
    protected String getPropertySourceRootName() {
        return DEFAULT_NAME;
    }

    /**
     * @param name The name to read property sources
     */
    protected void readPropertySources(String name) {
        refreshablePropertySources.clear();
        List<PropertySource> propertySources;
        if (configuration.isEnableDefaultPropertySources()) {
            propertySources = readPropertySourceList(name);
            addDefaultPropertySources(propertySources);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
            }
            String propertySourcesEnv = readPropertySourceListKeyFromEnvironment();
            if (propertySourcesEnv != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
            }
            refreshablePropertySources.addAll(propertySources);
            readConstantPropertySources(name, propertySources);
        } else {
            propertySources = new ArrayList<>(this.propertySources.size());
        }

        long number;
        List<PropertySource> registered;
        synchronized (registrationLock) {
            number = ++registrySnapshots;
            registered = new ArrayList<>(this.propertySources.values());
        }
        Set<PropertySource> taken = Collections.newSetFromMap(new IdentityHashMap<>());
        taken.addAll(registered);
        pendingSnapshot = new RegistrySnapshot(number, taken);
        propertySources.addAll(registered);
        OrderUtil.sortOrdered(propertySources);
        for (PropertySource propertySource : propertySources) {
            log.debug("Processing property source: {}", propertySource.getName());
            processPropertySource(propertySource, propertySource.getConvention());
        }
    }

    private void readConstantPropertySources(String name, List<PropertySource> propertySources) {
        Set<String> activeNames = getActiveNames();
        Set<String> propertySourceNames = CollectionUtils.newHashSet(activeNames.size() + 1);
        propertySourceNames.add(name);
        for (String env : activeNames) {
            propertySourceNames.add(name + "-" + env);
        }
        for (PropertySource p : getConstantPropertySources()) {
            if (propertySourceNames.contains(p.getName())) {
                propertySources.add(p);
            }
        }
    }

    /**
     * @return Property sources created at build time
     */
    protected List<PropertySource> getConstantPropertySources() {
        return CONSTANT_PROPERTY_SOURCES;
    }

    /**
     * Reads the value of MICRONAUT_CONFIG_FILES environment variable.
     *
     * @return The comma-separated list of files
     */
    protected String readPropertySourceListKeyFromEnvironment() {
        return CachedEnvironment.getenv(StringUtils.convertDotToUnderscore(Environment.PROPERTY_SOURCES_KEY));
    }

    /**
     * Resolve the property sources for files passed via system property and system env.
     *
     * @param files The comma separated list of files
     * @return The list of property sources for each file
     */
    protected List<PropertySource> readPropertySourceListFromFiles(String files) {
        List<PropertySource> propertySources = new ArrayList<>();
        Collection<PropertySourceLoader> propertySourceLoaders = getPropertySourceLoaders();
        Optional<Collection<String>> filePathList = Optional.ofNullable(files)
            .filter(value -> !value.isEmpty())
            .map(value -> value.split(FILE_SEPARATOR))
            .map(Arrays::asList)
            .map(Collections::unmodifiableList);

        filePathList.ifPresent(list -> {
            if (!list.isEmpty()) {
                int order = AbstractPropertySourceLoader.DEFAULT_POSITION + 50;
                for (String filePath: list) {
                    if (!propertySourceLoaders.isEmpty()) {
                        String extension = NameUtils.extension(filePath);
                        String fileName = NameUtils.filename(filePath);
                        Optional<PropertySourceLoader> propertySourceLoader = Optional.ofNullable(loaderByFormatMap.get(extension));
                        if (propertySourceLoader.isPresent()) {
                            log.debug("Reading property sources from loader: {}", propertySourceLoader);
                            Optional<Map<String, Object>> properties = readPropertiesFromLoader(fileName, filePath, propertySourceLoader.get());
                            if (properties.isPresent()) {
                                propertySources.add(PropertySource.of(filePath, properties.get(), order));
                            }
                            order++;
                        } else {
                            throw new ConfigurationException("Unsupported properties file format while reading " + fileName + "." + extension + " from " + filePath);
                        }
                    }
                }
            }
        });
        return propertySources;
    }

    /**
     * @param name The name to resolver property sources
     * @return The list of property sources
     */
    protected List<PropertySource> readPropertySourceList(String name) {
        List<PropertySource> propertySources = new ArrayList<>();
        for (String configLocation : configLocations) {
            ResourceLoader resourceLoader;
            if (configLocation.equals("classpath:/")) {
                resourceLoader = this;
            } else if (configLocation.startsWith("classpath:")) {
                resourceLoader = this.forBase(configLocation);
            } else  if (configLocation.startsWith("file:")) {
                configLocation = configLocation.substring(5);
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
                log.debug("Reading property sources from loader: {}", propertySourceLoader);
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources, resourceLoader);
            }
        }
    }

    /**
     * Adds default property sources.
     *
     * @param propertySources The list of property sources
     */
    protected void addDefaultPropertySources(List<PropertySource> propertySources) {
        if (!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if (!this.propertySources.containsKey(EnvironmentPropertySource.NAME) && configuration.isEnvironmentPropertySource()) {
            List<String> includes = configuration.getEnvironmentVariableIncludes();
            List<String> excludes = configuration.getEnvironmentVariableExcludes();
            if (this.names.contains(Environment.KUBERNETES)) {
                propertySources.add(new KubernetesEnvironmentPropertySource(includes, excludes));
            } else {
                propertySources.add(new EnvironmentPropertySource(includes, excludes));
            }
        }
    }

    /**
     * @return Loaded properties as a {@link SoftServiceLoader}
     */
    protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
        return SoftServiceLoader.load(PropertySourceLoader.class, getClassLoader());
    }

    @Override
    public Optional<PropertyEntry> getPropertyEntry(String name) {
        for (PropertyCatalog propertyCatalog : PropertyCatalog.values()) {
            Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, propertyCatalog);
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
        Collection<PropertySourceLoader> propertySourceLoaderList = this.propertySourceLoaderList;
        if (propertySourceLoaderList == null) {
            synchronized (this) { // double check
                propertySourceLoaderList = this.propertySourceLoaderList;
                if (propertySourceLoaderList == null) {
                    propertySourceLoaderList = evaluatePropertySourceLoaders();
                    this.propertySourceLoaderList = propertySourceLoaderList;
                }
            }
        }
        return propertySourceLoaderList;
    }

    @SuppressWarnings("MagicNumber")
    private Collection<PropertySourceLoader> evaluatePropertySourceLoaders() {
        SoftServiceLoader<PropertySourceLoader> definitions = readPropertySourceLoaders();
        Collection<PropertySourceLoader> allLoaders = definitions.collectAll();
        for (PropertySourceLoader propertySourceLoader : allLoaders) {
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String extension : extensions) {
                loaderByFormatMap.put(extension, propertySourceLoader);
            }
        }
        return allLoaders;
    }

    private void loadPropertySourceFromLoader(String name, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources, ResourceLoader resourceLoader) {
        Optional<PropertySource> defaultPropertySource = propertySourceLoader.load(name, resourceLoader);
        defaultPropertySource.ifPresent(propertySources::add);
        Set<String> activeNames = getActiveNames();
        int i = 0;
        for (String activeName: activeNames) {
            Optional<PropertySource> propertySource = propertySourceLoader.loadEnv(name, resourceLoader, ActiveEnvironment.of(activeName, i));
            propertySource.ifPresent(propertySources::add);
            i++;
        }
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

    private EnvironmentsAndPackage getEnvironmentsAndPackage(List<String> specifiedNames) {
        EnvironmentsAndPackage environmentsAndPackage = this.environmentsAndPackage;
        boolean isNotFunction = !specifiedNames.contains(Environment.FUNCTION);
        final boolean deduceEnvironment = shouldDeduceEnvironments();
        final boolean deduceCloudEnvironmentUsingProbes = isNotFunction && shouldDeduceCloudEnvironment();
        if (environmentsAndPackage == null) {
            synchronized (EnvironmentsAndPackage.class) { // double check
                environmentsAndPackage = this.environmentsAndPackage;
                if (environmentsAndPackage == null) {
                    environmentsAndPackage = deduceEnvironmentsAndPackage(
                            deduceEnvironment,
                            deduceCloudEnvironmentUsingProbes,
                            isNotFunction,
                            !deduceCloudEnvironmentUsingProbes
                    );
                    this.environmentsAndPackage = environmentsAndPackage;
                }
            }
        }
        return environmentsAndPackage;
    }

    private static EnvironmentsAndPackage deduceEnvironmentsAndPackage(
            boolean deduceEnvironments,
            boolean deduceComputePlatform,
            boolean inspectTrace,
            boolean deduceFunctionPlatform
        ) {

        EnvironmentsAndPackage environmentsAndPackage = new EnvironmentsAndPackage();
        Set<String> environments = environmentsAndPackage.enviroments;

        if (inspectTrace) {
            performStackTraceInspection(deduceEnvironments, environmentsAndPackage, environments);
        }

        if (deduceEnvironments && !environments.contains(ANDROID)) {
            performEnvironmentDeduction(deduceComputePlatform, environments);
        }

        if (deduceFunctionPlatform) {
            performFunctionDeduction(environments);
        }

        return environmentsAndPackage;
    }

    private static void performFunctionDeduction(Set<String> environments) {
        // deduce AWS Lambda
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(AWS_LAMBDA_FUNCTION_NAME_ENV))) {
            environments.add(Environment.AMAZON_EC2);
            environments.add(Environment.CLOUD);
        }
    }

    private static void performEnvironmentDeduction(boolean deduceComputePlatform, Set<String> environments) {
        // deduce k8s
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(K8S_ENV))) {
            environments.add(Environment.KUBERNETES);
            environments.add(Environment.CLOUD);
        }
        // deduce CF
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(PCF_ENV))) {
            environments.add(Environment.CLOUD_FOUNDRY);
            environments.add(Environment.CLOUD);
        }

        // deduce heroku
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(HEROKU_DYNO))) {
            environments.add(Environment.HEROKU);
            environments.add(Environment.CLOUD);
            deduceComputePlatform = false;
        }

        // deduce GAE
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(GOOGLE_APPENGINE_ENVIRONMENT))) {
            environments.add(Environment.GAE);
            environments.add(Environment.GOOGLE_COMPUTE);
            environments.add(Environment.CLOUD);
            deduceComputePlatform = false;
        }

        if (deduceComputePlatform) {
            performComputePlatformDeduction(environments);
        }
    }

    private static void performComputePlatformDeduction(Set<String> environments) {
        ComputePlatform computePlatform = determineCloudProvider();
        if (computePlatform != null) {
            switch (computePlatform) {
                case GOOGLE_COMPUTE:
                    //instantiate bean for GC metadata discovery
                    environments.add(GOOGLE_COMPUTE);
                    environments.add(Environment.CLOUD);
                    break;
                case AMAZON_EC2:
                    //instantiate bean for ec2 metadata discovery
                    environments.add(AMAZON_EC2);
                    environments.add(Environment.CLOUD);
                    break;
                case ORACLE_CLOUD:
                    environments.add(ORACLE_CLOUD);
                    environments.add(Environment.CLOUD);
                    break;
                case AZURE:
                    // not yet implemented
                    environments.add(AZURE);
                    environments.add(Environment.CLOUD);
                    break;
                case IBM:
                    // not yet implemented
                    environments.add(IBM);
                    environments.add(Environment.CLOUD);
                    break;
                case DIGITAL_OCEAN:
                    environments.add(DIGITAL_OCEAN);
                    environments.add(Environment.CLOUD);
                    break;
                case OTHER:
                    // do nothing here
                    break;
                default:
                    // no-op
            }
        }
    }

    private static void performStackTraceInspection(boolean deduceEnvironments, EnvironmentsAndPackage environmentsAndPackage, Set<String> environments) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int len = stackTrace.length;
        for (int i = 0; i < len; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String className = stackTraceElement.getClassName();

            analyzeStackTraceElement(deduceEnvironments, environmentsAndPackage, environments, stackTrace, len, i, stackTraceElement, className);
        }
    }

    private static void analyzeStackTraceElement(boolean deduceEnvironments, EnvironmentsAndPackage environmentsAndPackage, Set<String> environments, StackTraceElement[] stackTrace, int len, int i, StackTraceElement stackTraceElement, String className) {
        if (className.startsWith("io.micronaut")) {
            int nextIndex = i + 1;
            if (nextIndex < len) {
                StackTraceElement next = stackTrace[nextIndex];
                if (!next.getClassName().startsWith("io.micronaut")) {
                    environmentsAndPackage.aPackage = NameUtils.getPackageName(next.getClassName());
                }
            }
        }

        if (stackTraceElement.getMethodName().contains("$spock_")) {
            environmentsAndPackage.aPackage = NameUtils.getPackageName(className);
        }

        if (deduceEnvironments) {
            if (Stream.of("org.spockframework", "org.junit", "io.kotlintest", "io.kotest").anyMatch(className::startsWith)) {
                environments.add(TEST);
            }

            if (className.startsWith("com.android")) {
                environments.add(ANDROID);
            }
        }
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

    private static ComputePlatform determineCloudProvider() {
        String computePlatform = CachedEnvironment.getProperty(CLOUD_PLATFORM_PROPERTY);
        if (computePlatform != null) {

            try {
                return ComputePlatform.valueOf(computePlatform);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Illegal value specified for [" + CLOUD_PLATFORM_PROPERTY + "]: " + computePlatform);
            }

        }
        boolean isWindows = CachedEnvironment.getProperty("os.name")
            .toLowerCase().startsWith("windows");

        if (isWindows ? isEC2Windows() : isEC2Linux()) {
            return ComputePlatform.AMAZON_EC2;
        }

        if (isGoogleCompute()) {
            return ComputePlatform.GOOGLE_COMPUTE;
        }

        if (isWindows ? isOracleCloudWindows() : isOracleCloudLinux()) {
            return ComputePlatform.ORACLE_CLOUD;
        }

        if (isDigitalOcean()) {
            return ComputePlatform.DIGITAL_OCEAN;
        }

        //TODO check for azure and IBM
        //Azure - see https://blog.mszcool.com/index.php/2015/04/detecting-if-a-virtual-machine-runs-in-microsoft-azure-linux-windows-to-protect-your-software-when-distributed-via-the-azure-marketplace/
        //IBM - uses cloudfoundry, will have to use that to probe
        // if all else fails not a cloud server that we can tell
        return ComputePlatform.BARE_METAL;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isGoogleCompute() {
        try {
            InetAddress.getByName(GOOGLE_COMPUTE_METADATA);
            return true;
        } catch (Exception e) {
            // well not google then
        }
        return false;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isOracleCloudLinux() {
        return readFile(ORACLE_CLOUD_ASSET_TAG_FILE).toLowerCase().contains("oraclecloud");
    }

    private static Optional<Process> runWindowsCmd(String cmd) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("cmd.exe", "/c", cmd);
            builder.redirectErrorStream(true);
            builder.directory(new File(CachedEnvironment.getProperty("user.home")));
            Process process = builder.start();
            return Optional.of(process);
        } catch (IOException e) {

        }
        return Optional.empty();
    }

    private static StringBuilder readProcessStream(Process process) {
        StringBuilder stdout = new StringBuilder();

        try {
            //Read out dir output
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                stdout.append(line);
            }
        } catch (IOException e) {
            // ignore
        }

        return stdout;
    }

    private static boolean isOracleCloudWindows() {
        Optional<Process> optionalProcess = runWindowsCmd(ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD);
        if (!optionalProcess.isPresent()) {
            return false;
        }
        Process process = optionalProcess.get();
        StringBuilder stdout = readProcessStream(process);

        //Wait to get exit value
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0 && stdout.toString().toLowerCase().contains("oraclecloud")) {
                return true;
            }
        } catch (InterruptedException e) {
            // test negative
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean isEC2Linux() {
        if (readFile(EC2_LINUX_HYPERVISOR_FILE).startsWith("ec2")) {
            return true;
        } else if (readFile(EC2_LINUX_BIOS_VENDOR_FILE).toLowerCase().startsWith("amazon ec2")) {
            return true;
        }

        return false;
    }

    private static String readFile(String path) {
        try {
            Path pathPath = Paths.get(path);
            if (!Files.exists(pathPath)) {
                return "";
            }
            return new String(Files.readAllBytes(pathPath)).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean isEC2Windows() {
        Optional<Process> optionalProcess = runWindowsCmd(EC2_WINDOWS_HYPERVISOR_CMD);
        if (!optionalProcess.isPresent()) {
            return false;
        }
        Process process = optionalProcess.get();
        StringBuilder stdout = readProcessStream(process);
        //Wait to get exit value
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0 && stdout.toString().startsWith("EC2")) {
                return true;
            }
        } catch (InterruptedException e) {
            // test negative
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean isDigitalOcean() {
        return "digitalocean".equalsIgnoreCase(readFile(DO_SYS_VENDOR_FILE));
    }

    @Override
    public void close() {
        // queued whole for the refresh of another thread if this thread has a call queued, see addPropertySource
        if (!queueIfCallQueued(this::closeNow)) {
            closeNow();
        }
    }

    /**
     * Closes this environment as {@link #close()} does once it takes effect: closes what resolves
     * placeholders, then stops the environment.
     */
    private void closeNow() {
        try {
            super.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close!", e);
        }
        stop();
    }

    /**
     * Helper class for handling environments and package.
     */
    private static class EnvironmentsAndPackage {
        String aPackage;
        Set<String> enviroments = new LinkedHashSet<>(1);
    }

    /**
     * The registered property sources a start or a refresh took, by identity.
     *
     * @param number          Which snapshot it is
     * @param propertySources The property sources
     */
    private record RegistrySnapshot(long number, Set<PropertySource> propertySources) {
    }

    /**
     * A call that another thread made while a refresh was active, which that refresh carries out, see
     * {@link #queuedCalls}.
     */
    private interface QueuedCall {
    }

    /**
     * A stop(), start() or refresh() that another thread made while a refresh was active. A refresh
     * is queued when it gave up waiting for the active refresh.
     */
    private enum QueuedLifecycleCall implements QueuedCall {
        STOP, START, REFRESH
    }

    /**
     * A property source that another thread added while a refresh was active.
     *
     * @param propertySource          The property source
     * @param name                    The name it was registered by
     * @param convention              The convention it is processed with
     * @param snapshotsWhenRegistered How many snapshots had been taken when it was registered
     */
    private record QueuedAdd(PropertySource propertySource, String name,
                             PropertySource.PropertyConvention convention,
                             long snapshotsWhenRegistered) implements QueuedCall {
    }

    /**
     * A call that changes this environment, made by a thread that had a call queued already, see
     * {@link #hasQueuedCall()}: an add, a remove, an added package, configuration includes or
     * excludes, a converter, or close(). The refresh makes the change in its turn.
     *
     * @param change The change, as the call makes it once it takes effect
     */
    private record QueuedChange(Runnable change) implements QueuedCall {
    }

    /**
     * What a {@link #refreshAndDiff()} reports: the generated catalog before the refresh read, and
     * what the refresh changed, or {@code null} while it has published nothing.
     */
    private static final class Difference {
        private Map<String, DefaultPropertyEntry>[] before;
        private Map<String, Object> changes;
    }
}
