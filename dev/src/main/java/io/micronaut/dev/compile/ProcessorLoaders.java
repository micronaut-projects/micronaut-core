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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * The class loaders of the annotation processors of an embedded javac, kept from one compilation to the next while
 * the processor path and what it holds stay the same: a processor's classes are loaded, verified and compiled by the
 * JIT once, rather than for every edit.
 *
 * <p>What a processor keeps in static fields stays with its classes, as in a build tool's daemon that keeps the
 * processors' loader. A processor that keeps state of one compilation there must release it when the compilation
 * ends, as Micronaut's own do; one that does not will see the previous compilation's state in the next. Micronaut
 * OpenAPI 7.3.0 is one: its document keeps the schemas of the previous compilation, which
 * micronaut-projects/micronaut-openapi#2877 fixes. The same holds for objects in static fields: the visitors of
 * {@code BeanElementVisitor.VISITORS}, which Micronaut loads once per loader, are the same instances in every
 * compilation, each given {@code start} and {@code finish} again, and keep any state of their own between them.</p>
 *
 * <p>A compilation that fails after the processors ran, whatever failed, does not use their loader again: a processor
 * may have failed and been skipped, its class left uninitializable, or the compilation may have stopped before the
 * final round in which the processors release their state. The next compilation loads them again, as before.</p>
 *
 * <p>The system property {@value #RETAIN_PROPERTY}, {@code true} by default, set to {@code false} on the development
 * JVM gives every compilation a loader of its own.</p>
 *
 * <p>A loader stops being kept, when it is discarded or the compiler closes, without being closed while a compilation
 * still uses it: it closes once the last one using it returns it.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class ProcessorLoaders {

    /**
     * The system property that, set to {@code false}, gives every compilation a processor loader of its own.
     */
    static final String RETAIN_PROPERTY = "micronaut.dev.compile.retain-processors";

    private static final Logger LOG = LoggerFactory.getLogger(ProcessorLoaders.class);

    /**
     * Whether loaders are kept: not on Windows, where a jar a loader holds open cannot be replaced, so a processor the
     * build rebuilds while development mode runs could not be written.
     */
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private final Map<List<Path>, Retained> loaders = new HashMap<>();
    private final Map<Path, ManifestClassPath> manifestClassPaths = new HashMap<>();

    /**
     * The loader of the processors on a path: the one kept for it if what the path holds has not changed since,
     * otherwise a new one, which is kept in its place.
     *
     * @param processorPath The processor path
     * @return The loader, to give back through {@link #returned(ClassLoader)} when the compilation ends; closing it, as
     *         javac does when a compilation ends, does not close it, except on Windows or with {@value #RETAIN_PROPERTY}
     *         set to {@code false}, where each compilation has a loader of its own
     */
    synchronized ClassLoader loaderOf(List<Path> processorPath) {
        List<Path> key = List.copyOf(processorPath);
        if (!retains()) {
            return new CompilerPluginLoader(urls(key), List.of());
        }
        Map<Path, String> fingerprint = fingerprint(key);
        Retained retained = loaders.get(key);
        if (retained != null && retained.fingerprint().equals(fingerprint)) {
            retained.loader().acquire();
            return retained.loader();
        }
        if (retained != null) {
            LOG.debug("The processor path changed: loading the processors again");
            retained.loader().release();
        }
        Loader loader = new Loader(urls(key));
        loaders.put(key, new Retained(fingerprint, loader));
        loader.acquire();
        return loader;
    }

    /**
     * Gives back a loader of {@link #loaderOf(List)} once the compilation using it ended: one no longer kept closes when
     * no other compilation uses it.
     *
     * @param loader The loader
     */
    void returned(ClassLoader loader) {
        if (loader instanceof Loader kept) {
            kept.giveBack();
        }
    }

    private static boolean retains() {
        return !WINDOWS && Boolean.parseBoolean(System.getProperty(RETAIN_PROPERTY, "true").trim());
    }

    /**
     * Stops keeping the loader of a path, after a compilation that ran its processors failed: the next compilation loads
     * them again.
     *
     * @param processorPath The processor path
     */
    synchronized void discard(List<Path> processorPath) {
        Retained retained = loaders.remove(List.copyOf(processorPath));
        if (retained != null) {
            retained.loader().release();
        }
    }

    /**
     * Stops keeping every loader: each closes once no compilation uses it.
     */
    synchronized void close() {
        loaders.values().forEach(retained -> retained.loader().release());
        loaders.clear();
    }

    private static URL[] urls(List<Path> processorPath) {
        URL[] urls = new URL[processorPath.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = processorPath.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid processor path entry: " + processorPath.get(i), e);
            }
        }
        return urls;
    }

    /**
     * What a processor path holds, as far as a loader of it can tell: each file's identity, size and times, a directory's
     * regular files included, so that a processor recompiled into a directory of the path, or a jar replaced, is loaded
     * again. The contents are not read: the identity and the change time, where the file system has them, tell a file
     * rewritten within the same millisecond to the same size. The jars a jar's manifest names on its {@code Class-Path},
     * which the loader reads as well, are part of it.
     */
    private Map<Path, String> fingerprint(List<Path> processorPath) {
        Map<Path, String> fingerprint = new HashMap<>();
        Deque<Path> pending = new ArrayDeque<>(processorPath);
        Set<Path> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            Path entry = pending.poll();
            if (!visited.add(entry)) {
                continue;
            }
            if (Files.isDirectory(entry)) {
                try (Stream<Path> files = Files.walk(entry)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        addFile(fingerprint, file);
                    }
                } catch (IOException | UncheckedIOException e) {
                    // a directory changing while it is read: it differs from whatever was kept
                    fingerprint.put(entry, "unreadable " + System.nanoTime());
                }
            } else {
                addFile(fingerprint, entry);
                pending.addAll(manifestClassPath(entry, fingerprint.get(entry)));
            }
        }
        return fingerprint;
    }

    /**
     * The entries a jar's manifest adds to the class path, read again only when the jar changed. The loader reads any file
     * of the path as a jar, whatever its name.
     */
    private List<Path> manifestClassPath(Path jar, @Nullable String jarFingerprint) {
        if (jarFingerprint == null) {
            return List.of();
        }
        ManifestClassPath known = manifestClassPaths.get(jar);
        if (known != null && known.jarFingerprint().equals(jarFingerprint)) {
            return known.entries();
        }
        List<Path> entries = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            String classPath = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classPath != null) {
                for (String relative : classPath.trim().split("\\s+")) {
                    try {
                        entries.add(Path.of(jar.toUri().resolve(relative)));
                    } catch (RuntimeException e) {
                        // not a file URL: the loader cannot read it from a path either
                    }
                }
            }
        } catch (IOException e) {
            // not a jar the loader can read: nothing more on the path
        }
        manifestClassPaths.put(jar, new ManifestClassPath(jarFingerprint, List.copyOf(entries)));
        return entries;
    }

    private static void addFile(Map<Path, String> fingerprint, Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (attributes.isRegularFile()) {
                fingerprint.put(file, attributes.fileKey() + ":" + attributes.size() + ":" + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + ":" + changeTime(file));
            }
        } catch (IOException e) {
            fingerprint.put(file, "absent");
        }
    }

    private static Object changeTime(Path file) {
        try {
            return Files.getAttribute(file, "unix:ctime");
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            // a file system without the change time: the others tell
            return "";
        }
    }

    /**
     * Whether a loader of {@link #loaderOf(List)} was closed: for tests.
     *
     * @param loader The loader
     * @return True once closed; false for a loader that is not kept, which javac closes
     */
    static boolean isClosed(ClassLoader loader) {
        return loader instanceof Loader kept && kept.isClosed();
    }

    private record Retained(Map<Path, String> fingerprint, Loader loader) {
    }

    private record ManifestClassPath(String jarFingerprint, List<Path> entries) {
    }

    /**
     * A processor loader that javac's close at the end of a compilation leaves open. It closes once it is no longer kept
     * and no compilation uses it.
     */
    private static final class Loader extends CompilerPluginLoader {

        private int users;
        private boolean released;
        private boolean closed;

        Loader(URL[] urls) {
            super(urls, List.of());
        }

        @Override
        public void close() {
            // javac closes the processors' loader when a compilation ends; this one serves the next
        }

        synchronized void acquire() {
            users++;
        }

        synchronized void giveBack() {
            users--;
            closeIfUnused();
        }

        synchronized void release() {
            released = true;
            closeIfUnused();
        }

        synchronized boolean isClosed() {
            return closed;
        }

        private void closeIfUnused() {
            if (!released || users > 0 || closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } catch (IOException e) {
                LOG.debug("Cannot close a processor loader", e);
            }
        }
    }
}
