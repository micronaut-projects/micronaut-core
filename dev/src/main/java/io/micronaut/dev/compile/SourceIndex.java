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

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The sources under the roots and the top-level classes each declares.
 *
 * <p>A source declares the class its path names, and may declare further package-private top-level
 * classes; which ones is learnt from what javac writes for the source and kept in a mapping file
 * beside the class output, so that the next compilation, or a deletion, knows every class the source
 * produced. A source the embedded compiler has not compiled yet is known by its path only.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class SourceIndex {

    /**
     * The suffix of the mapping file, beside the class output; the language's name follows it, so two
     * languages sharing one output keep their records apart and a full compilation of one leaves the
     * other's outputs alone.
     */
    static final String MAPPING_SUFFIX = ".micronaut-dev-sources-";
    private static final String LEGACY_MAPPING_SUFFIX = ".micronaut-dev-sources";
    private static final String GENERATED_KEY_SUFFIX = "#generated";
    private static final String RESOURCES_KEY_SUFFIX = "#resources";

    private final List<SourceRoot> roots;
    private final Map<Path, Set<String>> classesBySource;
    private final Map<String, Path> sourceByClass;
    private final Map<Path, Set<String>> recorded;
    private final Map<Path, Set<String>> generated;
    private final Map<Path, Set<String>> resources;

    private SourceIndex(List<SourceRoot> roots, Map<Path, Set<String>> classesBySource, Map<String, Path> sourceByClass, Map<Path, Set<String>> recorded, Map<Path, Set<String>> generated, Map<Path, Set<String>> resources) {
        this.roots = roots;
        this.classesBySource = classesBySource;
        this.sourceByClass = sourceByClass;
        this.recorded = recorded;
        this.generated = generated;
        this.resources = resources;
    }

    /**
     * Scans the roots and reads the mapping file of the given class output.
     *
     * @param roots The source roots
     * @param classOutput The class output the mapping file sits beside
     * @param kind The language, which names the mapping file
     * @return The index
     */
    static SourceIndex scan(List<SourceRoot> roots, Path classOutput, SourceKind kind) {
        Map<Path, Set<String>> recorded = new LinkedHashMap<>();
        Map<Path, Set<String>> generated = new LinkedHashMap<>();
        Map<Path, Set<String>> resources = new LinkedHashMap<>();
        Path mapping = mappingFile(classOutput, kind);
        if (!Files.isRegularFile(mapping) && kind == SourceKind.JAVA) {
            // the layout before the mapping was kept per language named the Java one without the suffix
            mapping = classOutput.resolveSibling(classOutput.getFileName() + LEGACY_MAPPING_SUFFIX);
        }
        load(mapping, recorded, generated, resources);
        Map<Path, Set<String>> classesBySource = new LinkedHashMap<>();
        Map<String, Path> sourceByClass = new LinkedHashMap<>();
        for (SourceRoot root : roots) {
            // a request may carry the roots of another language the compiler reads, Java for Kotlin: they are not its sources
            if (root.kind() != kind || !Files.isDirectory(root.path())) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root.path())) {
                files.filter(Files::isRegularFile).filter(root.kind()::matches).forEach(file -> {
                    Path absolute = file.toAbsolutePath().normalize();
                    Set<String> classes = new LinkedHashSet<>();
                    classes.add(root.classNameOf(absolute));
                    classes.addAll(recorded.getOrDefault(absolute, Set.of()));
                    classesBySource.put(absolute, classes);
                    for (String className : classes) {
                        sourceByClass.putIfAbsent(className, absolute);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot scan " + root.path(), e);
            }
        }
        return new SourceIndex(roots, classesBySource, sourceByClass, recorded, generated, resources);
    }

    static Path mappingFile(Path classOutput, SourceKind kind) {
        return classOutput.resolveSibling(classOutput.getFileName() + MAPPING_SUFFIX + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    Set<Path> allSources() {
        return new LinkedHashSet<>(classesBySource.keySet());
    }

    Optional<Path> sourceOf(String topLevelClass) {
        return Optional.ofNullable(sourceByClass.get(topLevelClass));
    }

    /**
     * The top-level classes a source declares, by its path under a root and by what it produced last
     * time, whether or not the file still exists.
     *
     * @param source The source
     * @return The classes, empty when the source is under no root
     */
    Set<String> classesOf(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        Set<String> known = classesBySource.get(absolute);
        if (known != null) {
            return known;
        }
        Set<String> classes = new LinkedHashSet<>();
        for (SourceRoot root : roots) {
            if (absolute.startsWith(root.path()) && root.kind().matches(absolute)) {
                classes.add(root.classNameOf(absolute));
                break;
            }
        }
        classes.addAll(recorded.getOrDefault(absolute, Set.of()));
        return classes;
    }

    /**
     * The sources an annotation processor generated for a source last time, relative to the generated
     * sources directory.
     *
     * @param source The source
     * @return The generated sources
     */
    Set<String> generatedOf(Path source) {
        return generated.getOrDefault(source.toAbsolutePath().normalize(), Set.of());
    }

    /**
     * The resources an annotation processor wrote into the class output for a source last time,
     * relative to the class output.
     *
     * @param source The source
     * @return The resources
     */
    Set<String> resourcesOf(Path source) {
        return resources.getOrDefault(source.toAbsolutePath().normalize(), Set.of());
    }

    /**
     * Every source the mapping knows, whether or not it still exists: what the embedded compiler
     * produced outputs for.
     *
     * @return The recorded sources
     */
    Set<Path> recordedSources() {
        Set<Path> all = new LinkedHashSet<>(recorded.keySet());
        all.addAll(generated.keySet());
        all.addAll(resources.keySet());
        return all;
    }

    /**
     * The top-level classes recorded for a source, without the one its path names.
     *
     * @param source The source
     * @return The recorded classes
     */
    Set<String> recordedClassesOf(Path source) {
        return recorded.getOrDefault(source.toAbsolutePath().normalize(), Set.of());
    }

    /**
     * Records what a successful compilation produced and writes the mapping to a file, for the caller
     * to move into place with the outputs it describes.
     *
     * @param file Where to write the mapping
     * @param produced The top-level classes javac wrote, by source
     * @param producedSources The sources the processors generated, relative to the generated sources directory, by source
     * @param producedResources The resources the processors wrote into the class output, relative to it, by source
     * @param compiled The sources compiled, whose previous record is replaced
     * @param deleted The sources deleted, whose record goes
     * @param full Whether the compilation was a full one, replacing the whole record
     */
    void recordProduced(Path file, Map<Path, Set<String>> produced, Map<Path, Set<String>> producedSources, Map<Path, Set<String>> producedResources, Set<Path> compiled, Set<Path> deleted, boolean full) {
        if (full) {
            recorded.clear();
            generated.clear();
            resources.clear();
        }
        for (Path source : compiled) {
            Path key = source.toAbsolutePath().normalize();
            recorded.remove(key);
            generated.remove(key);
            resources.remove(key);
        }
        for (Path source : deleted) {
            Path key = source.toAbsolutePath().normalize();
            recorded.remove(key);
            generated.remove(key);
            resources.remove(key);
        }
        for (Map.Entry<Path, Set<String>> entry : produced.entrySet()) {
            recorded.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        for (Map.Entry<Path, Set<String>> entry : producedSources.entrySet()) {
            generated.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        for (Map.Entry<Path, Set<String>> entry : producedResources.entrySet()) {
            resources.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        Properties properties = new Properties();
        for (Map.Entry<Path, Set<String>> entry : recorded.entrySet()) {
            properties.setProperty(entry.getKey().toString(), String.join(",", entry.getValue()));
        }
        for (Map.Entry<Path, Set<String>> entry : generated.entrySet()) {
            properties.setProperty(entry.getKey() + GENERATED_KEY_SUFFIX, String.join(",", entry.getValue()));
        }
        for (Map.Entry<Path, Set<String>> entry : resources.entrySet()) {
            properties.setProperty(entry.getKey() + RESOURCES_KEY_SUFFIX, String.join(",", entry.getValue()));
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "The top-level classes each source produced, kept by the Micronaut development mode compiler");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static void load(Path file, Map<Path, Set<String>> recorded, Map<Path, Set<String>> generated, Map<Path, Set<String>> resources) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        for (String name : properties.stringPropertyNames()) {
            Set<String> values = new LinkedHashSet<>();
            for (String value : properties.getProperty(name).split(",")) {
                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
            if (name.endsWith(RESOURCES_KEY_SUFFIX)) {
                resources.put(Path.of(name.substring(0, name.length() - RESOURCES_KEY_SUFFIX.length())).toAbsolutePath().normalize(), values);
            } else if (name.endsWith(GENERATED_KEY_SUFFIX)) {
                generated.put(Path.of(name.substring(0, name.length() - GENERATED_KEY_SUFFIX.length())).toAbsolutePath().normalize(), values);
            } else {
                recorded.put(Path.of(name).toAbsolutePath().normalize(), values);
            }
        }
    }
}
