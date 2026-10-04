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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

/**
 * One generation of the reloadable tier: a {@link URLClassLoader} over a snapshot of the class and
 * resource directories, taken when the generation is created.
 *
 * <p>The snapshot is what makes a generation immutable: the build output keeps changing underneath
 * a running generation, by the next compilation or by the build tool, and a class the generation
 * resolves lazily must be the one that was there when the generation started, not a newer one that
 * would mix two compilations in one loader. The snapshot is deleted when the generation is closed or
 * collected.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class GenerationClassLoader extends URLClassLoader {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationClassLoader.class);
    private static final Cleaner CLEANER = Cleaner.create();

    static {
        registerAsParallelCapable();
    }

    private final int generation;
    private final List<Path> sources;
    private final List<Path> roots;
    private final Cleaner.Cleanable snapshotCleanup;
    // the URLs of the directories read live, whose resources hide the build output's copies of them
    private final List<String> live;
    // the resources known to belong to the live directories, whose build copies stay hidden while the live file is absent
    private final LiveResources liveResources;

    private GenerationClassLoader(int generation, List<Path> sources, List<Path> roots, int liveCount, LiveResources liveResources, @Nullable Path snapshot, @Nullable ClassLoader parent) {
        super("micronaut-dev-generation-" + generation, toUrls(roots), parent);
        URL[] urls = getURLs();
        List<String> liveUrls = new ArrayList<>(liveCount);
        for (int i = 0; i < liveCount; i++) {
            liveUrls.add(urls[i].toExternalForm());
        }
        this.live = List.copyOf(liveUrls);
        this.liveResources = liveResources;
        this.generation = generation;
        this.sources = List.copyOf(sources);
        this.roots = List.copyOf(roots);
        this.snapshotCleanup = CLEANER.register(this, new SnapshotCleanup(snapshot));
    }

    /**
     * Creates a generation over the given directories as they are, without a snapshot: for a tier
     * whose directories nothing writes to while the generation runs.
     *
     * @param generation The number of the generation, counted from one
     * @param roots The class and resource directories, searched in order
     * @param parent The parent loader, holding the libraries
     */
    public GenerationClassLoader(int generation, List<Path> roots, @Nullable ClassLoader parent) {
        this(generation, roots, roots, 0, new LiveResources(List.of()), null, parent);
    }

    /**
     * Creates a generation over a snapshot of the given directories.
     *
     * @param generation The number of the generation, counted from one
     * @param sources The class and resource directories to snapshot, searched in order
     * @param snapshotDir Where the snapshot goes, one directory per source under it; emptied first
     * @param parent The parent loader, holding the libraries
     * @return The generation
     * @throws UncheckedIOException if the directories cannot be copied
     */
    public static GenerationClassLoader snapshot(int generation, List<Path> sources, Path snapshotDir, @Nullable ClassLoader parent) {
        return snapshot(generation, List.of(), sources, snapshotDir, parent);
    }

    /**
     * Creates a generation over live directories followed by a snapshot of others. The live directories
     * are read as they are: the resource roots the developer edits, which the generation must serve
     * fresh; the snapshot isolates the generation from the build output the compiler rewrites.
     *
     * @param generation The number of the generation, counted from one
     * @param liveRoots The directories read live, searched first
     * @param sources The directories to snapshot, searched after the live ones
     * @param snapshotDir Where the snapshot goes; emptied first
     * @param parent The parent loader
     * @return The generation
     * @throws UncheckedIOException if the directories cannot be copied
     */
    public static GenerationClassLoader snapshot(int generation, List<Path> liveRoots, List<Path> sources, Path snapshotDir, @Nullable ClassLoader parent) {
        return snapshot(generation, liveRoots, new LiveResources(liveRoots), sources, snapshotDir, parent);
    }

    /**
     * Creates a generation over live directories followed by a snapshot of others, sharing what is known of the live
     * directories' resources with the other generations of the same loader: a resource deleted from a live directory
     * stays deleted in every generation, rather than coming back as the build output's stale copy.
     *
     * @param generation The number of the generation, counted from one
     * @param liveRoots The directories read live, searched first
     * @param liveResources The resources known to belong to the live directories
     * @param sources The directories to snapshot, searched after the live ones
     * @param snapshotDir Where the snapshot goes; emptied first
     * @param parent The parent loader
     * @return The generation
     * @throws UncheckedIOException if the directories cannot be copied
     * @since 5.3.0
     */
    public static GenerationClassLoader snapshot(int generation, List<Path> liveRoots, LiveResources liveResources, List<Path> sources, Path snapshotDir,
                                                 @Nullable ClassLoader parent) {
        List<Path> roots = new ArrayList<>(liveRoots.size() + sources.size());
        roots.addAll(liveRoots);
        try {
            deleteRecursively(snapshotDir);
            for (int i = 0; i < sources.size(); i++) {
                Path root = snapshotDir.resolve(String.valueOf(i));
                Files.createDirectories(root);
                copyTree(sources.get(i), root);
                roots.add(root);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot snapshot the reloadable directories into " + snapshotDir, e);
        }
        List<Path> all = new ArrayList<>(liveRoots);
        all.addAll(sources);
        return new GenerationClassLoader(generation, all, roots, liveRoots.size(), liveResources, snapshotDir, parent);
    }

    /**
     * @return The number of the generation, counted from one
     */
    public int generation() {
        return generation;
    }

    /**
     * @return The directories the generation loads from: the snapshot, or the sources when there is none
     */
    public List<Path> roots() {
        return roots;
    }

    /**
     * @return The directories the generation was created over, which the build writes to
     */
    public List<Path> sources() {
        return sources;
    }

    /**
     * The class of the given name this generation has defined, if it has.
     *
     * @param name The binary name
     * @return The class, or null if the generation has not loaded it
     */
    @Nullable
    public Class<?> loadedClass(String name) {
        synchronized (getClassLoadingLock(name)) {
            return findLoadedClass(name);
        }
    }

    /**
     * Replaces a class file in the generation's snapshot, so that a class not loaded yet is loaded in
     * the version the JVM was told about when the loaded ones were redefined.
     *
     * @param name The binary name
     * @param classFile The new class file
     * @return Whether a class file of that name was in the snapshot and is replaced
     * @throws IOException if the file cannot be written
     */
    public boolean replaceClassFile(String name, byte[] classFile) throws IOException {
        return replaceResource(name.replace('.', '/') + ".class", classFile);
    }

    /**
     * Replaces a file in the generation's snapshot, so that the generation reads the new contents from now on:
     * a class not loaded yet, or a resource such as a Python module that an interpreter reads again. The
     * directories read live are left alone, as they hold the current contents already.
     *
     * @param resource The resource name, relative to the roots, with {@code /} as separator
     * @param contents The new contents
     * @return Whether a file of that name was in the snapshot and is replaced
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the name is not relative to the roots, or resolves outside them
     * @since 5.3.0
     */
    public boolean replaceResource(String resource, byte[] contents) throws IOException {
        checkRelative(resource);
        for (int i = live.size(); i < roots.size(); i++) {
            Path file = resolveUnder(roots.get(i), resource);
            if (Files.isRegularFile(file)) {
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(temporary, contents);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects a resource name that is not relative with {@code /} as separator: empty; absolute; with a drive, as in
     * {@code C:/file} or {@code C:file}; with a backslash, which Windows reads as a separator, so that {@code ..\outside}
     * and {@code \\server\share} would escape the root there; or with a {@code ..} segment.
     *
     * @param resource The resource name
     */
    static void checkRelative(String resource) {
        boolean drive = resource.length() >= 2 && resource.charAt(1) == ':';
        if (resource.isEmpty() || resource.startsWith("/") || drive || resource.indexOf('\\') >= 0
            || Arrays.asList(resource.split("/")).contains("..")) {
            throw new IllegalArgumentException("Not a resource of the generation: " + resource);
        }
    }

    /**
     * Resolves a resource name under a root, refusing a name the file system reads as absolute and a target that,
     * normalized, is not under the normalized root: the last word on where a write goes, whatever the platform's
     * separators and roots.
     *
     * @param root The root
     * @param resource The resource name
     * @return The file under the root
     */
    static Path resolveUnder(Path root, String resource) {
        Path relative;
        try {
            relative = root.getFileSystem().getPath(resource);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Not a resource of the generation: " + resource, e);
        }
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (relative.isAbsolute() || relative.getRoot() != null || !target.startsWith(base) || target.equals(base)) {
            throw new IllegalArgumentException("Not a resource of the generation: " + resource);
        }
        return target;
    }

    /**
     * Finds a class in this generation's directories only, without delegating to the parent.
     *
     * @param name The binary name
     * @return The class
     * @throws ClassNotFoundException if the directories do not hold it
     */
    Class<?> findInGeneration(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            return loaded != null ? loaded : findClass(name);
        }
    }

    /**
     * Finds a resource in this generation's directories only.
     *
     * @param name The resource name
     * @return The resource, or null
     */
    @Nullable
    URL findInGenerationResource(String name) {
        return findResource(name);
    }

    /**
     * The resources of a name, without the build output's copies of those a live directory holds: a resource the
     * build copied from a resource root would be found twice, which configuration loading rejects as a duplicate,
     * and the copy may be stale. Resources under {@code META-INF/} are left alone: a processor generates those into
     * the class output beside the ones a resource root holds, as service descriptors, and every one counts.
     *
     * @param name The resource name
     * @return The resources
     * @throws IOException if the directories cannot be read
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        List<URL> all = Collections.list(super.findResources(name));
        if (live.isEmpty() || name.startsWith("META-INF/")) {
            return Collections.enumeration(all);
        }
        boolean anyLive = false;
        for (URL url : all) {
            if (isLive(url)) {
                anyLive = true;
                seen(name, url);
            }
        }
        if (!anyLive && !liveResources.belongs(name)) {
            return Collections.enumeration(all);
        }
        // the live file wins over the build's copies of it, and a live file deleted takes its copies with it
        List<URL> kept = new ArrayList<>(all.size());
        for (URL url : all) {
            if (isLive(url)) {
                kept.add(url);
            }
        }
        return Collections.enumeration(kept);
    }

    /**
     * The resource of a name, from a live directory first. A resource that belongs to a live directory and is absent
     * from it, deleted by the developer, is not found: the build output's copy of it is stale. Resources under
     * {@code META-INF/} are left alone, as in {@link #findResources(String)}.
     *
     * @param name The resource name
     * @return The resource, or null
     */
    @Override
    @Nullable
    public URL findResource(String name) {
        URL url = super.findResource(name);
        if (url == null || live.isEmpty() || name.startsWith("META-INF/")) {
            return url;
        }
        if (isLive(url)) {
            seen(name, url);
            return url;
        }
        return liveResources.belongs(name) ? null : url;
    }

    private void seen(String name, URL url) {
        if (liveResources.belongs(name)) {
            return;
        }
        try {
            liveResources.seen(Path.of(url.toURI()));
        } catch (URISyntaxException | IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            LOG.debug("Cannot record the live resource {}", url, e);
        }
    }

    private boolean isLive(URL url) {
        String external = url.toExternalForm();
        for (String root : live) {
            if (external.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds resources in this generation's directories only.
     *
     * @param name The resource name
     * @return The resources
     * @throws IOException if the directories cannot be read
     */
    Enumeration<URL> findInGenerationResources(String name) throws IOException {
        return findResources(name);
    }

    @Override
    public void close() throws IOException {
        super.close();
        snapshotCleanup.clean();
    }

    @Override
    public String toString() {
        return getName() + sources;
    }

    private static URL[] toUrls(List<Path> roots) {
        URL[] urls = new URL[roots.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                // a directory URL ends with a slash, or the URL loader treats it as a jar
                urls[i] = roots.get(i).toAbsolutePath().normalize().toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Not a valid root: " + roots.get(i), e);
            }
        }
        return urls;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * Deletes the snapshot once, when the generation is closed or collected. Holds no reference to
     * the loader, as a cleaning action must not.
     *
     * @param snapshot The snapshot directory, or null for a generation without one
     */
    private record SnapshotCleanup(@Nullable Path snapshot) implements Runnable {
        @Override
        public void run() {
            if (snapshot == null) {
                return;
            }
            try {
                deleteRecursively(snapshot);
            } catch (IOException e) {
                LOG.debug("Cannot delete the generation snapshot {}", snapshot, e);
            }
        }
    }
}
