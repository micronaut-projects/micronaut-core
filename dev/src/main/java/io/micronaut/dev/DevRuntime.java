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
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadCompletedEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.change.OutputSnapshot;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.CompileMode;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.ResourceRoot;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileChange;
import io.micronaut.scheduling.io.watch.FileChangeBatch;
import io.micronaut.scheduling.io.watch.WatchOptions;
import io.micronaut.scheduling.io.watch.event.WatchEventType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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
 * resources only reaches the resource watches of the running context. A configuration file
 * change restarts as well, retaining nothing, until the configuration refresh lands. A failed
 * compilation leaves the running generation as it is and is reported until the next success.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
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

    private final DevManifest manifest;
    private final DevClassLoader classLoader;
    private final ApplicationLauncher launcher;
    private final Map<SourceKind, SourceCompiler> compilers;
    private final Map<Path, ResourceKind> resourceRootKinds = new LinkedHashMap<>();
    private final LinkedBlockingQueue<Pending> pending = new LinkedBlockingQueue<>();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final Object lifecycle = new Object();
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
     * Whether the last generation failed to start, so that the next batch must launch one whether or not a class changed.
     */
    private volatile boolean startFailed;
    private @Nullable DirectoryWatcher watcher;
    private @Nullable Thread worker;
    private OutputSnapshot snapshot = OutputSnapshot.empty();

    /**
     * Creates the runtime; {@link #start(String[])} runs it.
     *
     * @param manifest The manifest
     * @param classLoader The reloadable loader, over the manifest's roots
     * @param launcher How the application's main is run
     * @param compilers The compilers, by language
     */
    DevRuntime(DevManifest manifest, DevClassLoader classLoader, ApplicationLauncher launcher, Map<SourceKind, SourceCompiler> compilers) {
        listIntrospectionsOfTheReloadableTier();
        this.manifest = manifest;
        this.classLoader = classLoader;
        this.launcher = launcher;
        this.compilers = new EnumMap<>(SourceKind.class);
        this.compilers.putAll(compilers);
        for (ResourceRoot root : manifest.resourceRoots()) {
            resourceRootKinds.put(root.path().toAbsolutePath().normalize(), root.kind());
        }
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
        if (!CURRENT.compareAndSet(null, this)) {
            throw new IllegalStateException("A development runtime already runs this process");
        }
        // only the application thread needs the generation loader; the caller's thread keeps its own
        arguments = args.clone();
        ApplicationContext first;
        try {
            snapshot = OutputSnapshot.of(manifest.reloadableRoots());
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
     * @return The strategy applied: RESTART, the only one implemented, whatever the manifest asks for
     */
    public ReloadStrategy strategy() {
        return ReloadStrategy.RESTART;
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

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
        }
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        DirectoryWatcher directoryWatcher = watcher;
        if (directoryWatcher != null) {
            directoryWatcher.close();
        }
        ApplicationContext current = context;
        if (current != null && current.isRunning()) {
            current.stop();
        }
        for (SourceCompiler compiler : new LinkedHashSet<>(compilers.values())) {
            compiler.close();
        }
        CURRENT.compareAndSet(this, null);
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
        // decided before anything is compiled: two languages sharing one output are both missing, or neither
        Set<SourceKind> missing = new LinkedHashSet<>();
        for (SourceKind kind : compilers.keySet()) {
            if (!manifest.sourceRoots(kind).isEmpty() && manifest.compileMode(kind) != CompileMode.BUILD_TOOL && !Files.isDirectory(manifest.classOutput(kind))) {
                missing.add(kind);
            }
        }
        for (Map.Entry<SourceKind, SourceCompiler> entry : compilers.entrySet()) {
            SourceKind kind = entry.getKey();
            if (!missing.contains(kind)) {
                continue;
            }
            CompilationRequest request = new CompilationRequest(kind, manifest.sourceRoots(kind), Set.of(), Set.of(), true, manifest.compileClasspath(),
                manifest.processorPath(), manifest.classOutput(kind), manifest.generatedSources(kind), manifest.compileOptions(kind)).asFull();
            CompilationResult result = entry.getValue().compile(request);
            if (!result.isSuccess()) {
                CompileFailure failure = new CompileFailure(kind, result.diagnostics(), Instant.now());
                throw new IllegalStateException(failure.describe());
            }
            LOG.info("Compiled {} {} source(s) in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
        }
    }

    private void startWatching() {
        DirectoryWatcher directoryWatcher;
        try {
            directoryWatcher = DirectoryWatcher.builder(FileSystems.getDefault().newWatchService())
                .threadName("micronaut-dev-watcher")
                .build()
                .start();
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
            directoryWatcher.watch(root.path(), WatchOptions.DEFAULT.including(globs), batch -> enqueue(sourceBatch(root, batch)));
        }
        for (ResourceRoot root : manifest.resourceRoots()) {
            if (Files.isDirectory(root.path())) {
                directoryWatcher.watch(root.path(), WatchOptions.DEFAULT, batch -> enqueue(resourceBatch(root, batch)));
            }
        }
        boolean external = false;
        for (SourceKind kind : SourceKind.values()) {
            external |= !manifest.sourceRoots(kind).isEmpty()
                && (manifest.compileMode(kind) == CompileMode.BUILD_TOOL || !compilers.containsKey(kind));
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
                    directoryWatcher.watch(directory, WatchOptions.nonRecursive().including(name), batch -> enqueue(new Pending(Map.of(), Map.of(), false)));
                }
            } else {
                // without a trigger the class output itself is watched, which may see a compilation half written
                LOG.warn("No micronaut.dev.build-tool.trigger configured: the class output is watched directly and a restart may see a compilation in progress");
                for (SourceKind kind : SourceKind.values()) {
                    Path output = manifest.classOutput(kind);
                    if (!manifest.sourceRoots(kind).isEmpty() && Files.isDirectory(output) && !directoryWatcher.isWatching(output)) {
                        directoryWatcher.watch(output, WatchOptions.DEFAULT, batch -> enqueue(new Pending(Map.of(), Map.of(), false)));
                    }
                }
            }
        }
        watcher = directoryWatcher;
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

    private void handle(Pending batch) {
        long start = System.nanoTime();
        // compile what changed, or everything on the manual trigger; a failure leaves the generation as it is
        Set<SourceKind> kinds = batch.full ? compilers.keySet() : batch.sources.keySet();
        boolean compiled = false;
        Set<SourceKind> compiledKinds = new LinkedHashSet<>();
        for (SourceKind kind : kinds) {
            SourceCompiler compiler = compilers.get(kind);
            if (compiler == null || manifest.compileMode(kind) == CompileMode.BUILD_TOOL || manifest.sourceRoots(kind).isEmpty()) {
                continue;
            }
            SourceChanges changes = batch.sources.getOrDefault(kind, SourceChanges.NONE);
            CompilationRequest request = new CompilationRequest(kind, manifest.sourceRoots(kind), changes.changed(), changes.deleted(),
                batch.full || !manifest.isIncremental(), manifest.compileClasspath(), manifest.processorPath(),
                manifest.classOutput(kind), manifest.generatedSources(kind), manifest.compileOptions(kind));
            CompilationResult result = compiler.compile(request);
            if (!result.isSuccess()) {
                CompileFailure failure = new CompileFailure(kind, result.diagnostics(), Instant.now());
                lastFailure = failure;
                LOG.error("{}", failure.describe().strip());
                return;
            }
            compiled = true;
            compiledKinds.add(kind);
            LOG.info("Compiled {} {} source(s) in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
        }
        CompileFailure failure = lastFailure;
        if (failure != null && compiledKinds.contains(failure.kind())) {
            // the broken language compiles again; a batch that did not touch it leaves the failure shown
            lastFailure = null;
        }
        // resources that are not configuration reach the running context's watches; configuration restarts
        boolean configurationChanged = false;
        ApplicationContext current = context;
        for (Map.Entry<ResourceKind, SourceChanges> entry : batch.resources.entrySet()) {
            if (entry.getKey() == ResourceKind.CONFIG) {
                configurationChanged = true;
            } else if (current instanceof DefaultBeanContext defaultBeanContext && current.isRunning()) {
                defaultBeanContext.notifyResourceChange(new ResourceChange(entry.getKey(), rootsOf(entry.getKey()),
                    new ArrayList<>(entry.getValue().changed()), new ArrayList<>(entry.getValue().deleted()), false));
            }
        }
        OutputSnapshot latest = OutputSnapshot.of(manifest.reloadableRoots());
        ChangeSet changeSet = snapshot.diff(latest);
        snapshot = latest;
        // a generation reads its snapshot: a resource the build wrote under a reloadable root, such as a service
        // descriptor, needs a new generation as a class does; a failed start needs one whatever changed
        if (changeSet.isEmpty() && !configurationChanged && !batch.forcesRestart() && !startFailed) {
            if (compiled) {
                LOG.info("Nothing to reload: the classes did not change");
            }
            return;
        }
        restart(changeSet, !configurationChanged, start);
    }

    private void restart(ChangeSet changeSet, boolean retentionAllowed, long startNanos) {
        ApplicationContext old = context;
        GenerationClassLoader retired = classLoader.swap();
        Collection<BeanRegistration<?>> retained = List.of();
        if (old != null) {
            ClassChangeEvent event = new ClassChangeEvent(this, retired.generation(), classLoader.retiredLoaders(), classLoader.current(), changeSet.classes(), ReloadStrategy.RESTART);
            try {
                old.publishEvent(event);
            } catch (RuntimeException e) {
                LOG.warn("A listener of the class change failed: {}", e.getMessage(), e);
            }
            if (old instanceof DefaultBeanContext defaultBeanContext && old.isRunning()) {
                retained = defaultBeanContext.stopRetaining(retentionPredicate(old, retentionAllowed));
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
        fresh.publishEvent(new ReloadCompletedEvent(this, new ClassChangeEvent(this, retired.generation(), classLoader.retiredLoaders(), classLoader.current(), changeSet.classes(), ReloadStrategy.RESTART), added, List.of(), elapsed));
        LOG.info("Reloaded: generation {} started in {} ms ({} class(es) changed, {} bean(s) retained)", classLoader.generation(), elapsed.toMillis(), changeSet.classes().size(), retainedCount);
        detectLeaks();
    }

    private Predicate<BeanRegistration<?>> retentionPredicate(ApplicationContext old, boolean retentionAllowed) {
        List<BeanRetentionPolicy> policies = new ArrayList<>(old.getBeansOfType(BeanRetentionPolicy.class));
        OrderUtil.sort(policies);
        return registration -> {
            if (isStale(registration)) {
                return false;
            }
            if (!retentionAllowed) {
                // until the configuration refresh tells which prefixes changed, a configuration change drops
                // every retained bean: a pool kept across a changed URL would be the old pool
                return false;
            }
            for (BeanRetentionPolicy policy : policies) {
                if (policy.retain(registration)) {
                    return true;
                }
            }
            return false;
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

    private void detectLeaks() {
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
     * The changed and deleted files of one language or resource kind.
     *
     * @param changed The files added or modified
     * @param deleted The files deleted
     */
    private record SourceChanges(Set<Path> changed, Set<Path> deleted) {
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
        final Map<ResourceKind, SourceChanges> resources;
        final boolean full;
        long sequence;

        Pending(Map<SourceKind, SourceChanges> sources, Map<ResourceKind, SourceChanges> resources, boolean full) {
            this.sources = sources;
            this.resources = resources;
            this.full = full;
        }

        boolean forcesRestart() {
            return false;
        }

        boolean isEmpty() {
            return !full && !forcesRestart()
                && sources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty())
                && resources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty());
        }

        static Pending merge(List<Pending> batches) {
            Map<SourceKind, SourceChanges> sources = new EnumMap<>(SourceKind.class);
            Map<ResourceKind, SourceChanges> resources = new EnumMap<>(ResourceKind.class);
            boolean full = false;
            boolean restart = false;
            for (Pending batch : batches) {
                batch.sources.forEach((kind, changes) -> sources.merge(kind, changes, SourceChanges::merge));
                batch.resources.forEach((kind, changes) -> resources.merge(kind, changes, SourceChanges::merge));
                full |= batch.full;
                restart |= batch.forcesRestart();
            }
            boolean forced = restart;
            return new Pending(sources, resources, full) {
                @Override
                boolean forcesRestart() {
                    return forced;
                }
            };
        }
    }
}
