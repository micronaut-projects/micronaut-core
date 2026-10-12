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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.reload.AnnotatedBeanRetentionPolicy;
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.context.reload.PolicyRetentionCriteria;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.context.reload.ReloadCompletedEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.reload.RequestAdmission;
import io.micronaut.context.reload.LauncherCloseActions;
import io.micronaut.context.reload.RequestAdmissionHolder;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.dev.agent.DynamicAttach;
import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.change.OutputSnapshot;
import io.micronaut.dev.compile.CompileMode;
import io.micronaut.dev.compile.OutputLock;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.http.DevServerSockets;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.livereload.LiveReloadServerFactory;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevMode;
import io.micronaut.dev.manifest.ResourceRoot;
import io.micronaut.dev.test.TestRunSummary;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher;
import io.micronaut.runtime.context.scope.refresh.RefreshResult;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileChange;
import io.micronaut.scheduling.io.watch.FileChangeBatch;
import io.micronaut.scheduling.io.watch.FileWatcherRegistration;
import io.micronaut.scheduling.io.watch.event.WatchEventType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The engine of development mode: watches the sources and resources the manifest names, compiles what
 * changed, and restarts the application on a new generation of the reloadable class loader, keeping
 * the singletons a {@link BeanRetentionPolicy} retains.
 *
 * <p>One runtime runs the process ({@link #current()}); the {@link DevApplicationContextConfigurer}
 * finds it from any context the application builds. A change is handled as one batch on the
 * runtime's own thread: sources are compiled per language, the class output is compared with the
 * previous snapshot, and a difference in classes restarts the application while a difference in
 * resources only reaches the resource watches of the running context. A difference in the class output
 * that holds no class, such as a Python module whose generated classes stayed the same, is first offered to
 * the running context's {@link io.micronaut.context.reload.InPlaceResourceReloader}s, which patch it into the
 * running application without a restart. A configuration file
 * change is refreshed in place, and restarts only when the refresh cannot apply it. A failed
 * compilation leaves the running generation as it is and is reported until the next success.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class DevRuntime implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final AtomicReference<DevRuntime> CURRENT = new AtomicReference<>();
    /**
     * The system property that has {@code BeanIntrospector.SHARED} read the introspections of the thread's context class loader.
     */
    private static final String INTROSPECTIONS_USE_CONTEXT_CLASSLOADER = "micronaut.introspections.use.context.classloader";
    /**
     * How long the worker waits for another batch after the last one it took: the watcher delivers the batches of
     * one save-all's roots within a few milliseconds of each other, and its quiet period already groups each root's.
     */
    private static final Duration COALESCE_IDLE = Duration.ofMillis(25);
    /**
     * How long the worker merges batches that keep arriving at most, from the first.
     */
    private static final Duration COALESCE_MAX = Duration.ofMillis(500);
    private static final Duration APP_STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FAILURE_WAIT = Duration.ofSeconds(2);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(5);

    private final DevManifest manifest;
    private final DevClassLoader classLoader;
    // held while compiling and while reading the outputs whole: another process of the project may be compiling into them
    private final OutputLock outputLock;
    private final ApplicationLauncher launcher;
    private final Map<SourceKind, SourceCompiler> compilers;
    private final Map<SourceKind, SourceKind> jointOwners;
    private final Map<Path, ResourceKind> resourceRootKinds = new LinkedHashMap<>();
    private final LinkedBlockingQueue<Pending> pending = new LinkedBlockingQueue<>();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong batchesHandled = new AtomicLong();
    private final Object lifecycle = new Object();
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    private volatile CompletableFuture<ApplicationContext> started = new CompletableFuture<>(); // NOSONAR java:S3077 swapped whole per generation; the future is thread-safe
    private volatile CompletableFuture<Void> ready = CompletableFuture.completedFuture(null); // NOSONAR java:S3077 swapped whole per generation; the future is thread-safe
    private volatile @Nullable ApplicationContext context; // NOSONAR java:S3077 only the reference is published; the context is thread-safe
    private volatile @Nullable Thread applicationThread; // NOSONAR java:S3077 only the reference is published
    /**
     * What the application's main of the last generation launched threw, once its context had started: a server that
     * cannot bind its port stops the context again, and main then throws why.
     */
    private volatile AtomicReference<@Nullable Throwable> applicationFailure = new AtomicReference<>(); // NOSONAR java:S3077 replaced whole per generation; the reference is thread-safe
    private volatile @Nullable CompileFailure lastFailure; // NOSONAR java:S3077 immutable failure, replaced whole
    private volatile Collection<BeanRegistration<?>> retainedForNext = List.of(); // NOSONAR java:S3077 replaced whole, never mutated once published
    private volatile String[] arguments = new String[0]; // NOSONAR java:S3077 copied in and out, never mutated in place
    private volatile int retainedCount;
    private volatile boolean closed;
    private final GenerationMemory memory;
    /**
     * Whether the runtime closed because its generation budget was spent, for its launcher to relaunch the process.
     */
    private volatile boolean relaunchRequested;
    private final Consumer<DevRuntime> relaunch;
    /**
     * Whether the last generation failed to start, so that the next batch must launch one whether or not a class changed.
     */
    private volatile boolean startFailed;
    private @Nullable DirectoryWatcher watcher;
    private final Map<Path, FileWatcherRegistration> pinnedWatches = new ConcurrentHashMap<>();
    private @Nullable LiveReloadServer liveReload;
    private @Nullable Instrumentation instrumentation;
    private final InPlaceUpdates inPlaceUpdates = new InPlaceUpdates(this);
    private @Nullable Thread worker;
    private OutputSnapshot snapshot = OutputSnapshot.empty();
    /**
     * The changes of the batch whose compilation failed, merged into the next batch until they compile: the next edit
     * may touch another file only, which an incremental compilation would compile alone.
     */
    private @Nullable Pending failedBatch;
    private final @Nullable TestSession tests;
    private final RestartRequests requests;
    private final DevModeNotice notice = new DevModeNotice();
    private final DevLogging logging = new DevLogging();

    /**
     * Creates the runtime; {@link #start(String[])} runs it.
     *
     * @param manifest The manifest
     * @param classLoader The reloadable loader, over the manifest's roots
     * @param launcher How the application's main is run
     * @param compilers The compilers, by language
     */
    DevRuntime(DevManifest manifest, DevClassLoader classLoader, ApplicationLauncher launcher, Map<SourceKind, SourceCompiler> compilers) {
        this(manifest, classLoader, launcher, compilers, runtime -> { });
    }

    /**
     * Creates the runtime; {@link #start(String[])} runs it.
     *
     * @param manifest The manifest
     * @param classLoader The reloadable loader, over the manifest's roots
     * @param launcher How the application's main is run
     * @param compilers The compilers, by language
     * @param relaunch What the launcher does once the runtime closed because its generation budget is spent
     */
    DevRuntime(DevManifest manifest, DevClassLoader classLoader, ApplicationLauncher launcher, Map<SourceKind, SourceCompiler> compilers,
               Consumer<DevRuntime> relaunch) {
        listIntrospectionsOfTheReloadableTier();
        this.relaunch = relaunch;
        this.manifest = manifest;
        this.classLoader = classLoader;
        this.memory = new GenerationMemory(classLoader);
        this.launcher = launcher;
        this.compilers = new EnumMap<>(SourceKind.class);
        this.compilers.putAll(compilers);
        this.jointOwners = Compilations.jointOwners(manifest, this.compilers);
        this.outputLock = Compilations.outputLock(manifest);
        for (ResourceRoot root : manifest.resourceRoots()) {
            resourceRootKinds.put(root.path().toAbsolutePath().normalize(), root.kind());
        }
        this.tests = manifest.mode() == DevMode.TEST ? new TestSession(this, manifest, this.compilers) : null;
        // in test mode the tests start their own servers, each on a port of its own
        this.requests = new RestartRequests(manifest, tests == null && manifest.retainServerSockets());
    }

    /**
     * @return The runtime running this process, or null outside development mode
     */
    @Nullable
    public static DevRuntime current() {
        return CURRENT.get();
    }

    /**
     * Lets {@code BeanIntrospector.SHARED} list the introspections of the reloadable tier: it reads those of its own
     * loader, the runtime classpath's, unless {@value #INTROSPECTIONS_USE_CONTEXT_CLASSLOADER} tells it to read those of
     * the thread's context class loader, which on a generation's threads is that generation's loader. Without it, a
     * module that lists introspections, as Micronaut Data's schema generation lists the entities, sees none of the
     * application's. A system property, set for this development JVM only, unless the launch set it.
     */
    private static void listIntrospectionsOfTheReloadableTier() {
        if (System.getProperty(INTROSPECTIONS_USE_CONTEXT_CLASSLOADER) == null) {
            System.setProperty(INTROSPECTIONS_USE_CONTEXT_CLASSLOADER, "true");
        }
    }

    /**
     * The compilers registered as services, those available in this JVM.
     *
     * @return The compilers by language
     */
    public static Map<SourceKind, SourceCompiler> availableCompilers() {
        Map<SourceKind, SourceCompiler> compilers = new EnumMap<>(SourceKind.class);
        for (SourceCompiler compiler : ServiceLoader.load(SourceCompiler.class, SourceCompiler.class.getClassLoader())) {
            if (compiler.isAvailable()) {
                for (SourceKind kind : compiler.kinds()) {
                    compilers.putIfAbsent(kind, compiler);
                }
            }
        }
        return compilers;
    }

    /**
     * Runs the application and starts watching. The application's main runs on its own thread; this
     * returns once the first generation's context has started, or throws when it could not.
     *
     * @param args The application's arguments
     * @return The started context
     * @throws IllegalStateException if another runtime runs the process or the application failed to start
     */
    public ApplicationContext start(String[] args) {
        if (tests != null) {
            throw new IllegalStateException("The manifest is in test mode: startTests() runs the tests");
        }
        if (!CURRENT.compareAndSet(null, this)) {
            throw new IllegalStateException("A development runtime already runs this process");
        }
        // servers that hold requests at their own level follow the gate filter; never in test mode (tests run in a batch)
        if (!RequestAdmissionHolder.install(requests)) {
            LOG.warn("Another launcher's request admission is installed: servers that hold requests at their own level follow it");
        }
        // only the application thread needs the generation loader; the caller's thread keeps its own
        arguments = args.clone();
        ApplicationContext first;
        RuntimeException notRunning;
        try {
            memory.initializeParentTierStatics();
            snapshot = generationSnapshot();
            if (manifest.strategy() != ReloadStrategy.RESTART && !NativeImageUtils.inImageRuntimeCode()) {
                // the fast path needs an agent: the launcher's, or one attached now. A native image has none: its
                // Instrumentation cannot redefine a class, and it cannot attach one
                instrumentation = DynamicAttach.instrumentation();
                if (instrumentation != null && !instrumentation.isRedefineClassesSupported()) {
                    instrumentation = null;
                }
            }
            startLiveReload();
            startWatching();
            Thread thread = new Thread(this::processBatches, "micronaut-dev-reload");
            thread.setDaemon(true);
            thread.start();
            worker = thread;
            first = launch("first start");
            // as after a restart: the servers bind a moment after the context starts, and the first generation is
            // only reported started once they accept, when a retained socket was handed to one
            ApplicationContext launched = first;
            Thread launching = applicationThread;
            AtomicReference<@Nullable Throwable> failure = applicationFailure;
            boolean definesServer = requests.started(launched, () -> context == launched, () -> launching != null && launching.isAlive());
            notRunning = notRunning(launched, launching, failure, definesServer, "first start");
            if (notRunning == null) {
                // of the generation that ran; when it did not, the first reload that runs one logs the note
                notice.logOnce(LOG, strategy(), liveReload, definesServer, definesServer && manifest.liveReload().injectScript(), launched, access());
            }
            if (notRunning != null && !markStartFailed(launched, notRunning)) {
                // a change launched the next generation meanwhile: it is the one that runs, or not
                notRunning = null;
            }
        } catch (RuntimeException e) {
            // nothing of a runtime that failed to start may linger: a retry in the same JVM must be possible
            close();
            throw e;
        }
        // once the first generation launched, not to race it
        reconcileOutputs();
        if (notRunning != null) {
            // as after a failed reload: the runtime keeps watching, and the next change launches a generation
            LOG.error("Development mode: {}; a change restarts it", notRunning.getMessage(), notRunning.getCause());
            return first;
        }
        LOG.info("Development mode: generation {} started with strategy {}, {} compiler(s), watching {} root(s)",
            classLoader.generation(), strategy(), compilers.keySet(), watchedRoots().size());
        return first;
    }

    /**
     * The LiveReload server, when {@code micronaut-dev-livereload} is on the classpath.
     *
     * @return The server
     */
    public Optional<LiveReloadServer> liveReload() {
        return Optional.ofNullable(liveReload);
    }

    /**
     * The path a browser would request a file of a resource root under: the file relative to its root,
     * with a leading slash, or the file name alone for a file under no root.
     *
     * @param file The file
     * @return The path
     */
    public String publicPathOf(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path root = mostSpecificRoot(absolute);
        String relative = root != null ? root.relativize(absolute).toString() : absolute.getFileName().toString();
        return "/" + relative.replace(java.io.File.separatorChar, '/');
    }

    /**
     * Requests a reload without waiting for it: for a caller the reload would stop, such as an endpoint
     * of the application.
     */
    public void requestReload() {
        enqueue(new Pending(Map.of(), Map.of(), true));
    }

    /**
     * @return The manifest
     */
    public DevManifest manifest() {
        return manifest;
    }

    /**
     * @return Who may reach the development pages of this launch: the hosts answered and the launch's access token
     */
    public DevAccess access() {
        return requests.access();
    }

    /**
     * @return The reloadable loader
     */
    public DevClassLoader classLoader() {
        return classLoader;
    }

    /**
     * @return The number of the current generation
     */
    public int generation() {
        return classLoader.generation();
    }

    /**
     * @return The strategy in force: RESTART when the manifest asks for it or no agent is available, otherwise
     *         the manifest's, under which a change to method bodies alone is applied in place and anything else restarts
     */
    public ReloadStrategy strategy() {
        return instrumentation == null ? ReloadStrategy.RESTART : manifest.strategy();
    }

    /**
     * @return How many times a change was applied in place, by redefining method bodies, since the start
     */
    public int redefinitions() {
        return inPlaceUpdates.redefinitions();
    }

    /**
     * @return How many times a change of resources alone was patched into the running application by an
     *         {@link InPlaceResourceReloader}, without a restart, since the start
     */
    public int inPlacePatches() {
        return inPlaceUpdates.inPlacePatches();
    }

    /**
     * @return The context of the current generation, once it started
     */
    public Optional<ApplicationContext> context() {
        return Optional.ofNullable(context);
    }

    /**
     * @return The compilation that failed last, if no compilation succeeded since
     */
    public Optional<CompileFailure> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    /**
     * @return How many beans the current generation was handed from the previous one
     */
    public int retainedCount() {
        return retainedCount;
    }

    /**
     * The one watcher of the process, over the manifest's roots, which the {@link io.micronaut.scheduling.io.watch.FileWatcher}
     * bean of every context registers through.
     *
     * @return The watcher, or null before the runtime started watching
     */
    @Nullable
    DirectoryWatcher fileWatcher() {
        return watcher;
    }

    /**
     * @return The directories outside the roots that a context's file watcher bean registered, kept watched for the process
     */
    Map<Path, FileWatcherRegistration> pinnedWatches() {
        return pinnedWatches;
    }

    /**
     * @return The roots the runtime watches
     */
    public List<Path> watchedRoots() {
        DirectoryWatcher directoryWatcher = watcher;
        return directoryWatcher == null ? List.of() : directoryWatcher.watchedDirectories();
    }

    /**
     * Whether a reload is in progress: sources compiling or the application restarting.
     *
     * @return True while it is
     */
    public boolean isReloading() {
        return !ready.isDone();
    }

    /**
     * Completes when no reload is in progress; at once when none is.
     *
     * @return The future
     */
    public CompletableFuture<Void> whenReady() {
        return ready;
    }

    /**
     * Whether the last generation failed to start: no generation runs until the next batch launches one.
     *
     * @return True when it failed
     */
    public boolean isStartFailed() {
        return startFailed;
    }

    /**
     * Completes when a request that arrived during a batch may proceed: once the batch is done, or once a restart
     * drains the generation the request arrived on, which serves it before it stops. At once when no batch runs.
     *
     * @return The future
     */
    public CompletableFuture<Void> whenAdmitted() {
        return requests.admission();
    }

    /**
     * The admission of requests across batches and restarts, with the manifest's hold and drain timeouts: the one
     * {@link RequestAdmission#current()} gives a server runtime while this runtime runs the application.
     *
     * @return The admission
     */
    public RequestAdmission requestAdmission() {
        return requests;
    }

    /**
     * The listening sockets the HTTP servers of every generation accept on, kept bound across restarts; none in test
     * mode or when {@value DevManifest#REQUESTS_RETAIN_SOCKETS} is off.
     *
     * @return The sockets
     */
    public Optional<DevServerSockets> serverSockets() {
        return requests.serverSockets();
    }

    /**
     * Compiles every source and restarts if any class changed: the manual trigger, for an IDE that
     * saved without the watcher noticing, or after a failed compilation was fixed elsewhere.
     */
    public void reload() {
        awaitBatch(enqueue(new Pending(Map.of(), Map.of(), true)));
    }

    /**
     * Applies a change something other than the watcher reports, a test harness or an IDE: the files are
     * sorted into the source and resource roots they are under, enqueued as the watcher would enqueue
     * them, behind any batch already pending, and the batch is awaited. A file under no root is ignored.
     *
     * @param changed The files written
     * @param deleted The files deleted
     * @since 5.3.0
     */
    @Internal
    public void changed(Collection<Path> changed, Collection<Path> deleted) {
        Map<SourceKind, SourceChanges> sources = new EnumMap<>(SourceKind.class);
        Map<SourceKind, SourceChanges> testSources = new EnumMap<>(SourceKind.class);
        Map<ResourceKind, SourceChanges> resources = new EnumMap<>(ResourceKind.class);
        sort(changed, false, sources, testSources, resources);
        sort(deleted, true, sources, testSources, resources);
        awaitBatch(enqueue(new Pending(sources, testSources, resources, false, null)));
    }

    private void sort(Collection<Path> files, boolean deleted, Map<SourceKind, SourceChanges> sources, Map<SourceKind, SourceChanges> testSources,
                      Map<ResourceKind, SourceChanges> resources) {
        for (Path file : files) {
            Path absolute = file.toAbsolutePath().normalize();
            SourceRoot sourceRoot = null;
            for (SourceRoot root : manifest.sourceRoots()) {
                if (absolute.startsWith(root.path()) && root.kind().matches(absolute)) {
                    sourceRoot = root;
                    break;
                }
            }
            SourceChanges change = deleted ? new SourceChanges(Set.of(), Set.of(absolute)) : new SourceChanges(Set.of(absolute), Set.of());
            if (sourceRoot != null) {
                sources.merge(sourceRoot.kind(), change, SourceChanges::merge);
                continue;
            }
            SourceRoot testRoot = null;
            for (SourceRoot root : manifest.testSourceRoots()) {
                if (absolute.startsWith(root.path()) && root.kind().matches(absolute)) {
                    testRoot = root;
                    break;
                }
            }
            if (testRoot != null) {
                testSources.merge(testRoot.kind(), change, SourceChanges::merge);
                continue;
            }
            Path resourceRoot = mostSpecificRoot(absolute);
            if (resourceRoot != null) {
                ResourceKind kind = resourceRootKinds.get(resourceRoot);
                if (deleted) {
                    for (Path removed : liveRemoved(absolute)) {
                        Path removedRoot = mostSpecificRoot(removed);
                        if (removedRoot != null) {
                            resources.merge(resourceRootKinds.get(removedRoot), new SourceChanges(Set.of(), Set.of(removed)), SourceChanges::merge);
                        }
                    }
                } else {
                    classLoader.liveResources().seen(absolute);
                    resources.merge(kind, change, SourceChanges::merge);
                }
            } else {
                LOG.debug("{} is under no source or resource root: ignored", absolute);
            }
        }
    }

    /**
     * Handles source changes as the watcher reports them, and waits for the reload.
     *
     * @param kind The language
     * @param changed The files added or modified
     * @param deleted The files deleted
     */
    void sourcesChanged(SourceKind kind, Set<Path> changed, Set<Path> deleted) {
        awaitBatch(enqueue(new Pending(Map.of(kind, new SourceChanges(changed, deleted)), Map.of(), false)));
    }

    /**
     * Restarts the application on a new generation without compiling anything.
     */
    public void restart() {
        awaitBatch(enqueue(new Pending(Map.of(), Map.of(), false) {
            @Override
            boolean forcesRestart() {
                return true;
            }
        }));
    }

    /**
     * Waits for a generation's context to start.
     *
     * @param generation The generation, counted from one
     * @param timeout How long to wait
     * @return The context
     * @throws TimeoutException if the generation did not start in time
     */
    public ApplicationContext awaitGeneration(int generation, Duration timeout) throws TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            ApplicationContext current = context;
            CompletableFuture<ApplicationContext> generationStart = started;
            if (classLoader.generation() >= generation && generationStart.isCompletedExceptionally() && !isReloading()) {
                throw new IllegalStateException("Generation " + classLoader.generation() + " failed to start", generationStart.handle((c, e) -> e).join());
            }
            if (current != null && classLoader.generation() >= generation && generationStart.isDone() && !isReloading()) {
                return current;
            }
            if (System.nanoTime() > deadline) {
                throw new TimeoutException("Generation " + generation + " did not start within " + timeout);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted", e);
            }
        }
    }

    /**
     * Called by the configurer with a context the application built.
     *
     * @param applicationContext The context
     */
    @Internal
    void contextCreated(ApplicationContext applicationContext) {
        this.context = applicationContext;
    }

    /**
     * Called by the startup listener once a generation's context started.
     *
     * @param applicationContext The context
     */
    @Internal
    void contextStarted(ApplicationContext applicationContext) {
        this.context = applicationContext;
        reportResourceState(applicationContext);
        started.complete(applicationContext);
    }

    /**
     * The registrations retained from the previous generation, handed over once.
     *
     * @return The registrations
     */
    @Internal
    Collection<BeanRegistration<?>> takeRetainedRegistrations() {
        Collection<BeanRegistration<?>> retained = retainedForNext;
        retainedForNext = List.of();
        retainedCount = retained.size();
        return retained;
    }

    /**
     * Runs the runtime in test mode: watches, compiles what changes, and runs the tests it affects on a new
     * generation each time. Returns once the first run, of every test, finished, or at once when the settings ask for
     * no first run.
     *
     * @return The first run, or null when there was none
     * @throws IllegalStateException if the manifest is not in test mode or another runtime runs the process
     */
    public @Nullable TestRunSummary startTests() {
        TestSession session = tests;
        if (session == null) {
            throw new IllegalStateException("The manifest is not in test mode: start(String[]) runs the application");
        }
        if (!CURRENT.compareAndSet(null, this)) {
            throw new IllegalStateException("A development runtime already runs this process");
        }
        try {
            memory.initializeParentTierStatics();
            snapshot = generationSnapshot();
            // what the class files depend on and which are tests, before any change comes: a first change that removes a
            // constant, or deletes a test, needs to know the state it changes
            session.prime();
            startLiveReload();
            startWatching();
            reconcileOutputs();
            Thread thread = new Thread(this::processBatches, "micronaut-dev-reload");
            thread.setDaemon(true);
            thread.start();
            worker = thread;
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        LOG.info("Test mode: {} compiler(s), runner {}, watching {} root(s), reports in {}",
            compilers.keySet(), session.settings().runner(), watchedRoots().size(), session.settings().reports());
        if (!session.settings().initialRun() && !session.settings().once()) {
            return null;
        }
        awaitBatch(enqueue(new Pending(Map.of(), Map.of(), Map.of(), false, TestRequest.ALL)));
        return session.lastRun();
    }

    /**
     * Runs tests a person or a tool asked for, in test mode, behind any change pending, and waits for the run.
     *
     * @param request Which tests
     * @return The run, or null when nothing ran
     */
    public @Nullable TestRunSummary requestTests(TestRequest request) {
        TestSession session = requireTests();
        int before = session.runs();
        awaitBatch(enqueue(new Pending(Map.of(), Map.of(), Map.of(), false, request)));
        return session.runs() > before ? session.lastRun() : null;
    }

    /**
     * Turns watching on or off, in test mode: while it is off, changes compile but run no test until one is asked for.
     *
     * @param watching Whether a change runs the tests it affects
     */
    public void watchTests(boolean watching) {
        requireTests().watching(watching);
    }

    /**
     * @return Whether a change runs the tests it affects, in test mode
     */
    public boolean isWatchingTests() {
        return requireTests().isWatching();
    }

    /**
     * @return The last test run, in test mode
     */
    public Optional<TestRunSummary> lastTestRun() {
        return Optional.ofNullable(requireTests().lastRun());
    }

    /**
     * @return How many test runs finished, in test mode
     */
    public int testRuns() {
        return requireTests().runs();
    }

    /**
     * @return The test classes that failed in their last run, in test mode
     */
    public Set<String> failedTestClasses() {
        return requireTests().failedClasses();
    }

    /**
     * Waits for a test run to finish, in test mode.
     *
     * @param run The run, counted from one
     * @param timeout How long to wait
     * @return The run
     * @throws TimeoutException if it did not finish in time
     */
    public TestRunSummary awaitTestRun(int run, Duration timeout) throws TimeoutException {
        return requireTests().awaitRun(run, timeout);
    }

    private TestSession requireTests() {
        TestSession session = tests;
        if (session == null) {
            throw new IllegalStateException("The manifest is not in test mode");
        }
        return session;
    }

    /**
     * @return The languages compiled jointly, by owner
     */
    Map<SourceKind, SourceKind> jointOwners() {
        return jointOwners;
    }

    /**
     * @return The lock of the outputs, held to read them whole
     */
    OutputLock outputLock() {
        return outputLock;
    }

    /**
     * What the current generation loads: the copy of the reloadable roots it was created over, the baseline the first
     * change is told from even when another process of the project compiled after the copy was made.
     */
    private OutputSnapshot generationSnapshot() {
        List<Path> roots = classLoader.current().roots();
        return OutputSnapshot.of(roots.subList(classLoader.liveRoots().size(), roots.size()));
    }

    /**
     * Another process of the project may have compiled since the outputs were last compared, between the first
     * generation's copy and the watches, for an edit no watch saw, or before a new generation's copy: what it wrote is
     * applied as a build tool's output would be.
     */
    private GenerationClassLoader swapGeneration() {
        GenerationClassLoader retired;
        try (OutputLock.Hold hold = outputLock.lock()) {
            retired = classLoader.swap();
        }
        // what another process compiled since the outputs were compared is in the new generation, not in what it was
        // told changed: the next batch tells it, as it would a build tool's output
        reconcileOutputs();
        return retired;
    }

    private void reconcileOutputs() {
        if (!snapshot.diff(outputSnapshot()).isEmpty()) {
            enqueue(outputsChanged());
        }
    }

    private OutputSnapshot outputSnapshot() {
        try (OutputLock.Hold hold = outputLock.lock()) {
            return OutputSnapshot.of(manifest.reloadableRoots());
        }
    }

    /**
     * @return What changed in the reloadable roots since {@link #takeOutputChanges()} was last asked, which stays to take
     */
    ChangeSet peekOutputChanges() {
        return snapshot.diff(outputSnapshot());
    }

    /**
     * What changed in the reloadable roots since the last time this was asked, which a new generation will see.
     *
     * @return The changes
     */
    ChangeSet takeOutputChanges() {
        OutputSnapshot latest = outputSnapshot();
        ChangeSet changes = snapshot.diff(latest);
        snapshot = latest;
        return changes;
    }

    /**
     * Retires the current generation for a new one over the roots as they are now.
     *
     * @return The new generation's loader
     */
    ClassLoader newGeneration() {
        swapGeneration();
        logging.beforeGeneration(classLoader.current());
        return classLoader.current();
    }

    void compilationFailed(CompileFailure failure) {
        lastFailure = failure;
        if (LOG.isErrorEnabled()) {
            LOG.error("{}", failure.describe().strip());
        }
    }

    void compilationRecovered() {
        lastFailure = null;
    }

    /**
     * Whether the generation budget, {@link DevManifest#maxGenerations()}, is spent: one more generation would exceed it.
     *
     * @return True when the next reload must relaunch the process instead
     */
    public boolean isGenerationBudgetSpent() {
        int budget = manifest.maxGenerations();
        return budget > 0 && classLoader.generation() >= budget;
    }

    /**
     * @return Whether the runtime closed because its generation budget was spent, for its launcher to relaunch the process
     */
    public boolean isRelaunchRequested() {
        return relaunchRequested;
    }

    /**
     * Closes the runtime because its generation budget is spent, then tells the launcher, which relaunches the
     * process. The change that found the budget spent was compiled already: the next process starts from it.
     */
    void requestRelaunch() {
        synchronized (lifecycle) {
            if (closed || relaunchRequested) {
                return;
            }
            relaunchRequested = true;
        }
        LOG.info("Generation {} reached the budget of {} generations ({}): closing for the process to be relaunched",
            classLoader.generation(), manifest.maxGenerations(), DevManifest.MAX_GENERATIONS);
        // not on the reload thread, which close() interrupts and which may hold the lock of a batch
        Thread thread = new Thread(() -> {
            try {
                close();
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("The runtime did not close cleanly before the relaunch: {}", e.getMessage(), e);
            } finally {
                relaunch.accept(this);
            }
        }, "micronaut-dev-relaunch");
        thread.setDaemon(false);
        thread.start();
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
        }
        try {
            TestSession session = tests;
            if (session != null) {
                session.cancelRun();
            }
            Thread thread = worker;
            if (thread != null) {
                thread.interrupt();
            }
            DirectoryWatcher directoryWatcher = watcher;
            if (directoryWatcher != null) {
                directoryWatcher.close();
            }
            LiveReloadServer server = liveReload;
            if (server != null) {
                server.close();
            }
            ApplicationContext current = context;
            if (current != null && current.isRunning()) {
                GenerationStop.stop(current);
            }
            requests.close();
            for (SourceCompiler compiler : new LinkedHashSet<>(compilers.values())) {
                compiler.close();
            }
        } finally {
            // whatever failed to stop: the threads of the shared schedulers end with them, and the schedule hook
            // keeps this runtime's loader no longer
            memory.releaseReactorThreads();
            // what modules kept across this runtime's contexts, such as a warm GraalPy engine, and the generation it holds
            LauncherCloseActions.runAll();
            // whatever failed to stop, whoever waits for the runtime to close is released
            RequestAdmissionHolder.uninstall(requests);
            CURRENT.compareAndSet(this, null);
            closedLatch.countDown();
        }
    }

    /**
     * Waits until the runtime is closed, by a shutdown hook, a key, or a tool. Test mode runs no application thread to
     * keep the JVM alive, so its launcher waits here.
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public void awaitClose() throws InterruptedException {
        closedLatch.await();
    }

    /**
     * Compiles in full every language whose class output does not exist yet, so that the launcher
     * works from a clean checkout as well as after a build. Runs before the loader takes its first
     * snapshot.
     *
     * @param manifest The manifest
     * @param compilers The compilers by language
     * @throws IllegalStateException if a compilation fails
     */
    public static void compileMissingOutputs(DevManifest manifest, Map<SourceKind, SourceCompiler> compilers) {
        Compilations.compileMissingOutputs(manifest, compilers);
    }

    /**
     * LiveReload is on when a server implementation is on the classpath, {@code micronaut-dev-livereload}:
     * adding the module to the development runtime classpath is the switch, there is no other.
     */
    private void startLiveReload() {
        LiveReloadServerFactory factory = ServiceLoader.load(LiveReloadServerFactory.class, LiveReloadServerFactory.class.getClassLoader()).findFirst().orElse(null);
        if (factory == null) {
            return;
        }
        try {
            liveReload = factory.start(manifest.liveReload().port(), manifest.liveReload().allowedOrigins());
        } catch (IOException e) {
            LOG.warn("LiveReload server could not bind port {}: {}", manifest.liveReload().port(), e.getMessage());
        }
    }

    private void startWatching() {
        DirectoryWatcher directoryWatcher;
        try {
            DevWatchService watchService = DevWatchService.create();
            DirectoryWatcher.Builder builder = DirectoryWatcher.builder(watchService.service())
                .registrar(watchService.registrar())
                .threadName("micronaut-dev-watcher");
            directoryWatcher = builder.closeWatchServiceOnClose(watchService.closeOnClose()).build().start();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start the file watcher", e);
        }
        for (SourceRoot root : manifest.sourceRoots()) {
            if (!Files.isDirectory(root.path())) {
                continue;
            }
            if (manifest.compileMode(root.kind()) == CompileMode.EMBEDDED && !compilers.containsKey(root.kind())) {
                LOG.warn("No embedded compiler for {} sources under {}: changes there are picked up from the class output only", root.kind(), root.path());
            }
            // both spellings: the glob syntax alone leaves a file directly under the root out of "**/"
            String[] globs = root.kind().extensions().stream().flatMap(extension -> Stream.of("*." + extension, "**/*." + extension)).toArray(String[]::new);
            directoryWatcher.directory(root.path()).include(globs).watch(batch -> enqueue(sourceBatch(root, batch)));
        }
        for (SourceRoot root : manifest.testSourceRoots()) {
            if (tests != null && Files.isDirectory(root.path())) {
                String[] globs = root.kind().extensions().stream().flatMap(extension -> Stream.of("*." + extension, "**/*." + extension)).toArray(String[]::new);
                directoryWatcher.directory(root.path()).include(globs).watch(batch -> {
                    Pending sources = sourceBatch(root, batch);
                    enqueue(new Pending(Map.of(), sources.sources, Map.of(), false, null));
                });
            }
        }
        for (ResourceRoot root : manifest.resourceRoots()) {
            if (Files.isDirectory(root.path())) {
                directoryWatcher.directory(root.path()).watch(batch -> enqueue(resourceBatch(root, batch)));
            }
        }
        // the languages the build tool compiles, the tests' included in test mode: the dev JVM only sees their class output
        List<DevManifest> targets = new ArrayList<>(List.of(manifest));
        if (tests != null) {
            targets.add(tests.tests());
        }
        boolean external = false;
        for (DevManifest target : targets) {
            for (SourceKind kind : SourceKind.values()) {
                external |= !target.sourceRoots(kind).isEmpty()
                    && (target.compileMode(kind) == CompileMode.BUILD_TOOL || !compilers.containsKey(kind));
            }
        }
        if (external) {
            Path trigger = manifest.buildToolTrigger();
            if (trigger != null) {
                // the build tool touches the trigger when its compilation finished: only then is the output whole
                Path directory = trigger.toAbsolutePath().getParent();
                if (directory != null) {
                    try {
                        Files.createDirectories(directory);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Cannot create the trigger directory " + directory, e);
                    }
                    String name = trigger.getFileName().toString();
                    directoryWatcher.directory(directory).recursive(false).include(name).watch(batch -> enqueue(outputsChanged()));
                }
            } else {
                // without a trigger the class output itself is watched, which may see a compilation half written
                LOG.warn("No micronaut.dev.build-tool.trigger configured: the class output is watched directly and a restart may see a compilation in progress");
                for (DevManifest target : targets) {
                    for (SourceKind kind : SourceKind.values()) {
                        Path output = target.classOutput(kind);
                        if (!target.sourceRoots(kind).isEmpty() && Files.isDirectory(output) && !directoryWatcher.isWatching(output)) {
                            directoryWatcher.directory(output).watch(batch -> enqueue(outputsChanged()));
                        }
                    }
                }
            }
        }
        watcher = directoryWatcher;
    }

    /**
     * A batch for outputs the build tool wrote: no source to compile, yet never empty, since what it changed is found
     * in the class output when the batch is handled.
     */
    private static Pending outputsChanged() {
        return new Pending(Map.of(), Map.of(), false) {
            @Override
            boolean isEmpty() {
                return false;
            }
        };
    }

    private static Pending sourceBatch(SourceRoot root, FileChangeBatch batch) {
        Set<Path> changed = new LinkedHashSet<>();
        Set<Path> deleted = new LinkedHashSet<>();
        for (FileChange change : batch.changes()) {
            if (!root.kind().matches(change.path())) {
                continue;
            }
            if (change.type() == WatchEventType.DELETE) {
                deleted.add(change.path());
            } else {
                changed.add(change.path());
            }
        }
        return new Pending(Map.of(root.kind(), new SourceChanges(changed, deleted)), Map.of(), false);
    }

    private Pending resourceBatch(ResourceRoot root, FileChangeBatch batch) {
        Set<Path> changed = new LinkedHashSet<>();
        Set<Path> removed = new LinkedHashSet<>();
        Path own = root.path().toAbsolutePath().normalize();
        for (FileChange change : batch.changes()) {
            if (Files.isDirectory(change.path()) || !own.equals(mostSpecificRoot(change.path()))) {
                // a file under a nested root, such as static files under the configuration root, belongs to that root
                continue;
            }
            if (change.type() == WatchEventType.DELETE) {
                for (Path file : liveRemoved(change.path())) {
                    if (own.equals(mostSpecificRoot(file))) {
                        removed.add(file);
                    }
                }
            } else {
                classLoader.liveResources().seen(change.path());
                changed.add(change.path());
            }
        }
        return new Pending(Map.of(), Map.of(root.kind(), new SourceChanges(changed, removed)), false);
    }

    /**
     * The resources a deletion removes: the file deleted, or every file known under a directory deleted as a whole,
     * whose events may name only the directory. Each is recorded as belonging to its live root, so that the build
     * output's copy of it is not served in its place.
     */
    private Set<Path> liveRemoved(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Set<Path> removed = new LinkedHashSet<>();
        removed.add(absolute);
        List<Path> under = classLoader.liveResources().knownUnder(absolute);
        if (under.isEmpty()) {
            classLoader.liveResources().seen(absolute);
        } else {
            removed.addAll(under);
        }
        return removed;
    }

    /**
     * The resource changes of a batch as the file system has them now, a late report of a write or a deletion settled.
     */
    private Map<ResourceKind, SourceChanges> settledResources(Map<ResourceKind, SourceChanges> resources) {
        Map<ResourceKind, SourceChanges> settled = ResourceNotifier.settle(resources, this::liveRemoved, file -> {
            Path root = mostSpecificRoot(file);
            return root == null ? null : resourceRootKinds.get(root);
        });
        logging.resourcesChanged(settled, classLoader.current());
        return settled;
    }

    /**
     * The deepest resource root a file is under, which decides its kind when roots nest.
     */
    @Nullable
    private Path mostSpecificRoot(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path best = null;
        for (Path root : resourceRootKinds.keySet()) {
            if (absolute.startsWith(root) && (best == null || root.getNameCount() > best.getNameCount())) {
                best = root;
            }
        }
        return best;
    }

    /**
     * @return The sequence number of the batch, which {@link #awaitBatch} waits for
     */
    long enqueue(Pending batch) {
        if (closed || batch.isEmpty()) {
            // a watcher over a nesting root sees the files of the nested one too, and keeps none of them
            return completed.get();
        }
        long sequence = submitted.incrementAndGet();
        batch.sequence = sequence;
        pending.add(batch);
        TestSession session = tests;
        if (session != null) {
            // the next run covers what the run under way covers and this change too
            session.cancelRun(batch);
        }
        return sequence;
    }

    /**
     * @return How many merged batches the worker has handled
     */
    long batchesHandled() {
        return batchesHandled.get();
    }

    /**
     * Waits until the worker has handled the batch with the given sequence number, whichever merge it
     * ended up in.
     */
    void awaitBatch(long sequence) {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos() + APP_STOP_TIMEOUT.toNanos();
        while (completed.get() < sequence && !closed) {
            if (System.nanoTime() > deadline) {
                LOG.warn("The reload did not complete in time");
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processBatches() {
        while (!closed) {
            Pending first;
            try {
                first = pending.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // the gate closes as soon as a batch is taken, so a caller waiting for the reload sees it in progress, and
            // a request waits for the batch: the gate filter holds those on open connections, and the servers stop
            // accepting new ones, which wait in the backlog for whichever generation runs once the batch is done
            CompletableFuture<Void> gate = new CompletableFuture<>();
            ready = gate;
            CompletableFuture<Void> admission = requests.batchStarted();
            List<Pending> batches = new ArrayList<>();
            batches.add(first);
            try {
                // one save-all touches several roots: their batches arrive within a few milliseconds of each other
                coalesce(batches);
                long handling = System.nanoTime();
                handle(Pending.merge(batches));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Handled {} batch(es) in {} ms, {} ms after the first was delivered", batches.size(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - handling), TimeUnit.NANOSECONDS.toMillis(handling - first.delivered));
                }
            } catch (InterruptedException e) {
                admission.complete(null);
                Thread.currentThread().interrupt();
                gate.complete(null);
                return;
            } catch (Throwable e) { // NOSONAR a reload that fails, with a LinkageError of the edited code too, leaves the reload thread running
                LOG.error("Reload failed: {}", e.getMessage(), e);
            } finally {
                // the timeouts follow the generation's configuration before whoever awaits the batch sees it done
                requests.batchDone(admission, context);
                long last = 0;
                for (Pending batch : batches) {
                    last = Math.max(last, batch.sequence);
                }
                completed.accumulateAndGet(last, Math::max);
                batchesHandled.incrementAndGet();
                gate.complete(null);
            }
        }
    }

    /**
     * Takes the batches that follow the first, until none came for {@link #COALESCE_IDLE} or they kept coming for
     * {@link #COALESCE_MAX}, so that a lone edit is handled right after the watcher delivers it.
     */
    private void coalesce(List<Pending> batches) throws InterruptedException {
        long deadline = System.nanoTime() + COALESCE_MAX.toNanos();
        while (true) {
            long wait = Math.min(COALESCE_IDLE.toNanos(), deadline - System.nanoTime());
            Pending next = wait > 0 ? pending.poll(wait, TimeUnit.NANOSECONDS) : null;
            if (next == null) {
                pending.drainTo(batches);
                return;
            }
            batches.add(next);
        }
    }

    /**
     * Compiles a batch's changes for the languages of a manifest, holding the output lock: see
     * {@link Compilations#round}.
     */
    CompileRound compileRound(DevManifest target, Map<SourceKind, SourceKind> joint, Map<SourceKind, SourceChanges> changes, boolean full, Set<String> seed) {
        try (OutputLock.Hold hold = outputLock.lock()) {
            return Compilations.round(compilers, target, joint, changes, full, seed);
        }
    }

    private void handle(Pending next) {
        TestSession session = tests;
        if (session != null) {
            session.handle(next.sources, next.testSources, settledResources(next.resources), next.full, next.requested);
            return;
        }
        long start = System.nanoTime();
        Pending failed = failedBatch;
        Pending batch = next;
        if (failed != null && (next.full || !next.sources.isEmpty())) {
            // a batch that compiles takes the failed sources with it; a restart or a resource change alone compiles
            // nothing and runs the last output that compiled, the failed sources waiting for the next compilation
            failedBatch = null;
            batch = Pending.merge(List.of(failed, next));
        }
        // compile what changed, or everything on the manual trigger; a failure leaves the generation as it is
        CompileRound round = compileRound(manifest, jointOwners, batch.sources, batch.full, Set.of());
        if (round.failure() != null) {
            lastFailure = round.failure();
            LOG.error("{}", round.failure().describe().strip());
            // nothing of the batch reached the application: its changes come again with the next one that compiles
            failedBatch = batch;
            if (batch.forcesRestart()) {
                // a restart asked for runs the output that compiled last, as it does alone, though a late report of
                // the broken source came with it; the failed changes wait for the next compilation without it. The
                // batch's resources reach the watches first, and its configuration change releases what it touched
                failedBatch = new Pending(batch.sources, batch.testSources, Map.of(), batch.full, batch.requested);
                ApplicationContext current = context;
                ConfigurationChange configurationChange = null;
                if (ResourceNotifier.notify(current, settledResources(batch.resources), this::rootsOf, this::refreshBrowsers)) {
                    RefreshResult refresh = refreshConfiguration(current);
                    configurationChange = refresh == null ? ConfigurationChange.ofAll() : refresh.change();
                }
                OutputSnapshot latest = outputSnapshot();
                ChangeSet changeSet = snapshot.diff(latest);
                snapshot = latest;
                restart(changeSet, configurationChange, start);
            }
            return;
        }
        boolean compiled = round.compiled();
        Set<SourceKind> compiledKinds = round.compiledKinds();
        CompileFailure failure = lastFailure;
        if (failure != null && compiledKinds.contains(failure.kind())) {
            // the broken language compiles again; a batch that did not touch it leaves the failure shown
            lastFailure = null;
        }
        // every resource change reaches the running context's watches, those under the configuration root too;
        // a configuration file changed also refreshes the configuration
        ApplicationContext current = context;
        boolean configurationChanged = ResourceNotifier.notify(current, settledResources(batch.resources), this::rootsOf, this::refreshBrowsers);
        OutputSnapshot previous = snapshot;
        OutputSnapshot latest = outputSnapshot();
        ChangeSet changeSet = previous.diff(latest);
        snapshot = latest;
        logging.outputChanged(changeSet);
        ConfigurationChange configurationChange = null;
        if (configurationChanged) {
            // the running context reads the file again and applies what it can: the configuration beans are
            // rebound, the refreshable beans disposed of, the watches told; a watch that cannot apply its change
            // answers REQUIRES_RESTART, and a bean retained across restarts that watched a touched prefix is not
            // kept when the restart comes
            ConfigurationStaleness.Requirements requirements = ConfigurationStaleness.beforeRefresh(current);
            RefreshResult refresh = refreshConfiguration(current);
            if (refresh == null) {
                configurationChange = ConfigurationChange.ofAll();
            } else {
                configurationChange = refresh.change();
                String stale = ConfigurationStaleness.afterRefresh(current, refresh.change(), requirements);
                if (stale != null) {
                    LOG.info("Restarting for the configuration change: {}", stale);
                } else if (!refresh.requiresRestart() && changeSet.isEmpty() && !batch.forcesRestart() && !startFailed) {
                    LOG.info("Configuration refreshed in place: {} bean(s) rebound, {} recreated, {} refreshable disposed of, {} watch(es) told",
                        refresh.rebound().size(), refresh.recreated().size(), refresh.disposed(), refresh.outcomes().size());
                    return;
                }
                if (refresh.requiresRestart()) {
                    LOG.info("A configuration watch needs a restart to apply the change");
                }
            }
        }
        // a generation reads its snapshot: a resource the build wrote under a reloadable root, such as a service
        // descriptor, needs a new generation as a class does; a failed start needs one whatever changed
        if (changeSet.isEmpty() && configurationChange == null && !batch.forcesRestart() && !startFailed) {
            if (compiled) {
                LOG.info("Nothing to reload: the classes did not change");
            }
            return;
        }
        if (configurationChange == null && !batch.forcesRestart() && !startFailed && inPlaceUpdates.patchInPlace(context, changeSet, previous, start)) {
            return;
        }
        if (configurationChange == null && !batch.forcesRestart() && !startFailed && inPlaceUpdates.redefine(instrumentation, context, changeSet, start)) {
            return;
        }
        restart(changeSet, configurationChange, start);
    }

    /**
     * Refreshes the configuration of the running context.
     *
     * @return The result, or null when the context is not running or has no refresher, which restarts instead
     */
    @Nullable
    static RefreshResult refreshConfiguration(@Nullable ApplicationContext current) {
        if (current == null || !current.isRunning()) {
            return null;
        }
        try {
            return current.findBean(ConfigurationRefresher.class).map(ConfigurationRefresher::refresh).orElse(null);
        } catch (RuntimeException e) {
            LOG.warn("The configuration could not be refreshed in place ({}): restarting instead", e.getMessage());
            return null;
        }
    }

    /**
     * @return The resources of the reloadable roots as they were last compared, which the next change is compared with
     */
    Set<String> outputResources() {
        return snapshot.resourcePaths();
    }

    /**
     * @return The current generation's loader
     */
    ClassLoader currentGeneration() {
        return classLoader.current();
    }

    InPlaceUpdates inPlaceUpdates() {
        return inPlaceUpdates;
    }

    private void restart(ChangeSet changeSet, @Nullable ConfigurationChange configurationChange, long startNanos) {
        if (isGenerationBudgetSpent()) {
            // the compiled classes stay in the class output: the relaunched process starts from them
            requestRelaunch();
            return;
        }
        ApplicationContext old = context;
        if (old != null && old.isRunning()) {
            // the requests held during the batch, and those in flight, finish on the generation they arrived on
            // before it stops; the servers stop accepting, and new connections wait for the next generation
            requests.beforeStop(old, classLoader.generation());
            if (closed || Thread.currentThread().isInterrupted()) {
                // the runtime closes: it stops the generation that still runs, and none is launched after it
                return;
            }
        }
        GenerationClassLoader retired = swapGeneration();
        Collection<BeanRegistration<?>> retained = List.of();
        if (old != null) {
            ClassChangeEvent event = new ClassChangeEvent(this, classLoader.retiredLoaders(), classLoader.current(), changeSet.classes(), ReloadStrategy.RESTART);
            try {
                old.publishEvent(event);
            } catch (RuntimeException e) {
                LOG.warn("A listener of the class change failed: {}", e.getMessage(), e);
            }
            if (old instanceof DefaultBeanContext defaultBeanContext && old.isRunning()) {
                retained = defaultBeanContext.stopRetaining(retentionCriteria(old, configurationChange));
            } else if (old.isRunning()) {
                old.stop();
            }
            awaitApplicationThread();
            // the swap emptied the shared caches, but what read the retired generation since, a restart watcher or
            // the stopping context, filled them again with its classes: forgotten again now that it no longer runs
            GenerationMemory.forgetRetired(retired);
        }
        // a thread a retained bean started in a retired generation took that generation's loader as its context
        // class loader, and keeps it as long as the bean lives
        memory.releaseThreads(Thread.currentThread(), applicationThread);
        retainedForNext = retained;
        started = new CompletableFuture<>();
        ApplicationContext fresh;
        try {
            fresh = launch("reload");
        } catch (RuntimeException e) {
            startFailed = true;
            requests.startFailed();
            // the retained beans belong to nobody now: the old context, stopped, still knows how to dispose them
            for (BeanRegistration<?> registration : takeRetainedRegistrations()) {
                try {
                    if (old != null) {
                        old.destroyBean(registration);
                    }
                } catch (RuntimeException destroyFailure) {
                    LOG.debug("Cannot destroy retained bean {}", registration, destroyFailure);
                }
            }
            throw e;
        }
        Thread launching = applicationThread;
        AtomicReference<@Nullable Throwable> failure = applicationFailure;
        boolean definesServer = requests.started(fresh, () -> context == fresh, () -> launching != null && launching.isAlive());
        RuntimeException notRunning = notRunning(fresh, launching, failure, definesServer, "reload");
        if (notRunning != null && markStartFailed(fresh, notRunning)) {
            // its context started, and stopped again: as when it cannot start at all, the next batch launches one
            throw notRunning;
        }
        startFailed = false;
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        List<BeanDefinition<?>> added = definitionsNamed(fresh, changeSet.classNames());
        fresh.publishEvent(new ReloadCompletedEvent(this, new ClassChangeEvent(this, classLoader.retiredLoaders(), classLoader.current(), changeSet.classes(), ReloadStrategy.RESTART), added, List.of(), elapsed));
        LOG.info("Reloaded: generation {} started in {} ms ({} class(es) changed, {} bean(s) retained)", classLoader.generation(), elapsed.toMillis(), changeSet.classes().size(), retainedCount);
        // when the first generation did not run, the first that does logs the note
        notice.logOnce(LOG, strategy(), liveReload, definesServer, definesServer && manifest.liveReload().injectScript(), fresh, access());
        LiveReloadServer server = liveReload;
        if (server != null) {
            // the new generation serves: the browsers see the new code
            server.reload("/", false);
        }
        detectLeaks();
    }

    /**
     * A change of static files or templates only needs the browser to refresh: a stylesheet is swapped in
     * place when nothing but stylesheets changed, anything else reloads the page.
     */
    private void refreshBrowsers(SourceChanges changes) {
        LiveReloadServer server = liveReload;
        if (server == null) {
            return;
        }
        boolean onlyCss = !changes.changed().isEmpty() && changes.deleted().isEmpty()
            && changes.changed().stream().allMatch(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".css"));
        if (onlyCss) {
            for (Path stylesheet : changes.changed()) {
                server.reload(publicPathOf(stylesheet), true);
            }
        } else {
            server.reload("/", false);
        }
    }

    /**
     * What a restart retains: what a policy retains and none refuses, unless a change touched a prefix one of those that
     * retain it declares for it, or a bean it holds or received is of a class of a generation, which the restart
     * replaces and the bean would keep running, or the change touched the prefix of a configuration bean in its closure.
     * No configuration bean is retained with it: the next generation binds each again.
     */
    private DefaultBeanContext.RetentionCriteria retentionCriteria(ApplicationContext old, @Nullable ConfigurationChange configurationChange) {
        List<BeanRetentionPolicy> policies = new ArrayList<>(old.getBeansOfType(BeanRetentionPolicy.class));
        if (manifest.retainAnnotated()) {
            // what the modules declare with @Retain, read from the definitions
            policies.add(AnnotatedBeanRetentionPolicy.INSTANCE);
        }
        PolicyRetentionCriteria criteria = new PolicyRetentionCriteria(policies,
            prefix -> configurationChange != null && configurationChange.touches(prefix),
            type -> type.getClassLoader() instanceof GenerationClassLoader);
        return new DefaultBeanContext.RetentionCriteria() {
            @Override
            public boolean retain(BeanRegistration<?> registration) {
                // a bean of the application is never retained, whatever a policy says, and needs no word in the log
                return !isStale(registration) && criteria.retain(registration);
            }

            @Override
            public Set<String> invalidatedBy(BeanRegistration<?> registration) {
                return criteria.invalidatedBy(registration);
            }

            @Override
            public boolean touches(String prefix) {
                return criteria.touches(prefix);
            }

            @Override
            public boolean isReplaced(Class<?> type) {
                return criteria.isReplaced(type);
            }

            @Override
            public @Nullable Object refusedBy(BeanRegistration<?> registration) {
                return criteria.refusedBy(registration);
            }
        };
    }

    private static boolean isStale(BeanRegistration<?> registration) {
        if (registration.getBeanDefinition().getClass().getClassLoader() instanceof GenerationClassLoader) {
            return true;
        }
        Object bean = registration.getBean();
        return bean.getClass().getClassLoader() instanceof GenerationClassLoader;
    }

    private ApplicationContext launch(String reason) {
        String[] args = arguments.clone();
        CompletableFuture<ApplicationContext> future = started;
        AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        applicationFailure = failure;
        // the generation loader, not the facade: a class the JVM resolved through the facade once would be
        // handed out again, from the retired generation, for as long as the facade lives
        GenerationClassLoader generation = classLoader.current();
        logging.beforeGeneration(generation);
        Thread thread = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(generation);
            try {
                launcher.launch(generation, manifest.mainClass(), args);
                if (!future.isDone()) {
                    // main returned without a context starting: a startup failure the application logged itself
                    future.completeExceptionally(new IllegalStateException("The application's main returned without starting a context"));
                }
            } catch (Throwable e) { // NOSONAR an Error from main fails the start at once, rather than when the start times out
                failure.set(e);
                future.completeExceptionally(e);
            }
        }, "micronaut-dev-app");
        thread.setDaemon(false);
        thread.start();
        applicationThread = thread;
        try {
            ApplicationContext applicationContext = future.get(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            // the startup event is published a moment before the context reports itself running
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!applicationContext.isRunning() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            return applicationContext;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while the application started (" + reason + ")", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("The application failed to start (" + reason + "): " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("The application did not start within " + START_TIMEOUT + " (" + reason + ")", e);
        }
    }

    /**
     * Why a generation whose context started does not run, once its servers were waited for: its context stopped
     * again, as it does when its HTTP server cannot bind its port, or a server it created does not run once its main
     * returned. A command line application whose main ran its context and stopped it, with no server, did not fail.
     *
     * @param generation The generation's context
     * @param launching The thread running its main
     * @param failure What its main threw, if it threw
     * @param definesServer Whether its context defines an HTTP server
     * @param reason What launched it, for the message
     * @return Why it does not run, or null when it does, or when it is no longer the current generation
     */
    private @Nullable RuntimeException notRunning(ApplicationContext generation, @Nullable Thread launching, AtomicReference<@Nullable Throwable> failure,
                                                  boolean definesServer, String reason) {
        if (context != generation) {
            return null;
        }
        boolean running = generation.isRunning();
        boolean mainRunning = launching != null && launching.isAlive();
        if (running && (mainRunning || RestartRequests.serversRun(generation))) {
            return null;
        }
        if (launching != null && mainRunning) {
            // the context stopped: main throws why a moment later
            try {
                launching.join(FAILURE_WAIT.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (context != generation) {
            // a change launched the next generation meanwhile
            return null;
        }
        Throwable cause = failure.get();
        if (cause == null && !definesServer) {
            return null;
        }
        String why;
        if (cause == null) {
            why = running ? "its HTTP server is not running" : "its context stopped right after it started";
        } else {
            why = cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage();
            Throwable root = cause;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root != cause && root.getMessage() != null && !why.contains(root.getMessage())) {
                why += " (" + root.getMessage() + ")";
            }
        }
        return new IllegalStateException("generation " + classLoader.generation() + " failed to start (" + reason + "): " + why, cause);
    }

    /**
     * Marks the start of a generation failed, as long as it is still the current generation: the next batch launches
     * one whether or not a class changed, a request is answered with a 503 meanwhile, and a caller waiting for the
     * generation sees the failure rather than its stopped context.
     *
     * @param generation The generation's context
     * @param failure Why it does not run
     * @return Whether it was still the current generation
     */
    private boolean markStartFailed(ApplicationContext generation, RuntimeException failure) {
        if (context != generation) {
            return false;
        }
        started = CompletableFuture.failedFuture(failure);
        startFailed = true;
        requests.startFailed();
        return true;
    }

    private void awaitApplicationThread() {
        Thread thread = applicationThread;
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(APP_STOP_TIMEOUT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            LOG.warn("The application thread of the previous generation is still running; the new generation starts beside it");
        }
    }

    private List<Path> rootsOf(ResourceKind kind) {
        List<Path> roots = new ArrayList<>();
        for (Map.Entry<Path, ResourceKind> entry : resourceRootKinds.entrySet()) {
            if (entry.getValue() == kind) {
                roots.add(entry.getKey());
            }
        }
        return roots;
    }

    /**
     * The initial resource state, for the watches of a context that just started.
     *
     * @param applicationContext The context
     */
    @Internal
    void reportResourceState(ApplicationContext applicationContext) {
        if (!(applicationContext instanceof DefaultBeanContext defaultBeanContext)) {
            return;
        }
        for (ResourceKind kind : ResourceKind.values()) {
            List<Path> roots = rootsOf(kind);
            if (roots.isEmpty()) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            for (Path root : roots) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile).forEach(files::add);
                } catch (IOException e) {
                    LOG.debug("Cannot list {}", root, e);
                }
            }
            defaultBeanContext.notifyResourceChange(new ResourceChange(kind, roots, files, List.of(), true));
        }
    }

    private static List<BeanDefinition<?>> definitionsNamed(ApplicationContext applicationContext, Set<String> classNames) {
        List<BeanDefinition<?>> definitions = new ArrayList<>();
        if (classNames.isEmpty()) {
            return definitions;
        }
        for (BeanDefinition<?> definition : applicationContext.getAllBeanDefinitions()) {
            if (classNames.contains(definition.getBeanType().getName())) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    void detectLeaks() {
        memory.detectLeaks();
    }

    /**
     * How the application's main is run, on the thread the runtime provides.
     */
    @FunctionalInterface
    public interface ApplicationLauncher {
        /**
         * Runs the application. Returns when main returns, which for a server is when the context stops.
         *
         * @param classLoader The loader to load the main class from
         * @param mainClass The main class name
         * @param args The arguments
         * @throws Exception if main throws
         */
        void launch(ClassLoader classLoader, String mainClass, String[] args) throws Exception; // NOSONAR the application's main may throw any exception
    }

    /**
     * What a compile round did.
     *
     * @param failure The compilation that failed, if one did: nothing after it was compiled
     * @param compiled Whether anything was compiled
     * @param compiledKinds The languages compiled
     * @param affectedClasses The classes the round changed, for the dependents in other languages and the tests
     */
    record CompileRound(@Nullable CompileFailure failure, boolean compiled, Set<SourceKind> compiledKinds, Set<String> affectedClasses) {
        static CompileRound failed(CompileFailure failure) {
            return new CompileRound(failure, false, Set.of(), Set.of());
        }
    }
}
