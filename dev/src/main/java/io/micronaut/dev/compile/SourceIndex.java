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

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The sources under the roots and the top-level classes each declares.
 *
 * <p>A source declares the class its path names, and may declare further package-private top-level
 * classes; which ones is learnt from what javac writes for the source and kept in a mapping file
 * beside the class output, so that the next compilation, or a deletion, knows every class the source
 * produced. A source the embedded compiler has not compiled yet is known by its path only.</p>
 *
 * <p>A generated output is credited to every source it originates from, so one class may be recorded for
 * several sources: a bean's definition for the source of its superclass as well as its own. All of them are
 * known here, since the output goes when any of them compiles and only they can write it again.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
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
    // a name of the package directive, each segment an identifier or a name in backticks
    private static final String KOTLIN_NAME = "(?:`[^`\\r\\n]+`|[\\p{L}\\p{Nl}_][\\p{L}\\p{Nl}\\p{Nd}\\p{Mn}\\p{Mc}\\p{Pc}]*)";
    private static final Pattern KOTLIN_PACKAGE = Pattern.compile("package\\s+(" + KOTLIN_NAME + "(?:\\s*\\.\\s*" + KOTLIN_NAME + ")*)");
    private static final Pattern KOTLIN_NAME_SEGMENT = Pattern.compile(KOTLIN_NAME);

    private final List<SourceRoot> roots;
    private final Map<Path, Set<String>> classesBySource;
    private final Map<String, Set<Path>> sourcesByClass;
    private final Map<Path, Set<String>> recorded;
    private final Map<Path, Set<String>> generated;
    private final Map<Path, Set<String>> resources;

    private SourceIndex(List<SourceRoot> roots, Map<Path, Set<String>> classesBySource, Map<String, Set<Path>> sourcesByClass, Map<Path, Set<String>> recorded, Map<Path, Set<String>> generated, Map<Path, Set<String>> resources) {
        this.roots = roots;
        this.classesBySource = classesBySource;
        this.sourcesByClass = sourcesByClass;
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
        Map<String, Set<Path>> sourcesByClass = new LinkedHashMap<>();
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
                    sourcesByClass.computeIfAbsent(root.classNameOf(absolute), c -> new LinkedHashSet<>()).add(absolute);
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot scan " + root.path(), e);
            }
        }
        if (kind == SourceKind.KOTLIN) {
            addKotlinFileClasses(classOutput, classesBySource, recorded);
        }
        // after the class each path names, so that a class belongs first to its own source, whatever else was credited with it
        classesBySource.forEach((source, classes) -> {
            for (String className : classes) {
                sourcesByClass.computeIfAbsent(className, c -> new LinkedHashSet<>()).add(source);
            }
        });
        return new SourceIndex(roots, classesBySource, sourcesByClass, recorded, generated, resources);
    }

    /**
     * Credits a Kotlin source that has no record, one the build compiled, with the top-level classes of the output whose
     * {@code SourceFile} attribute names it in the package it declares, as {@link KotlinCompilation} credits what it compiles. Its path alone names a class that may not exist: a file of
     * top-level declarations compiles to {@code FooKt}, and a file may declare several classes, or none named after it.
     * Without them, an edit of the source would neither hide the classes it declares nor compile their dependents. Only
     * the package directories of such sources are read; two sources of the same package and file name under different
     * roots are credited with neither's classes.
     */
    private static void addKotlinFileClasses(Path classOutput, Map<Path, Set<String>> classesBySource, Map<Path, Set<String>> recorded) {
        if (!Files.isDirectory(classOutput)) {
            return;
        }
        Map<String, Map<String, Path>> sourcesByPackage = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<Path, Set<String>> entry : classesBySource.entrySet()) {
            Path source = entry.getKey();
            if (recorded.containsKey(source) || entry.getValue().isEmpty()) {
                continue;
            }
            // the package the source declares, which kotlinc writes its classes in, whatever its directory
            String packageName = kotlinPackageOf(source);
            if (packageName == null) {
                continue;
            }
            String fileName = source.getFileName().toString();
            Path previous = sourcesByPackage.computeIfAbsent(packageName, p -> new LinkedHashMap<>()).putIfAbsent(fileName, source);
            if (previous != null && !previous.equals(source)) {
                ambiguous.add(packageName + "/" + fileName);
            }
        }
        for (Map.Entry<String, Map<String, Path>> entry : sourcesByPackage.entrySet()) {
            String packageName = entry.getKey();
            Path directory = packageName.isEmpty() ? classOutput : classOutput.resolve(packageName.replace('.', '/'));
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".class") || name.indexOf('$') >= 0 || !Files.isRegularFile(file)) {
                        continue;
                    }
                    String sourceFile = sourceFileOf(file);
                    Path source = sourceFile == null ? null : entry.getValue().get(sourceFile);
                    if (source != null && !ambiguous.contains(packageName + "/" + sourceFile)) {
                        String simpleName = name.substring(0, name.length() - ".class".length());
                        classesBySource.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(packageName.isEmpty() ? simpleName : packageName + "." + simpleName);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot scan " + directory, e);
            }
        }
    }

    /**
     * The package a Kotlin source declares, as kotlinc names it: its segments without backticks.
     *
     * @param text The text of the source
     * @return The package, empty for the root package
     */
    static String kotlinPackage(String text) {
        int position = startOfDeclarations(text);
        Matcher matcher = KOTLIN_PACKAGE.matcher(text).region(position, text.length());
        if (!matcher.lookingAt()) {
            return "";
        }
        // the segments without the blanks between them and without backticks, whatever is inside them
        StringBuilder name = new StringBuilder();
        Matcher segment = KOTLIN_NAME_SEGMENT.matcher(matcher.group(1));
        while (segment.find()) {
            String value = segment.group();
            name.append(name.isEmpty() ? "" : ".").append(value.startsWith("`") ? value.substring(1, value.length() - 1) : value);
        }
        return name.toString();
    }

    /**
     * Where the first token of a Kotlin source that is not a byte order mark, a shebang line, a blank, a comment or a file
     * annotation is: where a package directive is, if there is one.
     */
    private static int startOfDeclarations(String text) {
        int position = text.startsWith("\uFEFF") ? 1 : 0;
        if (text.startsWith("#!", position)) {
            position = lineEnd(text, position);
        }
        while (position < text.length()) {
            char c = text.charAt(position);
            if (Character.isWhitespace(c)) {
                position++;
            } else if (text.startsWith("//", position)) {
                position = lineEnd(text, position);
            } else if (text.startsWith("/*", position)) {
                int depth = 0;
                do {
                    if (text.startsWith("/*", position)) {
                        depth++;
                        position += 2;
                    } else if (text.startsWith("*/", position)) {
                        depth--;
                        position += 2;
                    } else {
                        position++;
                    }
                } while (depth > 0 && position < text.length());
            } else if (text.startsWith("@file", position)) {
                position = endOfFileAnnotation(text, position);
            } else {
                return position;
            }
        }
        return position;
    }

    /**
     * Past a file annotation, {@code @file:Name(...)} or {@code @file:[A B]}, skipping the strings in its arguments.
     */
    private static int endOfFileAnnotation(String text, int start) {
        int position = start + "@file".length();
        int depth = 0;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '"') {
                position++;
                while (position < text.length() && text.charAt(position) != '"' && text.charAt(position) != '\n') {
                    position += text.charAt(position) == '\\' ? 2 : 1;
                }
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
                if (depth == 0) {
                    return position + 1;
                }
            } else if (depth == 0 && Character.isWhitespace(c)) {
                return position;
            }
            position++;
        }
        return position;
    }

    private static int lineEnd(String text, int position) {
        int end = position;
        // a line ends at a line feed or a carriage return, as kotlinc reads it
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    private static @Nullable String kotlinPackageOf(Path source) {
        try {
            return kotlinPackage(Files.readString(source, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // deleted since the scan, or not text: its classes are not looked for
            return null;
        }
    }

    private static @Nullable String sourceFileOf(Path classFile) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(classFile))
                .findAttribute(Attributes.sourceFile())
                .map(attribute -> attribute.sourceFile().stringValue())
                .orElse(null);
        } catch (IOException | IllegalArgumentException e) {
            // a class file being written, or not one at all: it is credited to no source
            return null;
        }
    }

    static Path mappingFile(Path classOutput, SourceKind kind) {
        return classOutput.resolveSibling(classOutput.getFileName() + MAPPING_SUFFIX + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    Set<Path> allSources() {
        return new LinkedHashSet<>(classesBySource.keySet());
    }

    /**
     * The existing source a top-level class belongs to: the one its path names, or else the first credited with it.
     *
     * @param topLevelClass The class
     * @return The source, if any is known
     */
    Optional<Path> sourceOf(String topLevelClass) {
        return sourcesByClass.getOrDefault(topLevelClass, Set.of()).stream().findFirst();
    }

    /**
     * The existing source each class of the given source belongs to, see {@link #sourceOf(String)}.
     *
     * @param source The source, which may be deleted
     * @return The sources, which may include the given one
     */
    Set<Path> ownSourcesOfClassesOf(Path source) {
        Set<Path> own = new LinkedHashSet<>();
        for (String className : classesOf(source)) {
            sourceOf(className).ifPresent(own::add);
        }
        return own;
    }

    /**
     * The existing sources credited with an output of the given source, which may itself be deleted: whatever that
     * source's compilation or deletion removes, only they can write again.
     *
     * @param source The source
     * @return The sources, which may include the given one
     */
    Set<Path> sourcesSharingOutputsWith(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        Set<Path> sharing = new LinkedHashSet<>();
        for (String className : classesOf(absolute)) {
            sharing.addAll(sourcesByClass.getOrDefault(className, Set.of()));
        }
        addSourcesSharing(generated, absolute, sharing);
        addSourcesSharing(resources, absolute, sharing);
        return sharing;
    }

    /**
     * Whether the mapping records what the source produced. A source the embedded compiler has not compiled yet has no
     * record, whether it was added since or the build tool compiled it.
     *
     * @param source The source
     * @return True if the mapping records it
     */
    boolean isRecorded(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        return recorded.containsKey(absolute) || generated.containsKey(absolute) || resources.containsKey(absolute);
    }

    /**
     * The existing sources credited with a resource in the class output that another existing source is credited with
     * too. Such a resource is what a processor aggregates from several sources and writes again whole from the sources
     * of one compilation.
     *
     * @return The sources
     */
    Set<Path> sourcesOfSharedResources() {
        Map<String, Set<Path>> sourcesByResource = new LinkedHashMap<>();
        resources.forEach((source, names) -> {
            if (classesBySource.containsKey(source)) {
                for (String name : names) {
                    sourcesByResource.computeIfAbsent(name, n -> new LinkedHashSet<>()).add(source);
                }
            }
        });
        Set<Path> sharing = new LinkedHashSet<>();
        for (Set<Path> credited : sourcesByResource.values()) {
            if (credited.size() > 1) {
                sharing.addAll(credited);
            }
        }
        return sharing;
    }

    private void addSourcesSharing(Map<Path, Set<String>> record, Path source, Set<Path> sharing) {
        Set<String> outputs = record.get(source);
        if (outputs == null) {
            return;
        }
        for (Map.Entry<Path, Set<String>> entry : record.entrySet()) {
            if (classesBySource.containsKey(entry.getKey()) && !Collections.disjoint(entry.getValue(), outputs)) {
                sharing.add(entry.getKey());
            }
        }
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
        for (Path source : compiled) {
            // a source that produced nothing, a package-info without annotations, is recorded too: it is not a new one
            recorded.putIfAbsent(source.toAbsolutePath().normalize(), new LinkedHashSet<>());
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
