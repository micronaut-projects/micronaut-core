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
package io.micronaut.dev.loader;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * The application's classloader for the life of the development JVM: one stable identity that the
 * application context, the environment, the thread context loader and every cache keyed by loader
 * refer to, delegating to the current {@link GenerationClassLoader} and swapped to a new one when the
 * classes change.
 *
 * <p>Delegation is parent-first, as for any loader: the libraries in the parent are never shadowed by
 * the reloadable tier. The facade defines no class itself; a class's loader is its generation, so a
 * retired generation's classes are recognisable and the retired generation can be collected once
 * nothing refers to its classes any more.</p>
 *
 * <p>Swapping generations tells the process-wide caches keyed by loader identity to forget this
 * loader: the {@code META-INF/micronaut} service index and the shared bean introspector.</p>
 *
 * <p>The facade is the handle for swapping and for telling a stale class; it is not the loader to
 * give a context or a thread. The JVM records the loader {@code Class.forName} was called with as
 * the initiating loader of the class it found, and would answer with the retired generation's
 * class for as long as the facade lives. Each context loads through {@link #current()}, the
 * generation loader itself, which is also what the application thread has as its context loader.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class DevClassLoader extends ClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final List<Path> liveRoots;
    private final List<Path> sources;
    private final Path generationsDir;
    private volatile GenerationClassLoader current;
    private final List<WeakReference<GenerationClassLoader>> retired = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates the loader over its first generation.
     *
     * @param parent The parent loader, holding the libraries
     * @param roots The class and resource directories of the reloadable tier, which the build writes to
     * @param generationsDir The loader's own directory for the snapshot each generation loads from; emptied first
     */
    public DevClassLoader(@Nullable ClassLoader parent, List<Path> roots, Path generationsDir) {
        this(parent, List.of(), roots, generationsDir);
    }

    /**
     * Creates the loader over its first generation, with directories read live ahead of the snapshotted ones.
     *
     * @param parent The parent loader, holding the libraries
     * @param liveRoots The directories read as they are, searched first: the resource roots the developer edits
     * @param roots The class and resource directories of the reloadable tier, which the build writes to, snapshotted per generation
     * @param generationsDir The loader's own directory for the snapshots; emptied first
     */
    public DevClassLoader(@Nullable ClassLoader parent, List<Path> liveRoots, List<Path> roots, Path generationsDir) {
        super("micronaut-dev", parent);
        this.liveRoots = List.copyOf(liveRoots);
        this.sources = List.copyOf(roots);
        this.generationsDir = generationsDir;
        try {
            GenerationClassLoader.deleteRecursively(generationsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot empty the generations directory " + generationsDir, e);
        }
        this.current = snapshot(1, this.sources);
    }

    private GenerationClassLoader snapshot(int generation, List<Path> roots) {
        return GenerationClassLoader.snapshot(generation, liveRoots, roots, generationsDir.resolve(String.valueOf(generation)), getParent());
    }

    /**
     * @return The directories of the reloadable tier, which the build writes to
     */
    public List<Path> sources() {
        return sources;
    }

    /**
     * @return The directories every generation reads live, ahead of its snapshot
     */
    public List<Path> liveRoots() {
        return liveRoots;
    }

    /**
     * @return The current generation
     */
    public GenerationClassLoader current() {
        return current;
    }

    /**
     * @return The number of the current generation, counted from one
     */
    public int generation() {
        return current.generation();
    }

    /**
     * Replaces the current generation with a new one over a fresh snapshot of the same directories.
     *
     * @return The retired generation
     */
    public GenerationClassLoader swap() {
        return swap(sources);
    }

    /**
     * Replaces the current generation with a new one over a snapshot of the given directories.
     *
     * @param roots The class and resource directories of the new generation, which the build writes to
     * @return The retired generation
     */
    public synchronized GenerationClassLoader swap(List<Path> roots) {
        GenerationClassLoader previous = current;
        current = snapshot(previous.generation() + 1, roots);
        retired.add(new WeakReference<>(previous));
        MicronautMetaServiceLoaderUtils.invalidate(this);
        MicronautMetaServiceLoaderUtils.invalidate(previous);
        BeanIntrospector.SHARED.invalidate();
        return previous;
    }

    /**
     * Whether a class was defined by a generation this loader retired.
     *
     * @param type The class
     * @return True if the class belongs to a retired generation
     */
    public boolean isStale(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        ClassLoader loader = type.getClassLoader();
        return loader instanceof GenerationClassLoader generation && generation != current && isRetired(generation);
    }

    /**
     * The retired generations that are still reachable, which a leak detector reports.
     *
     * @return The retired generations not yet collected
     */
    public List<GenerationClassLoader> liveRetiredGenerations() {
        List<GenerationClassLoader> live = new ArrayList<>();
        synchronized (retired) {
            retired.removeIf(reference -> reference.get() == null);
            for (WeakReference<GenerationClassLoader> reference : retired) {
                GenerationClassLoader generation = reference.get();
                if (generation != null) {
                    live.add(generation);
                }
            }
        }
        return live;
    }

    /**
     * The retired generations, for a {@code ClassChangeEvent}.
     *
     * @return The retired generations still reachable, as a set
     */
    public Set<ClassLoader> retiredLoaders() {
        return Set.copyOf(liveRetiredGenerations());
    }

    private boolean isRetired(GenerationClassLoader generation) {
        synchronized (retired) {
            for (WeakReference<GenerationClassLoader> reference : retired) {
                if (reference.get() == generation) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // the parent was asked first by loadClass; the generation's own directories come next
        return current.findInGeneration(name);
    }

    @Override
    @Nullable
    protected URL findResource(String name) {
        return current.findInGenerationResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return current.findInGenerationResources(name);
    }

    @Override
    public String toString() {
        return getName() + "(generation " + generation() + ")";
    }
}
