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
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.context.reload.ReloadCompletedEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.change.ClassStructure;
import io.micronaut.dev.change.OutputSnapshot;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevReloadStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The changes a {@link DevRuntime} applies to the running generation rather than restarting it: method bodies the JVM
 * redefines in place, and resources an {@link InPlaceResourceReloader} patches in place. Either one that does not apply
 * leaves the change to a restart.
 */
@Internal
final class InPlaceUpdates {

    // the runtime's logger: these are the runtime's reloads
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);

    private final DevRuntime runtime;
    private final AtomicInteger redefinitions = new AtomicInteger();
    private final AtomicInteger inPlacePatches = new AtomicInteger();

    /**
     * @param runtime The runtime whose generations are updated
     */
    InPlaceUpdates(DevRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Returns how many changes the JVM redefined in place.
     *
     * @return The count
     */
    int redefinitions() {
        return redefinitions.get();
    }

    /**
     * Returns how many changes were patched in place.
     *
     * @return The count
     */
    int inPlacePatches() {
        return inPlacePatches.get();
    }

    /**
     * The fast path: when every changed class kept its structure and no generated class changed, the
     * JVM redefines the loaded ones in place and the generation's snapshot gets the new bytes for the
     * rest, so that the running beans see the new bodies and nothing restarts.
     *
     * @param agent The instrumentation, if attached
     * @param current The running context, if any
     * @param changeSet The change
     * @param startNanos When the batch started
     * @return Whether the change was applied this way
     */
    boolean redefine(@Nullable Instrumentation agent, @Nullable ApplicationContext current, ChangeSet changeSet, long startNanos) {
        if (agent == null || runtime.strategy() == DevReloadStrategy.RESTART || changeSet.classes().isEmpty()
            || !changeSet.changedResources().isEmpty() || !changeSet.removedResources().isEmpty()) {
            return false;
        }
        DevManifest manifest = runtime.manifest();
        GenerationClassLoader generation = runtime.classLoader().current();
        List<ClassDefinition> definitions = new ArrayList<>();
        Map<String, byte[]> originals = new LinkedHashMap<>();
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
            originals.put(name, before);
            replacements.put(name, after);
        }
        ClassFileReplacement snapshotChange = new ClassFileReplacement(generation);
        try {
            // the snapshot first, with class loading through the generation held until the change is applied or rolled
            // back: a class loaded before is found below and redefined, and one loaded after reads the files as left
            for (Map.Entry<String, byte[]> entry : replacements.entrySet()) {
                snapshotChange.replace(entry.getKey(), Objects.requireNonNull(originals.get(entry.getKey())), entry.getValue());
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
            // the snapshot gets back the class files it held; the restart that follows applies the whole change
            snapshotChange.rollBack();
            LOG.info("Cannot redefine {} class(es) in place: restarting instead", replacements.size(), e);
            return false;
        } finally {
            snapshotChange.close();
        }
        redefinitions.incrementAndGet();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        if (current != null && current.isRunning()) {
            ClassChangeEvent event = new ClassChangeEvent(runtime, Set.of(), generation, changeSet.classes(), ReloadStrategy.RELOAD);
            try {
                current.publishEvent(event);
                current.publishEvent(new ReloadCompletedEvent(runtime, event, List.of(), List.of(), elapsed));
            } catch (RuntimeException e) {
                LOG.warn("A listener of the class change failed: {}", e.getMessage(), e);
            }
        }
        LOG.info("Redefined {} class(es) in place in {} ms: method bodies only, generation {} keeps running", replacements.size(), elapsed.toMillis(), generation.generation());
        runtime.liveReload().ifPresent(server -> server.reload("/", false));
        return true;
    }

    /**
     * Tier one: a change of resources alone, none added or removed and no class changed, is offered to the running
     * context's {@link InPlaceResourceReloader}s; the first that takes the whole change gets the new contents in the
     * generation's snapshot, then applies them to the running application. A Python module whose generated classes
     * stayed byte for byte the same is such a change. Anything a reloader refuses, by answering no or by throwing,
     * restarts as before, and the restart's new generation discards a half-applied patch.
     *
     * @param current The running context, if any
     * @param changeSet The change
     * @param previous The output as it was before the change
     * @param startNanos When the batch started
     * @return Whether the change was applied this way
     */
    boolean patchInPlace(@Nullable ApplicationContext current, ChangeSet changeSet, OutputSnapshot previous, long startNanos) {
        if (!isPatchable(changeSet, previous.resourcePaths())) {
            return false;
        }
        if (current == null || !current.isRunning()) {
            return false;
        }
        GenerationClassLoader generation = runtime.classLoader().current();
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
        ClassChangeEvent event = new ClassChangeEvent(runtime, Set.of(), generation, List.of(), ReloadStrategy.RELOAD);
        try {
            current.publishEvent(event);
            current.publishEvent(new ReloadCompletedEvent(runtime, event, List.of(), List.of(), elapsed));
        } catch (RuntimeException e) {
            LOG.warn("A listener of the class change failed: {}", e.getMessage(), e);
        }
        LOG.info("Patched {} {} in place in {} ms: generation {} keeps running", result.count(), result.unit(), elapsed.toMillis(), generation.generation());
        runtime.liveReload().ifPresent(server -> server.reload("/", false));
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
        return runtime.manifest().patchInPlace() && !changeSet.hasClassChanges() && !changed.isEmpty() && changeSet.removedResources().isEmpty()
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
        GenerationClassLoader generation = runtime.classLoader().current();
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
                byte[] contents = resourceFile(runtime.manifest().reloadableRoots(), resource);
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
        inPlacePatches.incrementAndGet();
        return result;
    }

    /**
     * Reads a class file from the first of the roots that holds it.
     *
     * @param roots The roots to look in, in order
     * @param className The class
     * @return The class file of the first root that holds it, or null
     */
    static byte @Nullable [] classFile(List<Path> roots, String className) {
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
}
