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
package io.micronaut.dev.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * A small project run through the development runtime, in this JVM, so that a test can edit a
 * source, reload, and assert over the generation that follows. The classpath of the test is the
 * runtime classpath of the project, so the module under test is parent-tier, as it is for an
 * application, and the sources written here are the reloadable tier.
 *
 * <p>The project is written into a directory the test provides: Java sources under
 * {@code src/main/java}, resources under {@code src/main/resources}, and a {@code main} class the
 * harness generates that starts the application as any application does. The Micronaut annotation
 * processor must be on the test classpath, since the harness compiles the sources with it.</p>
 *
 * <pre>{@code
 * try (ReloadHarness harness = ReloadHarness.inDirectory(tempDir)) {
 *     harness.source("example.Greeter", "package example; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"one\"; } }");
 *     ApplicationContext first = harness.start();
 *     harness.source("example.Greeter", "package example; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"two\"; } }");
 *     ApplicationContext second = harness.reload();
 *     ReloadTck.assertFollowsReload(harness, context -> context.getBean(MyRegistry.class).target());
 * }
 * }</pre>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ReloadHarness implements AutoCloseable {

    /**
     * The name of the generated main class.
     */
    public static final String MAIN_CLASS = "io.micronaut.dev.tck.app.Application";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private final Path project;
    private final Path sources;
    private final Path resources;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Map<String, String> manifest = new LinkedHashMap<>();
    private final Set<String> retain = new LinkedHashSet<>();
    private final List<String> arguments = new ArrayList<>();
    private Duration timeout = DEFAULT_TIMEOUT;
    private int lastGeneration;
    private final Set<Path> written = new LinkedHashSet<>();
    private final Set<Path> removed = new LinkedHashSet<>();
    @Nullable
    private DevRuntime runtime;

    private ReloadHarness(Path project) {
        this.project = project.toAbsolutePath().normalize();
        this.sources = this.project.resolve("src/main/java");
        this.resources = this.project.resolve("src/main/resources");
    }

    /**
     * A harness over a project written into the given directory, empty or not.
     *
     * @param directory The directory, such as a JUnit temporary directory
     * @return The harness
     */
    public static ReloadHarness inDirectory(Path directory) {
        ReloadHarness harness = new ReloadHarness(directory);
        try {
            Files.createDirectories(harness.sources);
            Files.createDirectories(harness.resources);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the project under " + directory, e);
        }
        return harness;
    }

    /**
     * Writes, or overwrites, a Java source of the reloadable tier. Between {@link #start()} and
     * {@link #reload()} this is the edit the reload compiles.
     *
     * @param className The qualified name of the top-level class the source declares
     * @param source The source
     * @return This harness
     */
    public ReloadHarness source(String className, String source) {
        Path file = sources.resolve(className.replace('.', File.separatorChar) + ".java");
        write(file, source);
        edited(file, false);
        return this;
    }

    /**
     * Deletes a Java source of the reloadable tier.
     *
     * @param className The qualified name of the top-level class the source declares
     * @return This harness
     */
    public ReloadHarness deleteSource(String className) {
        Path file = sources.resolve(className.replace('.', File.separatorChar) + ".java");
        try {
            Files.deleteIfExists(file);
            edited(file, true);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the source of " + className, e);
        }
        return this;
    }

    /**
     * Writes, or overwrites, a resource of the reloadable tier.
     *
     * @param path The path relative to the resources directory
     * @param content The content
     * @return This harness
     */
    public ReloadHarness resource(String path, String content) {
        Path file = resources.resolve(path);
        write(file, content);
        edited(file, false);
        return this;
    }

    /**
     * A configuration property of the application, written to its {@code application.properties}.
     *
     * @param key The key
     * @param value The value
     * @return This harness
     */
    public ReloadHarness property(String key, String value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Types whose singletons are retained across a reload, as the manifest's {@code micronaut.dev.retain}.
     *
     * @param types The qualified names
     * @return This harness
     */
    public ReloadHarness retain(String... types) {
        retain.addAll(List.of(types));
        return this;
    }

    /**
     * An entry of the manifest, for what the harness does not set itself.
     *
     * @param key The key, with or without the {@code micronaut.dev.} prefix
     * @param value The value
     * @return This harness
     */
    public ReloadHarness manifest(String key, String value) {
        manifest.put(key.startsWith("micronaut.dev.") ? key : "micronaut.dev." + key, value);
        return this;
    }

    /**
     * The arguments the application's {@code main} receives.
     *
     * @param args The arguments
     * @return This harness
     */
    public ReloadHarness arguments(String... args) {
        arguments.addAll(List.of(args));
        return this;
    }

    /**
     * How long a start or a reload may take.
     *
     * @param timeout The timeout
     * @return This harness
     */
    public ReloadHarness timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Writes the main class, the properties and the manifest, compiles the sources, and starts the
     * application on generation one.
     *
     * @return The context of generation one
     */
    public ApplicationContext start() {
        if (runtime != null) {
            throw new IllegalStateException("The harness is started");
        }
        source(MAIN_CLASS, """
            package io.micronaut.dev.tck.app;

            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).mainClass(Application.class).start();
                }
            }
            """);
        writeProperties(resources.resolve("application.properties"), properties);
        // what a previous run of this directory left: the sources just written are what starts
        deleteRecursively(project.resolve("build"));
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Path argfile = project.resolve("classpath.argfile");
        try {
            Files.write(argfile, classpath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + argfile, e);
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("micronaut.dev.main-class", MAIN_CLASS);
        entries.put("micronaut.dev.strategy", "restart");
        entries.put("micronaut.dev.reloadable", "build/classes");
        entries.put("micronaut.dev.resources.config", "src/main/resources");
        entries.put("micronaut.dev.compile-classpath", "@classpath.argfile");
        entries.put("micronaut.dev.processor-path", "@classpath.argfile");
        entries.put("micronaut.dev.sources.java", "src/main/java");
        entries.put("micronaut.dev.compile.java.output", "build/classes");
        entries.put("micronaut.dev.compile.java.generated-sources", "build/generated");
        if (!retain.isEmpty()) {
            entries.put("micronaut.dev.retain", String.join(",", retain));
        }
        entries.putAll(manifest);
        Path manifestFile = project.resolve("dev.properties");
        writeProperties(manifestFile, entries);
        written.clear();
        removed.clear();
        // a launch that outlives the timeout is closed when it completes: a runtime registers itself with the
        // process, and one left running would refuse the next harness
        DevRuntime started = timed("The application did not start", () -> new MicronautDevMain().launch(DevManifest.load(manifestFile), arguments.toArray(String[]::new)), DevRuntime::close);
        runtime = started;
        lastGeneration = started.generation();
        return started.context().orElseThrow(() -> new AssertionError("The application did not start"));
    }

    /**
     * Applies what was written or deleted through the harness since the last generation: the sources are
     * compiled and the application restarted when a class changed, and a resource change reaches the
     * watches and the configuration refresh. The files are reported to the runtime as a batch of their
     * own, so nothing waits on the watcher, which may have seen them first; either way the context
     * returned is the one running once every pending batch is applied. An edit that changed no class,
     * an identical rewrite for one, restarts nothing, and the current context is returned. With nothing
     * written, every source is compiled, as the runtime's manual trigger does.
     *
     * @return The context running after the reload
     * @throws AssertionError if the compilation failed, with its diagnostics, or the new generation did not start in time
     */
    public ApplicationContext reload() {
        DevRuntime current = runtime();
        int before = lastGeneration;
        Set<Path> changed = Set.copyOf(written);
        Set<Path> deleted = Set.copyOf(removed);
        written.clear();
        removed.clear();
        // a batch of its own, queued after any the watcher enqueued for the edit, and awaited
        timed("The reload did not complete", () -> {
            if (changed.isEmpty() && deleted.isEmpty()) {
                current.reload();
            } else {
                current.changed(changed, deleted);
            }
            return null;
        }, late -> { });
        current.lastFailure().ifPresent(failure -> {
            throw new AssertionError("The reload did not compile:\n" + failure.describe());
        });
        int now = current.generation();
        lastGeneration = now;
        if (now == before) {
            return context();
        }
        try {
            return current.awaitGeneration(now, timeout);
        } catch (TimeoutException e) {
            throw new AssertionError("Generation " + now + " did not start within " + timeout, e);
        }
    }

    /**
     * @return The runtime
     * @throws IllegalStateException if the harness is not started
     */
    public DevRuntime runtime() {
        DevRuntime current = runtime;
        if (current == null) {
            throw new IllegalStateException("The harness is not started");
        }
        return current;
    }

    /**
     * @return The context of the current generation
     */
    public ApplicationContext context() {
        return runtime().context().orElseThrow(() -> new AssertionError("No context is running"));
    }

    /**
     * @return The current generation, one after the start
     */
    public int generation() {
        return runtime().generation();
    }

    /**
     * @return The project directory
     */
    public Path projectDirectory() {
        return project;
    }

    @Override
    public void close() {
        DevRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.close();
        }
    }

    private void edited(Path file, boolean deleted) {
        if (runtime == null) {
            // before the start everything is compiled anyway
            return;
        }
        if (deleted) {
            written.remove(file);
            removed.add(file);
        } else {
            removed.remove(file);
            written.add(file);
        }
    }

    /**
     * Runs a call of the runtime under the harness's timeout: the runtime has waits of its own, longer
     * than a test wants to hang for when the application does not come up. A call that outlives the
     * timeout keeps running on its thread; what it eventually returns goes to the given consumer.
     */
    private <T> T timed(String what, java.util.concurrent.Callable<T> call, java.util.function.Consumer<T> late) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "micronaut-dev-tck");
            thread.setDaemon(true);
            return thread;
        });
        java.util.concurrent.CompletableFuture<T> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return call.call();
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
        try {
            return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.thenAccept(late);
            throw new AssertionError(what + " within " + timeout, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.CompletionException completion && completion.getCause() != null) {
                cause = completion.getCause();
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting: " + what, e);
        } finally {
            // the thread finishes what it runs; nothing new is accepted
            executor.shutdown();
        }
    }

    private static void writeProperties(Path file, Map<String, String> entries) {
        Properties properties = new Properties();
        properties.putAll(entries);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                // the escaping of the format, so that a backslash, a line break or a character beyond Latin-1 survives
                properties.store(out, null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static void deleteRecursively(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + directory, e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
