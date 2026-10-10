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
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.dev.compile.CompileMode;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
 * micronaut.dev.patch-in-place=true
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
 * micronaut.dev.max-generations=10
 * micronaut.dev.requests.hold-timeout=30s
 * micronaut.dev.requests.drain-timeout=10s
 * micronaut.dev.requests.retain-sockets=true
 * </pre>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class DevManifest {

    /**
     * The prefix of every key.
     */
    public static final String PREFIX = "micronaut.dev.";

    /**
     * The system property naming the manifest file.
     */
    public static final String MANIFEST_PROPERTY = PREFIX + "manifest";

    /**
     * The key of the generation budget: how many generations one process creates before it closes for its launcher to
     * relaunch it.
     */
    public static final String MAX_GENERATIONS = PREFIX + "max-generations";

    /**
     * The key of how long a request that arrives while a batch is processed waits for it, before it is answered with a
     * 503 and a {@code Retry-After}. A duration, such as {@code 30s} or {@code 500ms}; a bare number is seconds.
     */
    public static final String REQUESTS_HOLD_TIMEOUT = PREFIX + "requests.hold-timeout";

    /**
     * The key of how long a restart waits for the requests in flight on the stopping generation to finish before it
     * stops it anyway.
     */
    public static final String REQUESTS_DRAIN_TIMEOUT = PREFIX + "requests.drain-timeout";

    /**
     * The key of whether the HTTP servers' listening sockets are kept bound across generations, so that a connection
     * made during a restart waits for the next generation instead of being refused. On by default.
     */
    public static final String REQUESTS_RETAIN_SOCKETS = PREFIX + "requests.retain-sockets";

    /**
     * The generation budget in a native image, where the classes of a retired generation are never unloaded: GraalVM's
     * runtime class loading keeps every class it defines, in a metaspace whose size is fixed when the image is built, and
     * the interpreter's data for them on the heap, for the life of the process.
     * Measured on a Pyronaut application whose generations each hold a GraalPy context, adding a route per generation:
     * once a retired generation is released, what stays is its classes and their data, about 1.3 MB of heap a
     * generation, and the resident memory of 61 generations stayed under 1 GB, with the metaspace not exhausted. Fifty
     * bounds that growth with room to spare.
     */
    public static final int NATIVE_MAX_GENERATIONS = 50;

    private static final Duration DEFAULT_HOLD_TIMEOUT = Duration.ofSeconds(30);
    /**
     * Shorter than the hold: a connection that never finishes, a websocket or an event stream, holds every restart this long.
     */
    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private static final String TEST = "test.";

    private final Path directory;
    private final Properties properties;
    private final DevMode mode;
    private final String mainClass;
    private final Path projectDir;
    private final ReloadStrategy strategy;
    private final boolean patchInPlace;
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
    private final Path generations;
    private final List<String> retain;
    private final boolean retainAnnotated;
    private final LiveReload liveReload;
    private final List<SourceRoot> testSourceRoots;
    private final List<ResourceRoot> testResourceRoots;
    private final TestSettings testSettings;
    private final int maxGenerations;
    private final Duration requestHoldTimeout;
    private final Duration requestDrainTimeout;
    private final boolean retainServerSockets;

    private DevManifest(Path directory, Properties properties) {
        this.directory = directory;
        this.properties = new Properties();
        this.properties.putAll(properties);
        this.mode = DevMode.valueOf(properties.getProperty(PREFIX + "mode", "run").trim().toUpperCase(Locale.ROOT));
        // a test run has no application main
        this.mainClass = mode == DevMode.TEST ? properties.getProperty(PREFIX + "main-class", "").trim() : require(properties, "main-class");
        this.projectDir = path(directory, properties.getProperty(PREFIX + "project-dir", "."));
        this.strategy = ReloadStrategy.valueOf(properties.getProperty(PREFIX + "strategy", "auto").trim().toUpperCase(Locale.ROOT));
        this.patchInPlace = Boolean.parseBoolean(properties.getProperty(PREFIX + "patch-in-place", "true").trim());
        this.runtimeClasspath = paths(directory, properties.getProperty(PREFIX + "runtime-classpath", ""));
        List<Path> reloadable = paths(directory, require(properties, "reloadable"));
        this.compileClasspath = paths(directory, properties.getProperty(PREFIX + "compile-classpath", ""));
        this.processorPath = paths(directory, properties.getProperty(PREFIX + "processor-path", ""));
        this.sourceRoots = sourceRoots(directory, properties);
        List<ResourceRoot> resources = resourceRoots(directory, properties);
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
        this.retainAnnotated = Boolean.parseBoolean(properties.getProperty(PREFIX + "retain-annotated", "true").trim());
        this.maxGenerations = maxGenerations(properties.getProperty(MAX_GENERATIONS));
        this.requestHoldTimeout = duration(REQUESTS_HOLD_TIMEOUT, properties.getProperty(REQUESTS_HOLD_TIMEOUT), DEFAULT_HOLD_TIMEOUT);
        this.requestDrainTimeout = duration(REQUESTS_DRAIN_TIMEOUT, properties.getProperty(REQUESTS_DRAIN_TIMEOUT), DEFAULT_DRAIN_TIMEOUT);
        this.retainServerSockets = Boolean.parseBoolean(properties.getProperty(REQUESTS_RETAIN_SOCKETS, "true").trim());
        String generationsDir = properties.getProperty(PREFIX + "generations");
        this.generations = generationsDir == null ? projectDir.resolve("build").resolve("micronaut-dev").resolve("generations") : path(directory, generationsDir);
        this.liveReload = new LiveReload(
            Integer.parseInt(properties.getProperty(PREFIX + "livereload.port", "35729").trim()),
            Boolean.parseBoolean(properties.getProperty(PREFIX + "livereload.inject-script", "true"))
        );
        this.testSourceRoots = sourceRoots(directory, properties, TEST + "sources.");
        this.testResourceRoots = resourceRoots(directory, properties, TEST + "resources.");
        if (mode == DevMode.TEST) {
            // the tests load from the generation as the classes under test do, ahead of them as on a build's test
            // classpath, so a test class may shadow an application class; their resources come first too
            List<Path> withTests = new ArrayList<>();
            for (SourceRoot root : testSourceRoots) {
                Path output = testClassOutput(directory, properties, classOutputs, reloadable, root.kind());
                if (!withTests.contains(output)) {
                    withTests.add(output);
                }
            }
            for (Path root : reloadable) {
                if (!withTests.contains(root)) {
                    withTests.add(root);
                }
            }
            this.reloadableRoots = Collections.unmodifiableList(withTests);
            List<ResourceRoot> withTestResources = new ArrayList<>(testResourceRoots);
            withTestResources.addAll(resources);
            this.resourceRoots = Collections.unmodifiableList(withTestResources);
        } else {
            this.reloadableRoots = reloadable;
            this.resourceRoots = resources;
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(PREFIX + TEST + "parameters.")) {
                parameters.put(name.substring((PREFIX + TEST + "parameters.").length()), properties.getProperty(name).trim());
            }
        }
        String selection = properties.getProperty(PREFIX + TEST + "selection", "affected").trim().toLowerCase(Locale.ROOT);
        if (!selection.equals("affected") && !selection.equals("all")) {
            throw new IllegalArgumentException("Unknown test selection " + selection + ": affected or all");
        }
        this.testSettings = new TestSettings(
            properties.getProperty(PREFIX + TEST + "runner", "junit-platform").trim(),
            selection.equals("affected"),
            Boolean.parseBoolean(properties.getProperty(PREFIX + TEST + "initial-run", "true")),
            Boolean.parseBoolean(properties.getProperty(PREFIX + TEST + "once", "false")),
            path(directory, properties.getProperty(PREFIX + TEST + "reports", "build/micronaut-dev/test-results")),
            path(directory, properties.getProperty(PREFIX + TEST + "html-report", "build/micronaut-dev/test-report")),
            servedPath(properties.getProperty(PREFIX + TEST + "html-report-path", "/tests/")),
            options(directory, properties.getProperty(PREFIX + TEST + "filter", "")),
            parameters
        );
        if (mode == DevMode.TEST && !testSettings.once() && maxGenerations > 0) {
            // test mode loads the classes of generation one and runs every test run on a generation of its own, and checks
            // the budget after a run: the first run takes the second generation, so a budget of two would relaunch after
            // the first run, and a relaunched process that runs its tests when it starts would do so without end
            int minimum = testSettings.initialRun() ? 3 : 2;
            if (maxGenerations < minimum) {
                throw new IllegalArgumentException("Invalid " + MAX_GENERATIONS + ": " + maxGenerations + ", test mode needs at least " + minimum
                    + (testSettings.initialRun() ? " when it runs the tests when it starts" : ": its first run takes the second generation"));
            }
        }
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
     * Whether a change of resources alone is offered to the running application's
     * {@link io.micronaut.context.reload.InPlaceResourceReloader}s before a restart, so that, for example, a Python
     * module whose generated classes did not change is patched into the running interpreters instead of starting a new
     * generation. On by default, under every strategy: patching defines no class, so it needs no agent and is not what
     * {@code restart} opts out of. {@code micronaut.dev.patch-in-place=false} turns it off.
     *
     * @return Whether resources may be patched in place
     */
    public boolean patchInPlace() {
        return patchInPlace;
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
     * The directory the class loader snapshots each generation into, {@code micronaut.dev.generations}: by default
     * {@code build/micronaut-dev/generations} under the project, which a Maven build points into {@code target}. The
     * launcher owns it and empties it when it starts.
     *
     * @return The directory
     */
    public Path generations() {
        return generations;
    }

    /**
     * The generation budget, {@code micronaut.dev.max-generations}: how many generations the runtime creates in one
     * process. When a reload would create one more, the runtime closes and the launcher exits with
     * {@link io.micronaut.dev.MicronautDevMain#RELAUNCH}, for whoever started it to start it again. Unlimited, zero, on
     * the JVM, which unloads a retired generation once nothing refers to it; {@link #NATIVE_MAX_GENERATIONS} in a
     * native image, which never does. {@code unlimited} or {@code 0} lifts it.
     * Test mode runs its tests on a generation of their own from the second on, and checks the budget after a run, so that
     * a run is never lost to the relaunch: watching, it takes at least three, or two without a first run, and run once it
     * takes any.
     *
     * @return The budget, zero when unlimited
     */
    public int maxGenerations() {
        return maxGenerations;
    }

    /**
     * @return The names of the types whose beans are retained across a restart
     */
    public List<String> retain() {
        return retain;
    }

    /**
     * Whether the beans a module annotated with {@link io.micronaut.context.annotation.Retain} are retained too, as they
     * are unless {@code micronaut.dev.retain-annotated=false}, which leaves only what {@code micronaut.dev.retain} names.
     *
     * @return True to retain them
     */
    public boolean retainAnnotated() {
        return retainAnnotated;
    }

    /**
     * How long a request that arrives while a batch is processed is held, {@value #REQUESTS_HOLD_TIMEOUT}: it is
     * served once the batch is done, by the generation that runs then, or answered with a 503 and a {@code Retry-After}
     * when the batch takes longer. 30 seconds by default; the application's configuration may set it when the manifest
     * does not (see {@link #sets(String)}).
     *
     * @return The timeout
     */
    public Duration requestHoldTimeout() {
        return requestHoldTimeout;
    }

    /**
     * How long a restart waits for the requests in flight on the stopping generation, {@value #REQUESTS_DRAIN_TIMEOUT}:
     * they finish on the generation they started on. 10 seconds by default: a connection that never finishes, such as a
     * websocket or an event stream, holds each restart this long. The application's configuration may set it when the
     * manifest does not (see {@link #sets(String)}).
     *
     * @return The timeout
     */
    public Duration requestDrainTimeout() {
        return requestDrainTimeout;
    }

    /**
     * Whether the manifest, or a system property of the same name, sets a key rather than leaving it to its default. A
     * setting the application's configuration may give as well, such as {@value #REQUESTS_HOLD_TIMEOUT} and
     * {@value #REQUESTS_DRAIN_TIMEOUT}, is taken from the application only when the manifest leaves it out: what the
     * launcher was told wins.
     *
     * @param key The key, with the {@code micronaut.dev.} prefix
     * @return True when the manifest gives the key a value
     * @since 5.3.0
     */
    public boolean sets(String key) {
        String value = properties.getProperty(key);
        return value != null && !value.isBlank();
    }

    /**
     * Whether the HTTP servers' listening sockets are kept bound across generations, {@value #REQUESTS_RETAIN_SOCKETS}.
     *
     * @return True unless turned off
     */
    public boolean retainServerSockets() {
        return retainServerSockets;
    }

    /**
     * @return The LiveReload settings
     */
    public LiveReload liveReload() {
        return liveReload;
    }

    /**
     * @return Whether the runtime runs the application or its tests
     */
    public DevMode mode() {
        return mode;
    }

    /**
     * The test source roots, compiled into the test class outputs against the main ones.
     *
     * @return The roots, from {@code micronaut.dev.test.sources.<kind>}
     */
    public List<SourceRoot> testSourceRoots() {
        return testSourceRoots;
    }

    /**
     * The test resource roots, read live ahead of the main ones in test mode.
     *
     * @return The roots, from {@code micronaut.dev.test.resources.<kind>}
     */
    public List<ResourceRoot> testResourceRoots() {
        return testResourceRoots;
    }

    /**
     * @return How test mode runs the tests
     */
    public TestSettings testSettings() {
        return testSettings;
    }

    /**
     * The class output of a test language: {@code micronaut.dev.test.compile.<kind>.output}, or else {@code <output>-test}
     * beside the application's output of that language.
     *
     * @param kind The language
     * @return The directory
     */
    public Path testClassOutput(SourceKind kind) {
        return testClassOutput(directory, properties, classOutputs, reloadableRoots, kind);
    }

    private static Path testClassOutput(Path directory, Properties properties, Map<SourceKind, Path> classOutputs, List<Path> reloadable, SourceKind kind) {
        String output = properties.getProperty(PREFIX + TEST + "compile." + kind.name().toLowerCase(Locale.ROOT) + ".output");
        if (output != null && !output.isBlank()) {
            return path(directory, output);
        }
        Path main = classOutputs.get(kind);
        if (main == null) {
            if (reloadable.isEmpty()) {
                throw new IllegalStateException("No class output for " + kind + " tests and no reloadable root");
            }
            main = reloadable.getFirst();
        }
        return main.resolveSibling(main.getFileName() + "-test");
    }

    /**
     * The tests seen as a manifest of their own, so that they compile as the application does: its source roots are
     * the test roots, its class outputs, generated sources and options are those of {@code micronaut.dev.test.compile.<kind>.*},
     * its compile classpath is {@code micronaut.dev.test.compile-classpath}, or else the application's, with the
     * application's class outputs added, and its processor path is {@code micronaut.dev.test.processor-path}, or else
     * the application's. A test language without an output of its own writes to {@code <output>-test} beside the
     * application's.
     *
     * @return The manifest of the tests
     */
    public DevManifest testView() {
        Properties view = new Properties();
        for (String name : properties.stringPropertyNames()) {
            String key = name.substring(name.startsWith(PREFIX) ? PREFIX.length() : 0);
            boolean application = key.startsWith("sources.") || key.startsWith("resources.") || key.equals("compile-classpath")
                || key.equals("processor-path") || (key.startsWith("compile.") && key.indexOf('.', "compile.".length()) > 0);
            if (name.startsWith(PREFIX) && !application && !key.startsWith(TEST)) {
                view.setProperty(name, properties.getProperty(name));
            }
        }
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(PREFIX + TEST + "sources.") || name.startsWith(PREFIX + TEST + "resources.") || name.startsWith(PREFIX + TEST + "compile.")) {
                view.setProperty(PREFIX + name.substring((PREFIX + TEST).length()), properties.getProperty(name));
            }
        }
        List<String> classpath = new ArrayList<>();
        String testClasspath = properties.getProperty(PREFIX + TEST + "compile-classpath");
        for (Path entry : testClasspath != null ? paths(directory, testClasspath) : compileClasspath) {
            classpath.add(entry.toString());
        }
        for (SourceRoot root : sourceRoots) {
            String output = classOutput(root.kind()).toString();
            if (!classpath.contains(output)) {
                classpath.add(output);
            }
        }
        view.setProperty(PREFIX + "compile-classpath", String.join(java.io.File.pathSeparator, classpath));
        String testProcessors = properties.getProperty(PREFIX + TEST + "processor-path");
        List<String> processors = new ArrayList<>();
        for (Path entry : testProcessors != null ? paths(directory, testProcessors) : processorPath) {
            processors.add(entry.toString());
        }
        view.setProperty(PREFIX + "processor-path", String.join(java.io.File.pathSeparator, processors));
        for (SourceRoot root : testSourceRoots) {
            view.setProperty(PREFIX + "compile." + root.kind().name().toLowerCase(Locale.ROOT) + ".output", testClassOutput(root.kind()).toString());
        }
        view.setProperty(PREFIX + "mode", DevMode.RUN.name().toLowerCase(Locale.ROOT));
        view.setProperty(PREFIX + "main-class", mainClass.isEmpty() ? "tests" : mainClass);
        return new DevManifest(directory, view);
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(PREFIX + key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The manifest lacks " + PREFIX + key);
        }
        return value.trim();
    }

    /**
     * A path the LiveReload server serves a page at: with a leading and a trailing slash, of plain segments, and
     * neither the root nor a path of the server's own.
     */
    private static String servedPath(String value) {
        String path = value.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        if (!path.matches("(/[A-Za-z0-9._~-]+)+/") || path.contains("/./") || path.contains("/../")) {
            throw new IllegalArgumentException("Not a path to serve the test report at: " + value + " (as /tests/, of plain segments)");
        }
        if (path.startsWith("/livereload/") || path.startsWith("/livereload.js") || path.startsWith("/micronaut-dev/")) {
            throw new IllegalArgumentException("The path " + value + " is the LiveReload server's own: choose another for the test report");
        }
        return path;
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

    static Duration duration(String key, @Nullable String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        try {
            Duration duration;
            if (trimmed.endsWith("ms")) {
                duration = Duration.ofMillis(Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim()));
            } else if (trimmed.endsWith("s")) {
                duration = Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
            } else if (trimmed.endsWith("m")) {
                duration = Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
            } else {
                duration = Duration.ofSeconds(Long.parseLong(trimmed));
            }
            if (duration.isNegative()) {
                throw new IllegalArgumentException("Invalid " + key + ": " + value.trim() + ", a duration such as 30s or 500ms");
            }
            return duration;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + key + ": " + value.trim() + ", a duration such as 30s or 500ms", e);
        }
    }

    static int maxGenerations(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return NativeImageUtils.inImageRuntimeCode() ? NATIVE_MAX_GENERATIONS : 0;
        }
        String trimmed = value.trim();
        if ("unlimited".equalsIgnoreCase(trimmed)) {
            return 0;
        }
        int budget;
        try {
            budget = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + MAX_GENERATIONS + ": " + trimmed + ", a number of generations or unlimited", e);
        }
        if (budget < 0) {
            throw new IllegalArgumentException("Invalid " + MAX_GENERATIONS + ": " + trimmed + ", a number of generations or unlimited");
        }
        return budget;
    }

    private static List<SourceRoot> sourceRoots(Path directory, Properties properties) {
        return sourceRoots(directory, properties, "sources.");
    }

    private static List<SourceRoot> sourceRoots(Path directory, Properties properties, String prefix) {
        List<SourceRoot> roots = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(PREFIX + prefix)) {
                String kindName = name.substring((PREFIX + prefix).length());
                SourceKind kind = SourceKind.of(kindName).orElseThrow(() -> new IllegalArgumentException("Unknown source kind in " + name));
                for (Path path : paths(directory, properties.getProperty(name))) {
                    roots.add(new SourceRoot(kind, path));
                }
            }
        }
        return Collections.unmodifiableList(roots);
    }

    private static List<ResourceRoot> resourceRoots(Path directory, Properties properties) {
        return resourceRoots(directory, properties, "resources.");
    }

    private static List<ResourceRoot> resourceRoots(Path directory, Properties properties, String prefix) {
        List<ResourceRoot> roots = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(PREFIX + prefix)) {
                String kindName = name.substring((PREFIX + prefix).length()).toUpperCase(Locale.ROOT);
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
     * @param port The port the LiveReload server listens on
     * @param injectScript Whether the client script is added to HTML responses
     */
    public record LiveReload(int port, boolean injectScript) {
    }
}
