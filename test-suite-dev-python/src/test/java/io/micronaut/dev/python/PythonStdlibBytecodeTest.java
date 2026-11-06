package io.micronaut.dev.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonStdlibBytecodeTest {

    private static final String PYTHON = "python";
    private static final String STDLIB_DIR = "import sys; sys._stdlib_dir";

    @TempDir
    Path root;

    @Test
    void contextsImportTheBytecodeOfTheTreeAndNothingIsWrittenBesideTheRealStandardLibrary() throws Exception {
        PythonStdlibBytecode bytecode = new PythonStdlibBytecode(root);
        bytecode.seed();
        Path tree = bytecode.recorded();
        assertNotNull(tree);

        String real;
        try (Context context = context(null)) {
            real = context.eval(PYTHON, STDLIB_DIR).asString();
        }
        assertFalse(Files.exists(Path.of(real, "json", "__pycache__")), "the real standard library stays as it is");

        try (Context context = context(tree)) {
            assertEquals(tree.toString(), context.eval(PYTHON, STDLIB_DIR).asString());
            assertTrue(context.eval(PYTHON, "import sys; sys.dont_write_bytecode").asBoolean());
            String cached = context.eval(PYTHON, "import json; json.__cached__").asString();
            assertTrue(cached.startsWith(tree.toString()), cached);
            assertTrue(Files.isRegularFile(Path.of(cached)), cached);
            assertFalse(Files.exists(tree.resolve("idlelib").resolve("__pycache__")), "what few applications import stays without bytecode");
            assertTrue(Files.exists(tree.resolve("idlelib").resolve("__init__.py")), "but can still be imported");
            assertTrue(context.eval(PYTHON, "json.loads.__code__.co_filename").asString().startsWith(tree.toString()),
                "the bytecode names the place it is used from, not where it was seeded");
        }
    }

    @Test
    void bytecodeRunsOnlyWhileItMatchesItsSource() throws Exception {
        PythonStdlibBytecode bytecode = new PythonStdlibBytecode(root);
        bytecode.seed();
        Path tree = bytecode.recorded();
        assertNotNull(tree);

        try (Context context = context(tree)) {
            context.eval(PYTHON, """
                import importlib.util, importlib._bootstrap_external as external, os, sys
                source = os.path.join(sys._stdlib_dir, "bisect.py")
                stat = os.stat(source)
                code = compile("FROM_BYTECODE = True", source, "exec")
                with open(importlib.util.cache_from_source(source), "wb") as file:
                    file.write(external._code_to_timestamp_pyc(code, stat.st_mtime, stat.st_size))
                """);
        }
        try (Context context = context(tree)) {
            assertTrue(context.eval(PYTHON, "import bisect; bisect.FROM_BYTECODE").asBoolean(), "bytecode that matches its source is what runs");
        }

        // a source of another time and size than the bytecode says, read through a link as any other
        Path link = tree.resolve("bisect.py");
        String source = Files.readString(link);
        Path changed = root.resolve("bisect-changed.py");
        Files.writeString(changed, source + "\nFROM_SOURCE = True\n");
        Files.delete(link);
        Files.createSymbolicLink(link, changed);
        try (Context context = context(tree)) {
            assertTrue(context.eval(PYTHON, "import bisect; bisect.FROM_SOURCE").asBoolean());
            assertFalse(context.eval(PYTHON, "hasattr(bisect, 'FROM_BYTECODE')").asBoolean());
        }
    }

    @Test
    void twoProcessesSeedingAtOnceEndWithOneCompleteTree() throws Exception {
        PythonStdlibBytecode first = new PythonStdlibBytecode(root);
        PythonStdlibBytecode second = new PythonStdlibBytecode(root);
        CompletableFuture.allOf(CompletableFuture.runAsync(first::seed), CompletableFuture.runAsync(second::seed)).get();

        assertEquals(first.recorded(), second.recorded());
        assertNotNull(first.recorded());
        List<Path> entries;
        try (Stream<Path> list = Files.list(root)) {
            entries = list.toList();
        }
        assertEquals(2, entries.size(), () -> "the tree and the record of it, nothing temporary: " + entries);
    }

    @Test
    void aTreeOfAnotherVersionOrOneRemovedIsNotUsed() throws Exception {
        PythonStdlibBytecode bytecode = new PythonStdlibBytecode(root);
        assertNull(bytecode.recorded(), "nothing is seeded yet");
        bytecode.seed();
        Path tree = bytecode.recorded();
        assertNotNull(tree);

        // what an upgrade of GraalPy finds: the record of the earlier version says nothing of the new one
        Path record;
        try (Stream<Path> list = Files.list(root)) {
            record = list.filter(entry -> entry.getFileName().toString().endsWith(".current")).findFirst().orElseThrow();
        }
        Path other = record.resolveSibling("1.0-1.0.current");
        Files.move(record, other);
        assertNull(bytecode.recorded());
        Files.move(other, record);
        assertEquals(tree, bytecode.recorded());
        Files.delete(tree.resolve(".complete"));
        assertNull(bytecode.recorded());

        // seeding replaces what is left of it
        bytecode.seed();
        assertEquals(tree, bytecode.recorded());
    }

    @Test
    void outsideDevelopmentModeTheContextIsLeftAlone() {
        assertNull(DevRuntime.current());
        String real;
        try (Context context = context(null)) {
            real = context.eval(PYTHON, STDLIB_DIR).asString();
        }
        Context.Builder builder = Context.newBuilder(PYTHON).allowAllAccess(true).allowExperimentalOptions(true);
        new PythonStdlibCustomizer().customize(builder);
        try (Context context = builder.build()) {
            assertEquals(real, context.eval(PYTHON, STDLIB_DIR).asString());
        }
    }

    @Test
    void theGenerationsOfAnApplicationReadTheTreeOnceItIsSeeded() throws Exception {
        PythonFixture fixture = PythonFixture.create(root);
        launch("""
            micronaut.dev.main-class=pyronaut_application.PyronautMain
            micronaut.dev.strategy=restart
            micronaut.dev.patch-in-place=false
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            """, runtime -> {
                Path trees = runtime.manifest().pythonStdlibBytecodeDirectory();
                assertNotNull(trees);
                PythonStdlibBytecode bytecode = new PythonStdlibBytecode(trees);
                long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
                while (bytecode.recorded() == null && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                }
                assertNotNull(bytecode.recorded());

                fixture.writeGreeter("two");
                runtime.reload();
                ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
                assertTrue(stdlibDir(second).startsWith(trees.toString()), stdlibDir(second));
                assertEquals("Hello two", greet(second));
            });
    }

    @Test
    void itCanBeTurnedOff() throws Exception {
        PythonFixture.create(root);
        launch("""
            micronaut.dev.main-class=pyronaut_application.PyronautMain
            micronaut.dev.strategy=restart
            micronaut.dev.python.stdlib-bytecode=false
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            """, runtime -> {
                assertFalse(stdlibDir(runtime.context().orElseThrow()).contains("python-stdlib"));
            });
    }

    private void launch(String manifest, RunningApplication test) throws Exception {
        List<String> classpath = PythonFixture.testClasspath().stream().map(Path::toString).toList();
        Files.write(root.resolve("cp.argfile"), classpath);
        Path manifestFile = root.resolve("dev.properties");
        Files.writeString(manifestFile, manifest);
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            test.run(runtime);
        } finally {
            runtime.close();
        }
    }

    private static String stdlibDir(ApplicationContext application) {
        return application.getBean(Context.class).eval(PYTHON, STDLIB_DIR).asString();
    }

    private static String greet(ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.PythonGreeter");
        return (String) type.getMethod("greet").invoke(context.getBean(type));
    }

    private static Context context(Path standardLibrary) {
        Context.Builder builder = Context.newBuilder(PYTHON)
            .allowAllAccess(true)
            .allowExperimentalOptions(true)
            .option("python.PosixModuleBackend", "java");
        if (standardLibrary != null) {
            builder.option("python.StdLibHome", standardLibrary.toString());
        }
        return builder.build();
    }

    private interface RunningApplication {
        void run(DevRuntime runtime) throws Exception;
    }
}
