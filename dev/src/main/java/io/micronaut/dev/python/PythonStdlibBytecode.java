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
package io.micronaut.dev.python;

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * A copy of GraalPy's standard library with its bytecode, which every generation of a Python application reads instead
 * of compiling the modules it imports again: a GraalPy context writes no bytecode, and the standard library it finds
 * has none.
 *
 * <p>The copy is a tree of links to the files of the real standard library, with the {@code __pycache__} directories
 * of its own, in a cache directory of the user. Contexts reach it through {@code python.StdLibHome}, and keep
 * {@code python.DontWriteBytecodeFlag}: only the standard library has bytecode here, so an edited module of the
 * application can never run from a stale file. {@code python.PyCachePrefix} would need no copy, but it moves the
 * bytecode of every module: the bytecode packaged in the virtual file system, of the application and of the runtime
 * module, would no longer be found. Nothing is written beside the real standard library, which production runs of the
 * same GraalPy read too.</p>
 *
 * <p>The first run seeds the tree in the background and the application does not wait for it: a context built before
 * it is complete uses the real standard library. The tree is built under a temporary name and renamed once complete,
 * so that a context never sees half of it, and of two processes seeding at once the first rename wins. A tree is named
 * after the GraalPy version and the location and time of the real standard library. Python checks each bytecode file
 * against the time and size of its source, through the link, so a file that does not match is compiled from its source
 * as it would be without this.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class PythonStdlibBytecode {

    private static final Logger LOG = LoggerFactory.getLogger(PythonStdlibBytecode.class);
    private static final String PYTHON = "python";
    private static final String COMPLETE = ".complete";
    private static final String CURRENT = ".current";
    private static final Duration ABANDONED = Duration.ofHours(1);
    /**
     * A file only present in a usable standard library; a link to it dangles once the real one is gone.
     */
    private static final String PROBE_FILE = "os.py";
    /**
     * Directories whose modules stay without bytecode: tests, tools and rarely used libraries.
     */
    private static final String NOT_COMPILED = "__phello__|_pyrepl|curses|dbm|ensurepip|idlelib|lib2to3|pydoc_data|sqlite3|test|tests|tkinter|turtledemo|venv|wsgiref|xmlrpc";
    /**
     * Directories of the real standard library left out: Python finds its packages from the prefix, which stays.
     */
    private static final Set<String> NOT_LINKED = Set.of("__pycache__", "site-packages");
    private static final String COMPILE = """
        def compile_all(directory, recorded_as, not_compiled):
            import compileall, py_compile, re
            skip = re.compile(re.escape(directory) + r"[/\\\\](?:.*[/\\\\])?(?:" + not_compiled + r")[/\\\\]")
            compileall.compile_dir(directory, ddir=recorded_as, quiet=2, force=True, rx=skip,
                                   invalidation_mode=py_compile.PycInvalidationMode.TIMESTAMP)
        compile_all
        """;

    private final Path root;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile @Nullable Path tree;

    /**
     * @param root The directory of the trees
     */
    PythonStdlibBytecode(Path root) {
        this.root = root;
    }

    /**
     * The standard library for the context being built. The first call looks for the tree of an earlier run and starts
     * seeding in the background, which leaves such a tree alone when it is still the right one.
     *
     * @return The directory for {@code python.StdLibHome}, or null to leave the real standard library in place
     */
    @Nullable Path standardLibrary() {
        if (started.compareAndSet(false, true)) {
            tree = recorded();
            Thread thread = new Thread(this::seed, "micronaut-dev-python-stdlib");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            // not the loader of the generation being started: this thread may outlive it
            thread.setContextClassLoader(PythonStdlibBytecode.class.getClassLoader());
            thread.start();
        }
        Path current = tree;
        // a clean of the build, or of the cache the real standard library lives in, while the application runs
        return current != null && usable(current) ? current : null;
    }

    /**
     * @return The tree an earlier run recorded, if it was seeded by this version of GraalPy and is still usable
     */
    @Nullable Path recorded() {
        try (Engine engine = engine()) {
            Path file = root.resolve(version(engine) + CURRENT);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Path candidate = root.resolve(Files.readString(file).trim());
            return usable(candidate) ? candidate : null;
        } catch (IOException | RuntimeException e) {
            LOG.debug("Not using the recorded compiled standard library of Python", e);
            return null;
        }
    }

    /**
     * Finds the tree of the real standard library, seeding it unless it exists, and uses it from the next context on.
     */
    void seed() {
        try {
            long start = System.nanoTime();
            Path standardLibrary;
            String version;
            try (Context context = context(null)) {
                standardLibrary = Path.of(context.eval(PYTHON, "import sys; sys._stdlib_dir").asString());
                version = version(context.getEngine());
            }
            String name = version + "-" + digest(standardLibrary);
            Path target = root.resolve(name);
            if (!usable(target)) {
                build(standardLibrary, target);
                LOG.debug("Compiled the standard library of Python in {} ms", (System.nanoTime() - start) / 1_000_000);
            }
            record(version, name);
            tree = target;
        } catch (IOException | RuntimeException e) {
            // only slower: the contexts keep compiling what they import
            LOG.debug("Could not compile the standard library of Python", e);
        }
    }

    private void build(Path standardLibrary, Path target) throws IOException {
        Files.createDirectories(root);
        deleteAbandoned(target);
        Path work = Files.createTempDirectory(root, target.getFileName() + ".");
        try {
            link(standardLibrary, work);
            try (Context context = context(work)) {
                context.eval(PYTHON, COMPILE).execute(work.toString(), target.toString(), NOT_COMPILED);
            }
            Files.createFile(work.resolve(COMPLETE));
            if (Files.exists(target) && !usable(target)) {
                // what is left of a tree part of which was removed
                delete(target);
            }
            try {
                Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                if (!usable(target)) {
                    throw e;
                }
                LOG.debug("Another process compiled the standard library of Python first");
            }
        } finally {
            if (Files.exists(work)) {
                delete(work);
            }
        }
    }

    /**
     * What a process killed while it was seeding left behind, which nothing else would ever remove from this directory.
     */
    private void deleteAbandoned(Path target) throws IOException {
        List<Path> abandoned;
        try (Stream<Path> entries = Files.list(root)) {
            abandoned = entries
                .filter(entry -> entry.getFileName().toString().startsWith(target.getFileName() + "."))
                .toList();
        }
        Instant before = Instant.now().minus(ABANDONED);
        for (Path entry : abandoned) {
            if (Files.getLastModifiedTime(entry).toInstant().isBefore(before)) {
                delete(entry);
            }
        }
    }

    private void record(String version, String name) throws IOException {
        Path file = Files.createTempFile(root, version + CURRENT, null);
        Files.writeString(file, name);
        Files.move(file, root.resolve(version + CURRENT), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean usable(Path candidate) {
        return Files.isRegularFile(candidate.resolve(COMPLETE)) && Files.exists(candidate.resolve(PROBE_FILE));
    }

    private static void link(Path standardLibrary, Path tree) throws IOException {
        Files.walkFileTree(standardLibrary, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(standardLibrary)) {
                    return FileVisitResult.CONTINUE;
                }
                if (NOT_LINKED.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectory(tree.resolve(standardLibrary.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path link = tree.resolve(standardLibrary.relativize(file).toString());
                try {
                    Files.createSymbolicLink(link, file);
                } catch (IOException | UnsupportedOperationException e) {
                    // no right to create links, as on Windows by default: a copy reads the same
                    Files.copy(file, link);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The directory for the trees when the manifest names none: where GraalPy's own files are cached, for each platform.
     *
     * @return The directory of the user's caches for the trees
     */
    static Path defaultDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String xdg = System.getenv("XDG_CACHE_HOME");
        String localAppData = System.getenv("LOCALAPPDATA");
        Path caches;
        if (os.contains("mac")) {
            caches = home.resolve("Library").resolve("Caches");
        } else if (os.contains("windows") && localAppData != null) {
            caches = Path.of(localAppData);
        } else if (xdg != null && !xdg.isEmpty()) {
            caches = Path.of(xdg);
        } else {
            caches = home.resolve(".cache");
        }
        return caches.resolve("micronaut").resolve("dev").resolve("python-stdlib");
    }

    private static Context context(@Nullable Path standardLibrary) {
        Context.Builder builder = Context.newBuilder(PYTHON)
            .allowAllAccess(true)
            .allowExperimentalOptions(true)
            .option("engine.WarnInterpreterOnly", Boolean.FALSE.toString())
            .option("python.PosixModuleBackend", "java")
            .out(OutputStream.nullOutputStream())
            .err(OutputStream.nullOutputStream());
        if (standardLibrary != null) {
            builder.option("python.StdLibHome", standardLibrary.toString());
        }
        return builder.build();
    }

    private static Engine engine() {
        return Engine.newBuilder(PYTHON).option("engine.WarnInterpreterOnly", Boolean.FALSE.toString()).build();
    }

    private static String version(Engine engine) {
        return engine.getVersion() + "-" + Objects.requireNonNull(engine.getLanguages().get(PYTHON)).getVersion();
    }

    /**
     * Names a real standard library by where it is and when it was written: extracted again, its sources are newer
     * than the bytecode of a tree, which Python would then ignore, and a new tree is seeded.
     */
    private static String digest(Path standardLibrary) throws IOException {
        String identity = standardLibrary + "\n" + Files.getLastModifiedTime(standardLibrary.resolve(PROBE_FILE)).toMillis();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
