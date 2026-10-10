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
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.reload.AnnotatedBeanRetentionPolicy;
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.context.reload.ReloadCompletedEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.dev.agent.DynamicAttach;
import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.change.ClassStructure;
import io.micronaut.dev.change.OutputSnapshot;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.CompileMode;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
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
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileChange;
import io.micronaut.scheduling.io.watch.FileChangeBatch;
import io.micronaut.scheduling.io.watch.event.WatchEventType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
    private static final Duration COALESCE = Duration.ofMillis(150);
    private static final Duration APP_STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(5);
    private static final int LEAK_TOLERANCE = 2;
    private static final int MAX_PROPAGATION_PASSES = 5;

    private final DevManifest manifest;
    private final DevClassLoader classLoader;
    private final ApplicationLauncher launcher;
    private final Map<SourceKind, SourceCompiler> compilers;
    private final Map<SourceKind, SourceKind> jointOwners;
    private final Map<Path, ResourceKind> resourceRootKinds = new LinkedHashMap<>();
    private final LinkedBlockingQueue<Pending> pending = new LinkedBlockingQueue<>();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final Object lifecycle = new Object();
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    private volatile CompletableFuture<ApplicationContext> started = new CompletableFuture<>();
    private volatile CompletableFuture<Void> ready = CompletableFuture.completedFuture(null);
    private volatile @Nullable ApplicationContext context;
    private volatile @Nullable Thread applicationThread;
    private volatile @Nullable CompileFailure lastFailure;
    private volatile Collection<BeanRegistration<?>> retainedForNext = List.of();
    private volatile String[] arguments = new String[0];
    private volatile int retainedCount;
    private volatile long generationStartedNanos;
    private volatile boolean closed;
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
    private @Nullable LiveReloadServer liveReload;
    private @Nullable Instrumentation instrumentation;
    private volatile int redefinitions;
    private volatile int inPlacePatches;
    private @Nullable Thread worker;
    private OutputSnapshot snapshot = OutputSnapshot.empty();
    /**
     * The changes of the batch whose compilation failed, merged into the next batch until they compile: the next edit
     * may touch another file only, which an incremental compilation would compile alone.
     */
    private @Nullable Pending failedBatch;
    private final @Nullable TestSession tests;

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
        this.launcher = launcher;
        this.compilers = new EnumMap<>(SourceKind.class);
        this.compilers.putAll(compilers);
        this.jointOwners = jointOwners(manifest, this.compilers);
        for (ResourceRoot root : manifest.resourceRoots()) {
            resourceRootKinds.put(root.path().toAbsolutePath().normalize(), root.kind());
        }
        this.tests = manifest.mode() == DevMode.TEST ? new TestSession(this, manifest, this.compilers) : null;
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
        // only the application thread needs the generation loader; the caller's thread keeps its own
        arguments = args.clone();
        ApplicationContext first;
        try {
            snapshot = OutputSnapshot.of(manifest.reloadableRoots());
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
        } catch (RuntimeException e) {
            // nothing of a runtime that failed to start may linger: a retry in the same JVM must be possible
            close();
            throw e;
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
        return redefinitions;
    }

    /**
     * @return How many times a change of resources alone was patched into the running application by an
     *         {@link InPlaceResourceReloader}, without a restart, since the start
     */
    public int inPlacePatches() {
        return inPlacePatches;
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
                resources.merge(resourceRootKinds.get(resourceRoot), change, SourceChanges::merge);
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
            snapshot = OutputSnapshot.of(manifest.reloadableRoots());
            // what the class files depend on and which are tests, before any change comes: a first change that removes a
            // constant, or deletes a test, needs to know the state it changes
            session.prime();
            startLiveReload();
            startWatching();
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
     * What changed in the reloadable roots since the last time this was asked, which a new generation will see.
     *
     * @return The changes
     */
    ChangeSet takeOutputChanges() {
        OutputSnapshot latest = OutputSnapshot.of(manifest.reloadableRoots());
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
        classLoader.swap();
        generationStartedNanos = System.nanoTime();
        return classLoader.current();
    }

    void compilationFailed(CompileFailure failure) {
        lastFailure = failure;
        LOG.error("{}", failure.describe().strip());
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
                stopGeneration(current);
            }
            for (SourceCompiler compiler : new LinkedHashSet<>(compilers.values())) {
                compiler.close();
            }
        } finally {
            // whatever failed to stop, whoever waits for the runtime to close is released
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
     * Stops a generation as the shutdown hook {@code Micronaut.run} registers does: through the embedded
     * applications it started, then the context. The HTTP server's {@code stop()} holds the server's lock
     * while it stops the context, so stopping the context first, which stops the server bean under the
     * context's lock, takes the two locks in the opposite order; when the JVM exits, both hooks run at once
     * and would deadlock.
     */
    private static void stopGeneration(ApplicationContext generation) {
        // only the applications already created: looking one up must not create it while stopping. Each is stopped
        // whether it reports running or not, as the hook does: the hook's stop clears the flag before it stops the
        // context, and stopping the application waits for it, where stopping the context would deadlock with it
        for (BeanRegistration<EmbeddedApplication> registration : generation.getActiveBeanRegistrations(EmbeddedApplication.class)) {
            registration.getBean().stop();
        }
        if (generation.isRunning()) {
            generation.stop();
        }
    }

    /**
     * The roots a compilation of a language reads: its own, and for Kotlin the Java roots too, since kotlinc
     * resolves the Java sources of a mixed module and KSP processes them, and those of the languages it compiles
     * jointly; the index of a request takes only the roots of its own language as its sources.
     */
    private static List<SourceRoot> compilationRoots(DevManifest manifest, SourceKind kind, Map<SourceKind, SourceKind> jointOwners) {
        List<SourceRoot> roots = new ArrayList<>(manifest.sourceRoots(kind));
        if (kind == SourceKind.KOTLIN) {
            roots.addAll(manifest.sourceRoots(SourceKind.JAVA));
        }
        jointOwners.forEach((joint, owner) -> {
            if (owner == kind) {
                roots.addAll(manifest.sourceRoots(joint));
            }
        });
        return roots;
    }

    /**
     * The options of a compilation of a language: its own, then those of each language it compiles jointly,
     * since one compiler run compiles them all. A language's options are kept whole, a flag with its value,
     * and are not repeated when they are the same as the owner's.
     */
    private static List<String> compileOptions(DevManifest manifest, SourceKind kind, Map<SourceKind, SourceKind> jointOwners) {
        List<String> own = manifest.compileOptions(kind);
        List<String> options = new ArrayList<>(own);
        jointOwners.forEach((joint, owner) -> {
            List<String> jointOptions = manifest.compileOptions(joint);
            if (owner == kind && !jointOptions.equals(own)) {
                options.addAll(jointOptions);
            }
        });
        return options;
    }

    /**
     * The languages a compiler of another language compiles jointly (see {@link SourceCompiler#jointKinds()}):
     * those it names that have sources, are compiled in this JVM and share its class output.
     *
     * @param manifest The manifest
     * @param compilers The compilers by language
     * @return The owning language, by jointly compiled language
     */
    static Map<SourceKind, SourceKind> jointOwners(DevManifest manifest, Map<SourceKind, SourceCompiler> compilers) {
        Map<SourceKind, SourceKind> owners = new EnumMap<>(SourceKind.class);
        compilers.forEach((kind, compiler) -> {
            if (!embedded(manifest, kind)) {
                return;
            }
            for (SourceKind joint : compiler.jointKinds()) {
                if (joint != kind && embedded(manifest, joint) && manifest.classOutput(joint).equals(manifest.classOutput(kind))) {
                    owners.putIfAbsent(joint, kind);
                }
            }
        });
        return owners;
    }

    private static boolean embedded(DevManifest manifest, SourceKind kind) {
        return !manifest.sourceRoots(kind).isEmpty() && manifest.compileMode(kind) != CompileMode.BUILD_TOOL;
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
        Map<SourceKind, SourceKind> jointOwners = jointOwners(manifest, compilers);
        // decided before anything is compiled: two languages sharing one output are both missing, or neither
        Set<SourceKind> missing = new LinkedHashSet<>();
        for (SourceKind kind : compilers.keySet()) {
            if (!jointOwners.containsKey(kind) && embedded(manifest, kind) && !Files.isDirectory(manifest.classOutput(kind))) {
                missing.add(kind);
            }
        }
        for (Map.Entry<SourceKind, SourceCompiler> entry : compilers.entrySet()) {
            SourceKind kind = entry.getKey();
            if (!missing.contains(kind)) {
                continue;
            }
            CompilationRequest request = new CompilationRequest(kind, compilationRoots(manifest, kind, jointOwners), Set.of(), Set.of(), true, manifest.compileClasspath(),
                manifest.processorPath(), manifest.classOutput(kind), manifest.generatedSources(kind), compileOptions(manifest, kind, jointOwners)).asFull();
            CompilationResult result = entry.getValue().compile(request);
            if (!result.isSuccess()) {
                CompileFailure failure = new CompileFailure(kind, result.diagnostics(), Instant.now());
                throw new IllegalStateException(failure.describe());
            }
            LOG.info("Compiled {} {} source(s) in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
        }
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
            liveReload = factory.start(manifest.liveReload().port());
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
                removed.add(change.path());
            } else {
                changed.add(change.path());
            }
        }
        return new Pending(Map.of(), Map.of(root.kind(), new SourceChanges(changed, removed)), false);
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
    private long enqueue(Pending batch) {
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
            session.cancelRun();
        }
        return sequence;
    }

    /**
     * Waits until the worker has handled the batch with the given sequence number, whichever merge it
     * ended up in.
     */
    private void awaitBatch(long sequence) {
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
                return;
            }
            // the gate closes as soon as a batch is taken, so a caller waiting for the reload sees it in progress
            CompletableFuture<Void> gate = new CompletableFuture<>();
            ready = gate;
            List<Pending> batches = new ArrayList<>();
            batches.add(first);
            try {
                // one save-all touches several roots: their batches arrive within a few milliseconds of each other
                Thread.sleep(COALESCE.toMillis());
                pending.drainTo(batches);
                handle(Pending.merge(batches));
            } catch (InterruptedException e) {
                gate.complete(null);
                return;
            } catch (Throwable e) {
                LOG.error("Reload failed: {}", e.getMessage(), e);
            } finally {
                long last = 0;
                for (Pending batch : batches) {
                    last = Math.max(last, batch.sequence);
                }
                completed.accumulateAndGet(last, Math::max);
                gate.complete(null);
            }
        }
    }

    /**
     * Compiles a batch's changes for the languages of a manifest, the application's or its tests' view, and
     * what depends on them: the changed sources of each language, then, until nothing new is produced, the
     * sources of every language that reference a class another one changed.
     *
     * @param target The manifest whose roots, outputs and classpaths are compiled
     * @param joint The languages compiled jointly, by owner
     * @param changes The changed sources, by language
     * @param full Whether everything is compiled
     * @param seed The classes changed by an earlier compilation, whose dependents among these sources are compiled too
     * @return What was compiled, or the failure
     */
    CompileRound compileRound(DevManifest target, Map<SourceKind, SourceKind> joint, Map<SourceKind, SourceChanges> changes, boolean full, Set<String> seed) {
        Map<SourceKind, SourceChanges> sources = new EnumMap<>(SourceKind.class);
        changes.forEach((kind, change) -> sources.merge(joint.getOrDefault(kind, kind), change, SourceChanges::merge));
        // what another compilation changed reaches every language, as the changes of this batch do
        Set<SourceKind> kinds = full || !seed.isEmpty() ? compilers.keySet() : sources.keySet();
        boolean compiled = false;
        Set<SourceKind> compiledKinds = new LinkedHashSet<>();
        Set<String> affectedClasses = new LinkedHashSet<>();
        for (SourceKind kind : kinds) {
            SourceCompiler compiler = compilers.get(kind);
            if (compiler == null || joint.containsKey(kind) || !embedded(target, kind)) {
                continue;
            }
            SourceChanges change = sources.getOrDefault(kind, SourceChanges.NONE);
            CompilationRequest request = new CompilationRequest(kind, compilationRoots(target, kind, joint), change.changed(), change.deleted(),
                full || !target.isIncremental(), target.compileClasspath(), target.processorPath(),
                target.classOutput(kind), target.generatedSources(kind), compileOptions(target, kind, joint));
            if (!seed.isEmpty()) {
                request = request.withAffectedClasses(seed);
            }
            CompilationResult result = compiler.compile(request);
            if (!result.isSuccess()) {
                return CompileRound.failed(new CompileFailure(kind, result.diagnostics(), Instant.now()));
            }
            compiled = true;
            compiledKinds.add(kind);
            affectedClasses.addAll(result.compiledClasses());
            LOG.info("Compiled {} {} source(s) in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
        }
        // a change in one language reaches the others: their sources that reference a changed class are
        // recompiled too, and fail rather than keep bytecode linked against what is gone
        // until nothing new is produced: a Groovy class recompiled for a Java change may itself be what a
        // Java source depends on, and Java was visited first
        Set<String> propagated = new LinkedHashSet<>();
        for (int pass = 0; pass < MAX_PROPAGATION_PASSES && compilers.size() - joint.size() > 1 && !affectedClasses.equals(propagated); pass++) {
            Set<String> fresh = new LinkedHashSet<>(affectedClasses);
            fresh.removeAll(propagated);
            propagated.addAll(affectedClasses);
            for (Map.Entry<SourceKind, SourceCompiler> entry : compilers.entrySet()) {
                SourceKind kind = entry.getKey();
                // a language compiled in this batch for its own changes is visited again: an unchanged source of
                // it that references what another language changed was not selected the first time
                if (joint.containsKey(kind) || !embedded(target, kind)) {
                    continue;
                }
                CompilationRequest request = new CompilationRequest(kind, compilationRoots(target, kind, joint), Set.of(), Set.of(), false, target.compileClasspath(),
                    target.processorPath(), target.classOutput(kind), target.generatedSources(kind), compileOptions(target, kind, joint)).withAffectedClasses(fresh);
                CompilationResult result = entry.getValue().compile(request);
                if (!result.isSuccess()) {
                    return CompileRound.failed(new CompileFailure(kind, result.diagnostics(), Instant.now()));
                }
                if (result.status() == CompilationResult.Status.SUCCESS) {
                    compiledKinds.add(kind);
                    affectedClasses.addAll(result.compiledClasses());
                    LOG.info("Compiled {} {} source(s) that depend on the changed classes in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
                }
            }
        }
        return new CompileRound(null, compiled, compiledKinds, affectedClasses);
    }

    private void handle(Pending next) {
        TestSession session = tests;
        if (session != null) {
            session.handle(next.sources, next.testSources, next.resources, next.full, next.requested);
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
            return;
        }
        boolean compiled = round.compiled();
        Set<SourceKind> compiledKinds = round.compiledKinds();
        CompileFailure failure = lastFailure;
        if (failure != null && compiledKinds.contains(failure.kind())) {
            // the broken language compiles again; a batch that did not touch it leaves the failure shown
            lastFailure = null;
        }
        // resources that are not configuration reach the running context's watches; configuration is refreshed
        boolean configurationChanged = false;
        ApplicationContext current = context;
        for (Map.Entry<ResourceKind, SourceChanges> entry : batch.resources.entrySet()) {
            if (entry.getKey() == ResourceKind.CONFIG) {
                configurationChanged = true;
            } else {
                if (current instanceof DefaultBeanContext defaultBeanContext && current.isRunning()) {
                    defaultBeanContext.notifyResourceChange(new ResourceChange(entry.getKey(), rootsOf(entry.getKey()),
                        new ArrayList<>(entry.getValue().changed()), new ArrayList<>(entry.getValue().deleted()), false));
                }
                if (entry.getKey() == ResourceKind.STATIC || entry.getKey() == ResourceKind.VIEWS) {
                    refreshBrowsers(entry.getValue());
                }
            }
        }
        OutputSnapshot previous = snapshot;
        OutputSnapshot latest = OutputSnapshot.of(manifest.reloadableRoots());
        ChangeSet changeSet = previous.diff(latest);
        snapshot = latest;
        ConfigurationChange configurationChange = null;
        if (configurationChanged) {
            // the running context reads the file again and applies what it can: the configuration beans are
            // rebound, the refreshable beans disposed of, the watches told; a watch that cannot apply its change
            // answers REQUIRES_RESTART, and a bean retained across restarts that watched a touched prefix is not
            // kept when the restart comes
            RefreshResult refresh = refreshConfiguration(current);
            if (refresh == null) {
                configurationChange = ConfigurationChange.ofAll();
            } else {
                configurationChange = refresh.change();
                String stale = staleAfterRefresh(current, refresh.change());
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
        if (configurationChange == null && !batch.forcesRestart() && !startFailed && patchInPlace(changeSet, previous, start)) {
            return;
        }
        if (configurationChange == null && !batch.forcesRestart() && !startFailed && redefine(changeSet, start)) {
            return;
        }
        restart(changeSet, configurationChange, start);
    }

    /**
     * What a refresh cannot update: a singleton that received a changed property through {@code @Value}
     * or {@code @Property} rather than a configuration bean, and a definition whose {@code @Requires} names a
     * changed property, whose presence the change may have flipped. Either one makes the batch restart.
     *
     * @return Why a restart is needed, or null when the refresh covered the change
     */
    @Nullable
    private static String staleAfterRefresh(@Nullable ApplicationContext current, ConfigurationChange change) {
        if (current == null || change.all()) {
            return current == null ? null : "every property may have changed";
        }
        // the singletons, and through the graph what they hold: a prototype a singleton received and keeps
        // is as stale as the singleton would be
        Set<BeanDefinition<?>> definitions = new LinkedHashSet<>();
        Optional<io.micronaut.context.BeanDependencyGraph> graph = current instanceof ConfigurableBeanContext configurable ? configurable.findDependencyGraph() : Optional.empty();
        for (BeanRegistration<?> registration : current.getActiveBeanRegistrations(io.micronaut.inject.qualifiers.Qualifiers.any())) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            definitions.add(definition);
            graph.ifPresent(g -> definitions.addAll(g.transitiveDependenciesOf(definition)));
        }
        for (BeanDefinition<?> definition : definitions) {
            if (definition.isConfigurationProperties()) {
                continue;
            }
            if (injectsChangedProperty(definition, change)) {
                return definition.getBeanType().getName() + " injects a changed property directly";
            }
        }
        for (io.micronaut.inject.BeanDefinitionReference<?> reference : current.getBeanDefinitionReferences()) {
            for (io.micronaut.core.annotation.AnnotationValue<io.micronaut.context.annotation.Requires> requires : reference.getAnnotationMetadata().getAnnotationValuesByType(io.micronaut.context.annotation.Requires.class)) {
                String property = requires.stringValue("property").orElse(null);
                if (property != null && change.touches(property)) {
                    return reference.getBeanDefinitionName() + " requires a changed property";
                }
            }
        }
        return null;
    }

    private static boolean injectsChangedProperty(BeanDefinition<?> definition, ConfigurationChange change) {
        for (io.micronaut.core.type.Argument<?> argument : definition.getConstructor().getArguments()) {
            if (mentionsChangedProperty(argument.getAnnotationMetadata(), change)) {
                return true;
            }
        }
        for (io.micronaut.inject.FieldInjectionPoint<?, ?> field : definition.getInjectedFields()) {
            if (mentionsChangedProperty(field.getAnnotationMetadata(), change)) {
                return true;
            }
        }
        for (io.micronaut.inject.MethodInjectionPoint<?, ?> method : definition.getInjectedMethods()) {
            for (io.micronaut.core.type.Argument<?> argument : method.getArguments()) {
                if (mentionsChangedProperty(argument.getAnnotationMetadata(), change)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether an injection point names a changed property: {@code @Property(name = "key")}, or a
     * {@code @Value} expression with a {@code ${key}} or {@code ${key:default}} placeholder.
     */
    private static boolean mentionsChangedProperty(io.micronaut.core.annotation.AnnotationMetadata metadata, ConfigurationChange change) {
        // the raw values: a string value read through the metadata has its placeholders resolved already
        Object property = metadata.getValues(io.micronaut.context.annotation.Property.class.getName()).get("name");
        if (property != null && change.touches(property.toString())) {
            return true;
        }
        Object raw = metadata.getValues(io.micronaut.context.annotation.Value.class.getName()).get("value");
        if (raw == null) {
            return false;
        }
        String expression = raw.toString();
        if (expression.contains("#{") && expression.contains("env")) {
            // an evaluated expression reading the environment: which keys it reads is not known, so any change counts
            return true;
        }
        int start = expression.indexOf("${");
        while (start >= 0) {
            int end = expression.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String placeholder = expression.substring(start + 2, end);
            int colon = placeholder.indexOf(':');
            String key = (colon >= 0 ? placeholder.substring(0, colon) : placeholder).trim();
            if (!key.isEmpty() && change.touches(key)) {
                return true;
            }
            start = expression.indexOf("${", end);
        }
        return false;
    }

    /**
     * Refreshes the configuration of the running context.
     *
     * @return The result, or null when the context is not running or has no refresher, which restarts instead
     */
    @Nullable
    private static RefreshResult refreshConfiguration(@Nullable ApplicationContext current) {
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
     * The fast path: when every changed class kept its structure and no generated class changed, the
     * JVM redefines the loaded ones in place and the generation's snapshot gets the new bytes for the
     * rest, so that the running beans see the new bodies and nothing restarts.
     *
     * @return Whether the change was applied this way
     */
    private boolean redefine(ChangeSet changeSet, long startNanos) {
        Instrumentation agent = instrumentation;
        if (agent == null || strategy() == ReloadStrategy.RESTART || changeSet.classes().isEmpty()
            || !changeSet.changedResources().isEmpty() || !changeSet.removedResources().isEmpty()) {
            return false;
        }
        GenerationClassLoader generation = classLoader.current();
        List<ClassDefinition> definitions = new ArrayList<>();
        Map<String, byte[]> replacements = new LinkedHashMap<>();
        for (ClassChange change : changeSet.classes()) {
            String name = change.className();
            String simpleName = name.substring(name.lastIndexOf('.') + 1);
            if (change.kind() != ClassChange.Kind.MODIFIED || simpleName.startsWith("$")) {
                // a class added or removed, or a generated one that changed: the definitions changed shape
                return false;
            }
            byte[] before = classFile(generation.roots(), name);
            byte[] after = classFile(manifest.reloadableRoots(), name);
            if (before == null || after == null || !ClassStructure.bodyOnlyChange(before, after)) {
                return false;
            }
            replacements.put(name, after);
        }
        ApplicationContext current = context;
        try {
            // the snapshot first: a class a thread loads from now on is the new version, and one loaded before is
            // found below and redefined; nothing can load the old version in between
            for (Map.Entry<String, byte[]> entry : replacements.entrySet()) {
                generation.replaceClassFile(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, byte[]> entry : replacements.entrySet()) {
                Class<?> loaded = generation.loadedClass(entry.getKey());
                if (loaded != null) {
                    definitions.add(new ClassDefinition(loaded, entry.getValue()));
                }
            }
            if (!definitions.isEmpty()) {
                agent.redefineClasses(definitions.toArray(new ClassDefinition[0]));
            }
        } catch (Exception | LinkageError e) {
            // the snapshot may already hold the new bytes: the restart that follows builds a new generation anyway
            LOG.info("Cannot redefine {} class(es) in place ({}): restarting instead", replacements.size(), e.getMessage());
            return false;
        }
        redefinitions++;
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        if (current != null && current.isRunning()) {
            ClassChangeEvent event = new ClassChangeEvent(this, Set.of(), generation, changeSet.classes(), ReloadStrategy.RELOAD);
            try {
                current.publishEvent(event);
                current.publishEvent(new ReloadCompletedEvent(this, event, List.of(), List.of(), elapsed));
            } catch (RuntimeException e) {
                LOG.warn("A listener of the class change failed: {}", e.getMessage(), e);
            }
        }
        LOG.info("Redefined {} class(es) in place in {} ms: method bodies only, generation {} keeps running", replacements.size(), elapsed.toMillis(), generation.generation());
        LiveReloadServer server = liveReload;
        if (server != null) {
            server.reload("/", false);
        }
        return true;
    }

    /**
     * Tier one: a change of resources alone, none added or removed and no class changed, is offered to the running
     * context's {@link InPlaceResourceReloader}s; the first that takes the whole change gets the new contents in the
     * generation's snapshot, then applies them to the running application. A Python module whose generated classes
     * stayed byte for byte the same is such a change. Anything a reloader refuses, by answering no or by throwing,
     * restarts as before, and the restart's new generation discards a half-applied patch.
     *
     * @return Whether the change was applied this way
     */
    private boolean patchInPlace(ChangeSet changeSet, OutputSnapshot previous, long startNanos) {
        if (!isPatchable(changeSet, previous.resourcePaths())) {
            return false;
        }
        ApplicationContext current = context;
        if (current == null || !current.isRunning()) {
            return false;
        }
        GenerationClassLoader generation = classLoader.current();
        List<InPlaceResourceReloader> reloaders;
        try {
            reloaders = new ArrayList<>(current.getBeansOfType(InPlaceResourceReloader.class));
        } catch (RuntimeException e) {
            LOG.debug("Cannot look up the in-place reloaders: restarting instead", e);
            return false;
        }
        InPlaceResourceReloader.Result result = patchGeneration(changeSet, reloaders, "restarting instead");
        if (result == null) {
            return false;
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        ClassChangeEvent event = new ClassChangeEvent(this, Set.of(), generation, List.of(), ReloadStrategy.RELOAD);
        try {
            current.publishEvent(event);
            current.publishEvent(new ReloadCompletedEvent(this, event, List.of(), List.of(), elapsed));
        } catch (RuntimeException e) {
            LOG.warn("A listener of the class change failed: {}", e.getMessage(), e);
        }
        LOG.info("Patched {} {} in place in {} ms: generation {} keeps running", result.count(), result.unit(), elapsed.toMillis(), generation.generation());
        LiveReloadServer server = liveReload;
        if (server != null) {
            server.reload("/", false);
        }
        return true;
    }

    /**
     * Whether a change of the class output is one an {@link InPlaceResourceReloader} may take: resources changed,
     * none added or removed, and no class changed. {@link DevManifest#patchInPlace()} turns this off.
     *
     * @param changeSet The change
     * @param before The resources the output held before it
     * @return True if the change may be patched in place
     */
    boolean isPatchable(ChangeSet changeSet, Set<String> before) {
        Set<String> changed = changeSet.changedResources();
        // nor a resource added: a reloader reads an index of the resources it knows, a Python file system its file list
        return manifest.patchInPlace() && !changeSet.hasClassChanges() && !changed.isEmpty() && changeSet.removedResources().isEmpty()
            && before.containsAll(changed);
    }

    /**
     * Patches a {@link #isPatchable patchable} change into the current generation: the first of the reloaders, in
     * order, that takes the whole change gets the new contents in the generation's snapshot, then applies them. A
     * refusal, by answering no or by throwing, leaves it to the caller to start a new generation, which discards a
     * half-applied patch.
     *
     * @param changeSet The change
     * @param reloaders The reloaders that may take it
     * @param otherwise What the caller does when the change is not patched, for the log
     * @return What was patched, or null when nothing was
     */
    InPlaceResourceReloader.@Nullable Result patchGeneration(ChangeSet changeSet, List<InPlaceResourceReloader> reloaders, String otherwise) {
        Set<String> changed = changeSet.changedResources();
        GenerationClassLoader generation = classLoader.current();
        InPlaceResourceReloader reloader = null;
        try {
            List<InPlaceResourceReloader> sorted = new ArrayList<>(reloaders);
            OrderUtil.sort(sorted);
            for (InPlaceResourceReloader candidate : sorted) {
                if (candidate.canReload(changed, Set.of())) {
                    reloader = candidate;
                    break;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("An in-place reloader failed to answer: {}", otherwise, e);
            return null;
        }
        if (reloader == null) {
            return null;
        }
        InPlaceResourceReloader.Result result;
        try {
            // the snapshot first: the reloader reads the new contents through the generation's loader
            for (String resource : changed) {
                byte[] contents = resourceFile(manifest.reloadableRoots(), resource);
                if (contents == null || !generation.replaceResource(resource, contents)) {
                    LOG.info("Cannot patch {} in place, the generation does not hold it: {}", resource, otherwise);
                    return null;
                }
            }
            result = reloader.reload(changed);
        } catch (Exception | LinkageError e) {
            LOG.info("Cannot patch {} resource(s) in place ({}): {}", changed.size(), e.getMessage(), otherwise);
            LOG.debug("The in-place patch failed", e);
            return null;
        }
        inPlacePatches++;
        return result;
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

    private static byte @Nullable [] classFile(List<Path> roots, String className) {
        return resourceFile(roots, className.replace('.', '/') + ".class");
    }

    private static byte @Nullable [] resourceFile(List<Path> roots, String relative) {
        for (Path root : roots) {
            Path file = root.resolve(relative);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readAllBytes(file);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void restart(ChangeSet changeSet, @Nullable ConfigurationChange configurationChange, long startNanos) {
        if (isGenerationBudgetSpent()) {
            // the compiled classes stay in the class output: the relaunched process starts from them
            requestRelaunch();
            return;
        }
        ApplicationContext old = context;
        classLoader.swap();
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
        }
        retainedForNext = retained;
        started = new CompletableFuture<>();
        ApplicationContext fresh;
        try {
            fresh = launch("reload");
        } catch (RuntimeException e) {
            startFailed = true;
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
        startFailed = false;
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        List<BeanDefinition<?>> added = definitionsNamed(fresh, changeSet.classNames());
        fresh.publishEvent(new ReloadCompletedEvent(this, new ClassChangeEvent(this, classLoader.retiredLoaders(), classLoader.current(), changeSet.classes(), ReloadStrategy.RESTART), added, List.of(), elapsed));
        LOG.info("Reloaded: generation {} started in {} ms ({} class(es) changed, {} bean(s) retained)", classLoader.generation(), elapsed.toMillis(), changeSet.classes().size(), retainedCount);
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
     * What a restart retains: what a policy retains, unless a change touched a prefix one of them declares for it, or a
     * bean it holds or received is of a class of a generation, which the restart replaces and the bean would keep
     * running. The prefixes the policies declare for a bean are also those under which a configuration bean it received
     * is not retained with it, but bound again by the next generation.
     */
    private DefaultBeanContext.RetentionCriteria retentionCriteria(ApplicationContext old, @Nullable ConfigurationChange configurationChange) {
        List<BeanRetentionPolicy> policies = new ArrayList<>(old.getBeansOfType(BeanRetentionPolicy.class));
        if (manifest.retainAnnotated()) {
            // what the modules declare with @Retain, read from the definitions
            policies.add(AnnotatedBeanRetentionPolicy.INSTANCE);
        }
        OrderUtil.sort(policies);
        return new DefaultBeanContext.RetentionCriteria() {
            @Override
            public boolean retain(BeanRegistration<?> registration) {
                if (isStale(registration)) {
                    return false;
                }
                // every policy that retains the bean has a say: a pool kept across a changed URL would be the old pool,
                // so a touched prefix any of them declares for the bean drops it, and none declaring one keeps it
                boolean retained = false;
                for (BeanRetentionPolicy policy : policies) {
                    if (!policy.retain(registration)) {
                        continue;
                    }
                    retained = true;
                    if (configurationChange != null) {
                        for (String prefix : policy.observedConfigurationPrefixes(registration)) {
                            if (configurationChange.touches(prefix)) {
                                return false;
                            }
                        }
                    }
                }
                return retained;
            }

            @Override
            public Set<String> invalidatedBy(BeanRegistration<?> registration) {
                Set<String> prefixes = new LinkedHashSet<>();
                for (BeanRetentionPolicy policy : policies) {
                    if (policy.retain(registration)) {
                        prefixes.addAll(policy.observedConfigurationPrefixes(registration));
                    }
                }
                return prefixes;
            }

            @Override
            public boolean isReplaced(Class<?> type) {
                return type.getClassLoader() instanceof GenerationClassLoader;
            }
        };
    }

    private static boolean isStale(BeanRegistration<?> registration) {
        if (registration.getBeanDefinition().getClass().getClassLoader() instanceof GenerationClassLoader) {
            return true;
        }
        Object bean = registration.getBean();
        return bean != null && bean.getClass().getClassLoader() instanceof GenerationClassLoader;
    }

    private ApplicationContext launch(String reason) {
        String[] args = arguments.clone();
        CompletableFuture<ApplicationContext> future = started;
        generationStartedNanos = System.nanoTime();
        // the generation loader, not the facade: a class the JVM resolved through the facade once would be
        // handed out again, from the retired generation, for as long as the facade lives
        GenerationClassLoader generation = classLoader.current();
        Thread thread = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(generation);
            try {
                launcher.launch(generation, manifest.mainClass(), args);
                if (!future.isDone()) {
                    // main returned without a context starting: a startup failure the application logged itself
                    future.completeExceptionally(new IllegalStateException("The application's main returned without starting a context"));
                }
            } catch (Throwable e) {
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
        if (NativeImageUtils.inImageRuntimeCode()) {
            // a native image never unloads a class it defined at runtime: every retired generation stays, as the budget expects
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            System.gc();
            int current = classLoader.generation();
            List<GenerationClassLoader> live = classLoader.liveRetiredGenerations();
            List<Integer> old = live.stream().map(GenerationClassLoader::generation).filter(generation -> generation < current - LEAK_TOLERANCE).toList();
            if (!old.isEmpty()) {
                LOG.warn("{} retired generation(s) {} are still reachable after the reload: a static cache or a thread of the application keeps old classes alive", old.size(), old);
            }
        }, "micronaut-dev-leak-detector");
        thread.setDaemon(true);
        thread.start();
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
        void launch(ClassLoader classLoader, String mainClass, String[] args) throws Exception;
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

    /**
     * The changed and deleted files of one language or resource kind.
     *
     * @param changed The files added or modified
     * @param deleted The files deleted
     */
    record SourceChanges(Set<Path> changed, Set<Path> deleted) {
        static final SourceChanges NONE = new SourceChanges(Set.of(), Set.of());

        SourceChanges merge(SourceChanges other) {
            Set<Path> allChanged = new LinkedHashSet<>(changed);
            allChanged.addAll(other.changed);
            Set<Path> allDeleted = new LinkedHashSet<>(deleted);
            allDeleted.addAll(other.deleted);
            // the later batch wins: a file deleted then written again is a change, a file written then deleted is gone
            allChanged.removeAll(other.deleted);
            allDeleted.removeAll(other.changed);
            return new SourceChanges(allChanged, allDeleted);
        }
    }

    /**
     * A batch waiting for the reload thread.
     */
    private static class Pending {
        final Map<SourceKind, SourceChanges> sources;
        final Map<SourceKind, SourceChanges> testSources;
        final Map<ResourceKind, SourceChanges> resources;
        final boolean full;
        final @Nullable TestRequest requested;
        long sequence;

        Pending(Map<SourceKind, SourceChanges> sources, Map<ResourceKind, SourceChanges> resources, boolean full) {
            this(sources, Map.of(), resources, full, null);
        }

        Pending(Map<SourceKind, SourceChanges> sources, Map<SourceKind, SourceChanges> testSources, Map<ResourceKind, SourceChanges> resources,
                boolean full, @Nullable TestRequest requested) {
            this.sources = sources;
            this.testSources = testSources;
            this.resources = resources;
            this.full = full;
            this.requested = requested;
        }

        boolean forcesRestart() {
            return false;
        }

        boolean isEmpty() {
            return !full && !forcesRestart() && requested == null
                && sources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty())
                && testSources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty())
                && resources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty());
        }

        /**
         * How many tests a request runs, for merging two of them: the widest wins, every test, then the last run's
         * again, which holds the failures, then the failures alone.
         */
        private static int breadth(TestRequest request) {
            return switch (request) {
                case ALL -> 2;
                case RERUN -> 1;
                case FAILED -> 0;
            };
        }

        static Pending merge(List<Pending> batches) {
            Map<SourceKind, SourceChanges> sources = new EnumMap<>(SourceKind.class);
            Map<SourceKind, SourceChanges> testSources = new EnumMap<>(SourceKind.class);
            Map<ResourceKind, SourceChanges> resources = new EnumMap<>(ResourceKind.class);
            boolean full = false;
            boolean restart = false;
            TestRequest requested = null;
            for (Pending batch : batches) {
                batch.sources.forEach((kind, changes) -> sources.merge(kind, changes, SourceChanges::merge));
                batch.testSources.forEach((kind, changes) -> testSources.merge(kind, changes, SourceChanges::merge));
                batch.resources.forEach((kind, changes) -> resources.merge(kind, changes, SourceChanges::merge));
                full |= batch.full;
                restart |= batch.forcesRestart();
                if (batch.requested != null && (requested == null || breadth(batch.requested) > breadth(requested))) {
                    requested = batch.requested;
                }
            }
            boolean forced = restart;
            return new Pending(sources, testSources, resources, full, requested) {
                @Override
                boolean forcesRestart() {
                    return forced;
                }
            };
        }
    }
}
