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
package io.micronaut.dev.manifest;

import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.compile.CompileMode;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * What a build tool tells the development launcher about the project: the classpaths, the roots,
 * how to compile, what to retain. Written as a {@code java.util.Properties} file, every key under
 * {@code micronaut.dev.}, so that a build can write it from its model without running anything and
 * any key can be overridden with a system property of the same name.
 *
 * <p>Paths are resolved against the manifest's own directory unless absolute. A list is separated by
 * commas or the platform's path separator; a value starting with {@code @} names an argument file
 * holding one entry per line, which keeps long classpaths off the command line.</p>
 *
 * <pre>
 * micronaut.dev.main-class=example.Application
 * micronaut.dev.project-dir=.
 * micronaut.dev.strategy=auto
 * micronaut.dev.runtime-classpath=@runtime.argfile
 * micronaut.dev.reloadable=build/classes/java/main,build/resources/main
 * micronaut.dev.compile-classpath=@compile.argfile
 * micronaut.dev.processor-path=@processors.argfile
 * micronaut.dev.sources.java=src/main/java
 * micronaut.dev.resources.config=src/main/resources
 * micronaut.dev.resources.views=src/main/resources/views
 * micronaut.dev.compile.mode=embedded
 * micronaut.dev.compile.incremental=true
 * micronaut.dev.compile.java.output=build/classes/java/main
 * micronaut.dev.compile.java.generated-sources=build/generated/sources/annotationProcessor/java/main
 * micronaut.dev.compile.java.options=-parameters,--release,21,-Amicronaut.processing.group=example
 * micronaut.dev.build-tool=gradle
 * micronaut.dev.build-tool.trigger=build/micronaut-dev/reload
 * micronaut.dev.retain=javax.sql.DataSource
 * micronaut.dev.livereload.enabled=auto
 * </pre>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class DevManifest {

    /**
     * The prefix of every key.
     */
    public static final String PREFIX = "micronaut.dev.";

    /**
     * The system property naming the manifest file.
     */
    public static final String MANIFEST_PROPERTY = PREFIX + "manifest";

    private final Path directory;
    private final String mainClass;
    private final Path projectDir;
    private final ReloadStrategy strategy;
    private final List<Path> runtimeClasspath;
    private final List<Path> reloadableRoots;
    private final List<Path> compileClasspath;
    private final List<Path> processorPath;
    private final List<SourceRoot> sourceRoots;
    private final List<ResourceRoot> resourceRoots;
    private final CompileMode compileMode;
    private final Map<SourceKind, CompileMode> compileModes;
    private final boolean incremental;
    private final Map<SourceKind, Path> classOutputs;
    private final Map<SourceKind, Path> generatedSources;
    private final Map<SourceKind, List<String>> compileOptions;
    @Nullable
    private final String buildTool;
    @Nullable
    private final Path buildToolTrigger;
    private final List<String> retain;
    private final LiveReload liveReload;

    private DevManifest(Path directory, Properties properties) {
        this.directory = directory;
        this.mainClass = require(properties, "main-class");
        this.projectDir = path(directory, properties.getProperty(PREFIX + "project-dir", "."));
        this.strategy = ReloadStrategy.valueOf(properties.getProperty(PREFIX + "strategy", "auto").trim().toUpperCase(Locale.ROOT));
        this.runtimeClasspath = paths(directory, properties.getProperty(PREFIX + "runtime-classpath", ""));
        this.reloadableRoots = paths(directory, require(properties, "reloadable"));
        this.compileClasspath = paths(directory, properties.getProperty(PREFIX + "compile-classpath", ""));
        this.processorPath = paths(directory, properties.getProperty(PREFIX + "processor-path", ""));
        this.sourceRoots = sourceRoots(directory, properties);
        this.resourceRoots = resourceRoots(directory, properties);
        this.compileMode = CompileMode.of(properties.getProperty(PREFIX + "compile.mode", "embedded"))
            .orElseThrow(() -> new IllegalArgumentException("Unknown compile mode " + properties.getProperty(PREFIX + "compile.mode")));
        this.compileModes = perKind(properties, "mode", value -> CompileMode.of(value).orElseThrow(() -> new IllegalArgumentException("Unknown compile mode " + value)));
        this.incremental = Boolean.parseBoolean(properties.getProperty(PREFIX + "compile.incremental", "true"));
        this.classOutputs = perKind(properties, "output", value -> path(directory, value));
        this.generatedSources = perKind(properties, "generated-sources", value -> path(directory, value));
        this.compileOptions = perKind(properties, "options", value -> options(directory, value));
        this.buildTool = properties.getProperty(PREFIX + "build-tool");
        String trigger = properties.getProperty(PREFIX + "build-tool.trigger");
        this.buildToolTrigger = trigger == null ? null : path(directory, trigger);
        this.retain = list(directory, properties.getProperty(PREFIX + "retain", ""));
        this.liveReload = new LiveReload(
            properties.getProperty(PREFIX + "livereload.enabled", "auto").trim().toLowerCase(Locale.ROOT),
            Integer.parseInt(properties.getProperty(PREFIX + "livereload.port", "35729").trim()),
            Boolean.parseBoolean(properties.getProperty(PREFIX + "livereload.inject-script", "true"))
        );
    }

    /**
     * Reads a manifest file. System properties of the same names override its entries.
     *
     * @param file The properties file
     * @return The manifest
     * @throws UncheckedIOException if the file cannot be read
     * @throws IllegalArgumentException if a required key is missing or a value is invalid
     */
    public static DevManifest load(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(absolute, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the manifest " + absolute, e);
        }
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(PREFIX) && !name.equals(MANIFEST_PROPERTY)) {
                properties.setProperty(name, System.getProperty(name));
            }
        }
        return new DevManifest(Objects.requireNonNull(absolute.getParent(), "manifest directory"), properties);
    }

    /**
     * Builds a manifest from properties, for a launcher that assembles them itself.
     *
     * @param directory The directory relative paths resolve against
     * @param properties The entries
     * @return The manifest
     */
    public static DevManifest of(Path directory, Properties properties) {
        return new DevManifest(directory.toAbsolutePath().normalize(), properties);
    }

    /**
     * @return The directory relative paths resolve against
     */
    public Path directory() {
        return directory;
    }

    /**
     * @return The class declaring the application's {@code main} method
     */
    public String mainClass() {
        return mainClass;
    }

    /**
     * @return The project directory, the working directory of the application
     */
    public Path projectDir() {
        return projectDir;
    }

    /**
     * @return The reload strategy
     */
    public ReloadStrategy strategy() {
        return strategy;
    }

    /**
     * @return The library jars of the parent loader
     */
    public List<Path> runtimeClasspath() {
        return runtimeClasspath;
    }

    /**
     * @return The class and resource directories of the reloadable tier, in order
     */
    public List<Path> reloadableRoots() {
        return reloadableRoots;
    }

    /**
     * @return What the sources compile against
     */
    public List<Path> compileClasspath() {
        return compileClasspath;
    }

    /**
     * @return The annotation processor path
     */
    public List<Path> processorPath() {
        return processorPath;
    }

    /**
     * @return The typed source roots
     */
    public List<SourceRoot> sourceRoots() {
        return sourceRoots;
    }

    /**
     * The source roots of one language.
     *
     * @param kind The language
     * @return The roots
     */
    public List<SourceRoot> sourceRoots(SourceKind kind) {
        return sourceRoots.stream().filter(root -> root.kind() == kind).toList();
    }

    /**
     * @return The typed resource roots
     */
    public List<ResourceRoot> resourceRoots() {
        return resourceRoots;
    }

    /**
     * How the sources of a language are compiled: the language's own setting, else the global one.
     *
     * @param kind The language
     * @return The mode
     */
    public CompileMode compileMode(SourceKind kind) {
        return compileModes.getOrDefault(kind, compileMode);
    }

    /**
     * @return Whether compilations are incremental
     */
    public boolean isIncremental() {
        return incremental;
    }

    /**
     * The class output directory of a language: its own setting, else the first reloadable root.
     *
     * @param kind The language
     * @return The directory
     */
    public Path classOutput(SourceKind kind) {
        Path output = classOutputs.get(kind);
        if (output != null) {
            return output;
        }
        if (reloadableRoots.isEmpty()) {
            throw new IllegalStateException("No class output for " + kind + " and no reloadable root");
        }
        return reloadableRoots.getFirst();
    }

    /**
     * The generated sources directory of a language: its own setting, else a directory next to the class output.
     *
     * @param kind The language
     * @return The directory
     */
    public Path generatedSources(SourceKind kind) {
        Path generated = generatedSources.get(kind);
        return generated != null ? generated : classOutput(kind).resolveSibling("generated-sources-" + kind.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The compiler options of a language, as the build passes them.
     *
     * @param kind The language
     * @return The options
     */
    public List<String> compileOptions(SourceKind kind) {
        return compileOptions.getOrDefault(kind, List.of());
    }

    /**
     * @return The build tool that compiles in build-tool mode ({@code gradle} or {@code maven}), if named
     */
    @Nullable
    public String buildTool() {
        return buildTool;
    }

    /**
     * @return The file the build tool touches when it finished compiling, if named
     */
    @Nullable
    public Path buildToolTrigger() {
        return buildToolTrigger;
    }

    /**
     * @return The names of the types whose beans are retained across a restart
     */
    public List<String> retain() {
        return retain;
    }

    /**
     * @return The LiveReload settings
     */
    public LiveReload liveReload() {
        return liveReload;
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(PREFIX + key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The manifest lacks " + PREFIX + key);
        }
        return value.trim();
    }

    private static Path path(Path directory, String value) {
        return directory.resolve(value.trim()).toAbsolutePath().normalize();
    }

    private static List<Path> paths(Path directory, String value) {
        List<Path> paths = new ArrayList<>();
        for (String entry : list(directory, value)) {
            paths.add(path(directory, entry));
        }
        return Collections.unmodifiableList(paths);
    }

    /**
     * Splits a list value: entries separated by commas or the path separator, or the lines of an
     * argument file when the value starts with {@code @}, the file resolved against the manifest's
     * directory unless absolute.
     */
    static List<String> list(Path directory, String value) {
        return list(directory, value, true);
    }

    /**
     * Splits compiler options: entries separated by commas only, since an option such as
     * {@code -Xlint:deprecation} holds the path separator of Unix; or the lines of an argument file,
     * resolved against the manifest's directory unless absolute.
     */
    static List<String> options(Path directory, String value) {
        return list(directory, value, false);
    }

    private static List<String> list(@Nullable Path directory, String value, boolean splitOnPathSeparator) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.startsWith("@")) {
            Path named = Path.of(trimmed.substring(1));
            Path argfile = directory == null || named.isAbsolute() ? named : directory.resolve(named);
            try {
                List<String> entries = new ArrayList<>();
                for (String line : Files.readAllLines(argfile, StandardCharsets.UTF_8)) {
                    String entry = line.trim();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        entries.add(entry);
                    }
                }
                return Collections.unmodifiableList(entries);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the argument file " + argfile, e);
            }
        }
        List<String> entries = new ArrayList<>();
        String separators = splitOnPathSeparator ? "[,\\Q" + java.io.File.pathSeparator + "\\E]" : ",";
        for (String entry : trimmed.split(separators)) {
            String element = entry.trim();
            if (!element.isEmpty()) {
                entries.add(element);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    private static List<SourceRoot> sourceRoots(Path directory, Properties properties) {
        List<SourceRoot> roots = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(PREFIX + "sources.")) {
                String kindName = name.substring((PREFIX + "sources.").length());
                SourceKind kind = SourceKind.of(kindName).orElseThrow(() -> new IllegalArgumentException("Unknown source kind in " + name));
                for (Path path : paths(directory, properties.getProperty(name))) {
                    roots.add(new SourceRoot(kind, path));
                }
            }
        }
        return Collections.unmodifiableList(roots);
    }

    private static List<ResourceRoot> resourceRoots(Path directory, Properties properties) {
        List<ResourceRoot> roots = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(PREFIX + "resources.")) {
                String kindName = name.substring((PREFIX + "resources.").length()).toUpperCase(Locale.ROOT);
                ResourceKind kind;
                try {
                    kind = ResourceKind.valueOf(kindName);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown resource kind in " + name, e);
                }
                for (Path path : paths(directory, properties.getProperty(name))) {
                    roots.add(new ResourceRoot(kind, path));
                }
            }
        }
        return Collections.unmodifiableList(roots);
    }

    private static <V> Map<SourceKind, V> perKind(Properties properties, String suffix, java.util.function.Function<String, V> parser) {
        Map<SourceKind, V> values = new EnumMap<>(SourceKind.class);
        for (SourceKind kind : SourceKind.values()) {
            String value = properties.getProperty(PREFIX + "compile." + kind.name().toLowerCase(Locale.ROOT) + "." + suffix);
            if (value != null && !value.isBlank()) {
                values.put(kind, parser.apply(value));
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * The LiveReload settings.
     *
     * @param enabled {@code true}, {@code false} or {@code auto} (on when a static or views root is present)
     * @param port The port the LiveReload server listens on
     * @param injectScript Whether the client script is added to HTML responses
     */
    public record LiveReload(String enabled, int port, boolean injectScript) {

        /**
         * Whether the server runs, given whether static or view resources are served.
         *
         * @param servesStaticOrViews Whether the manifest has a static or views root
         * @return True if the server runs
         */
        public boolean isEnabled(boolean servesStaticOrViews) {
            return switch (enabled) {
                case "true" -> true;
                case "false" -> false;
                default -> servesStaticOrViews;
            };
        }
    }
}
