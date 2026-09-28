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
package io.micronaut.dev.change;

import io.micronaut.context.reload.ClassChange;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The contents of the reloadable output directories at one moment: every class file by binary name
 * and every other file by relative path, each with a digest of its bytes. Two snapshots tell what a
 * compilation, embedded or by the build tool, changed.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class OutputSnapshot {

    private final Map<String, byte[]> classes;
    private final Map<String, byte[]> resources;

    private OutputSnapshot(Map<String, byte[]> classes, Map<String, byte[]> resources) {
        this.classes = classes;
        this.resources = resources;
    }

    /**
     * Reads the given directories. A missing directory contributes nothing.
     *
     * @param roots The class and resource directories
     * @return The snapshot
     * @throws UncheckedIOException if a file cannot be read
     */
    public static OutputSnapshot of(List<Path> roots) {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        Map<String, byte[]> resources = new LinkedHashMap<>();
        for (Path root : roots) {
            Path absoluteRoot = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(absoluteRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(absoluteRoot)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    String relative = absoluteRoot.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                    if (relative.endsWith(".class")) {
                        String className = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                        classes.putIfAbsent(className, digest(file));
                    } else {
                        resources.putIfAbsent(relative, digest(file));
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + absoluteRoot, e);
            }
        }
        return new OutputSnapshot(classes, resources);
    }

    /**
     * @return An empty snapshot
     */
    public static OutputSnapshot empty() {
        return new OutputSnapshot(Map.of(), Map.of());
    }

    /**
     * @return The binary names of the classes
     */
    public Set<String> classNames() {
        return Set.copyOf(classes.keySet());
    }

    /**
     * @return The relative paths of the other files
     */
    public Set<String> resourcePaths() {
        return Set.copyOf(resources.keySet());
    }

    /**
     * What changed from this snapshot to a later one.
     *
     * @param later The later snapshot
     * @return The changes
     */
    public ChangeSet diff(OutputSnapshot later) {
        List<ClassChange> classChanges = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : later.classes.entrySet()) {
            byte[] before = classes.get(entry.getKey());
            if (before == null) {
                classChanges.add(new ClassChange(entry.getKey(), ClassChange.Kind.ADDED));
            } else if (!Arrays.equals(before, entry.getValue())) {
                classChanges.add(new ClassChange(entry.getKey(), ClassChange.Kind.MODIFIED));
            }
        }
        for (String name : classes.keySet()) {
            if (!later.classes.containsKey(name)) {
                classChanges.add(new ClassChange(name, ClassChange.Kind.REMOVED));
            }
        }
        Set<String> changedResources = new HashSet<>();
        for (Map.Entry<String, byte[]> entry : later.resources.entrySet()) {
            byte[] before = resources.get(entry.getKey());
            if (before == null || !Arrays.equals(before, entry.getValue())) {
                changedResources.add(entry.getKey());
            }
        }
        Set<String> removedResources = new HashSet<>();
        for (String path : resources.keySet()) {
            if (!later.resources.containsKey(path)) {
                removedResources.add(path);
            }
        }
        return new ChangeSet(classChanges, changedResources, removedResources);
    }

    private static byte[] digest(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(Files.readAllBytes(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }
}
