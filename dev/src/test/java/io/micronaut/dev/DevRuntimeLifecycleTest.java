package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevRuntimeLifecycleTest {

    @TempDir
    Path project;

    @Test
    void onlyTheGenerationsOlderThanTheToleranceCountAsLeaked() {
        assertEquals(List.of(), GenerationMemory.olderThanTolerance(List.of(), 5));
        assertEquals(List.of(1, 2), GenerationMemory.olderThanTolerance(List.of(1, 2, 3, 4), 5));
        assertEquals(List.of(), GenerationMemory.olderThanTolerance(List.of(1, 2), 3));
    }

    @Test
    void aMissingOutputThatFailsToCompileStopsTheLaunch() throws IOException {
        DevManifest manifest = manifest("micronaut.dev.sources.java=src/main/java\n");
        Files.createDirectories(project.resolve("src/main/java"));
        FailingCompiler compiler = new FailingCompiler();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DevRuntime.compileMissingOutputs(manifest, Map.of(SourceKind.JAVA, compiler)));
        assertTrue(e.getMessage().contains("cannot find symbol"), e.getMessage());
        assertEquals(1, compiler.calls.get());
    }

    @Test
    void theBuildToolCompilesWhatItOwns() throws IOException {
        DevManifest manifest = manifest("""
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.mode=build-tool
            """);
        Files.createDirectories(project.resolve("src/main/java"));
        FailingCompiler compiler = new FailingCompiler();
        DevRuntime.compileMissingOutputs(manifest, Map.of(SourceKind.JAVA, compiler));
        assertEquals(0, compiler.calls.get());
    }

    @Test
    void aMainThatFailsFailsTheStartAndLeavesTheProcessFreeForAnotherRuntime() throws IOException {
        DevManifest manifest = manifest("");
        DevRuntime failing = runtime(manifest, (loader, mainClass, args) -> {
            throw new IOException("no database");
        });
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> failing.start(new String[0]));
        assertTrue(e.getMessage().contains("failed to start"), e.getMessage());
        assertTrue(e.getMessage().contains("no database"), e.getMessage());
        assertNull(DevRuntime.current());

        DevRuntime silent = runtime(manifest, (loader, mainClass, args) -> {
            // returns without starting a context, as a main that logged its own failure does
        });
        e = assertThrows(IllegalStateException.class, () -> silent.start(new String[0]));
        assertTrue(e.getMessage().contains("returned without starting a context"), e.getMessage());
        assertNull(DevRuntime.current());
        assertFalse(silent.context().isPresent());
    }

    @Test
    void aSecondRuntimeIsRefusedAndARestartThatFailsToStartIsRetried() throws Exception {
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve("build/classes"));
        // the build tool compiles: its trigger is watched, not the class output
        DevManifest manifest = manifest("""
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.mode=build-tool
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.build-tool.trigger=build/micronaut-dev/compiled
            """);
        AtomicInteger launches = new AtomicInteger();
        DevRuntime runtime = runtime(manifest, (loader, mainClass, args) -> {
            if (launches.incrementAndGet() == 2) {
                throw new IllegalStateException("broken second generation");
            }
            ApplicationContext.builder()
                .classLoader(loader)
                .properties(Map.of("spec.name", "DevRuntimeLifecycleTest"))
                .start();
        });
        try {
            ApplicationContext first = runtime.start(new String[0]);
            assertSame(runtime, DevRuntime.current());
            assertTrue(first.isRunning());
            assertTrue(Files.isDirectory(project.resolve("build/micronaut-dev")));

            DevRuntime second = runtime(manifest, (loader, mainClass, args) -> { });
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> second.start(new String[0]));
            assertTrue(e.getMessage().contains("already runs this process"), e.getMessage());
            assertSame(runtime, DevRuntime.current());

            // the second generation fails to start: waiting for it says so
            runtime.restart();
            e = assertThrows(IllegalStateException.class, () -> runtime.awaitGeneration(2, Duration.ofMinutes(1)));
            assertTrue(e.getMessage().contains("failed to start"), e.getMessage());
            assertFalse(first.isRunning());

            // the next restart launches again
            runtime.restart();
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(1));
            assertTrue(third.isRunning());
            assertEquals(3, launches.get());
        } finally {
            runtime.close();
        }
        assertNull(DevRuntime.current());
    }

    private DevRuntime runtime(DevManifest manifest, DevRuntime.ApplicationLauncher launcher) {
        DevClassLoader loader = new DevClassLoader(DevRuntimeLifecycleTest.class.getClassLoader(), manifest.reloadableRoots(), project.resolve("build/generations"));
        return new DevRuntime(manifest, loader, launcher, Map.of());
    }

    private DevManifest manifest(String extra) throws IOException {
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            """ + extra);
        return DevManifest.load(manifestFile);
    }

    private static final class FailingCompiler implements SourceCompiler {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Set<SourceKind> kinds() {
            return Set.of(SourceKind.JAVA);
        }

        @Override
        public CompilationResult compile(CompilationRequest request) {
            calls.incrementAndGet();
            return new CompilationResult(CompilationResult.Status.FAILED,
                List.of(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "cannot find symbol", null, 0, 0)), Set.of(), Set.of(), Duration.ZERO);
        }
    }
}
