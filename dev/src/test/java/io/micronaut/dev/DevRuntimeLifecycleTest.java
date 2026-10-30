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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    @Test
    void closingWhileAReloadLaunchesWaitsForTheGenerationAndRefusesTheContextItsMainCreates() throws Exception {
        Files.createDirectories(project.resolve("build/classes"));
        DevManifest manifest = manifest("");
        CountDownLatch secondMain = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> refused = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        DevRuntime runtime = runtime(manifest, (loader, mainClass, args) -> {
            if (launches.incrementAndGet() == 2) {
                // the second generation's main is still configuring as the runtime closes
                secondMain.countDown();
                release.await();
                try {
                    startContext(loader, "DevRuntimeLifecycleTest");
                } catch (RuntimeException e) {
                    refused.set(e);
                    throw e;
                }
                return;
            }
            startContext(loader, "DevRuntimeLifecycleTest");
        });
        ApplicationContext first = runtime.start(new String[0]);
        Thread restarting = new Thread(runtime::restart);
        restarting.start();
        assertTrue(secondMain.await(1, TimeUnit.MINUTES));
        Thread closing = new Thread(runtime::close);
        closing.start();
        // the reload is interrupted, and close waits for the main of the generation it launched
        closing.join(500);
        assertTrue(closing.isAlive());
        assertSame(runtime, DevRuntime.current());
        release.countDown();
        closing.join(TimeUnit.MINUTES.toMillis(1));
        restarting.join(TimeUnit.MINUTES.toMillis(1));
        assertFalse(closing.isAlive());
        assertFalse(restarting.isAlive());
        assertNull(DevRuntime.current());
        assertFalse(first.isRunning());
        Throwable failure = refused.get();
        assertNotNull(failure);
        assertTrue(causes(failure).stream().anyMatch(cause -> String.valueOf(cause.getMessage()).contains("development runtime is closed")), String.valueOf(failure));
    }

    @Test
    void aGenerationAdoptsTheFirstContextItsMainCreatesAndARestartStopsThatOne() throws Exception {
        Files.createDirectories(project.resolve("build/classes"));
        List<ApplicationContext> clients = new CopyOnWriteArrayList<>();
        DevRuntime runtime = runtime(manifest(""), (loader, mainClass, args) -> {
            startContext(loader, "DevRuntimeLifecycleTest");
            // a client's context, built in main after the application's, carries the runtime as a bean too
            clients.add(startContext(loader, "DevRuntimeLifecycleTest"));
        });
        try {
            ApplicationContext application = runtime.start(new String[0]);
            // main builds the client once the application's context started
            awaitSize(clients, 1);
            assertSame(application, runtime.context().orElseThrow());
            ApplicationContext client = clients.get(0);
            assertNotSame(client, application);

            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(1));
            awaitSize(clients, 2);
            assertSame(second, runtime.context().orElseThrow());
            assertNotSame(clients.get(1), second);
            assertFalse(application.isRunning());
            assertTrue(client.isRunning(), "the restart stopped the client's context rather than the application's");
        } finally {
            runtime.close();
            clients.forEach(ApplicationContext::close);
        }
    }

    @Test
    void aReloadRequestedOnTheThreadsTheReloadWaitsForFailsFast() throws Exception {
        Files.createDirectories(project.resolve("build/classes"));
        AtomicReference<DevRuntime> self = new AtomicReference<>();
        AtomicReference<Throwable> onMain = new AtomicReference<>();
        ReentrantReloadListener.OUTCOME.set(null);
        DevRuntime runtime = runtime(manifest(""), (loader, mainClass, args) -> {
            try {
                // the generation's main, whose context the reload waits for
                self.get().reload();
            } catch (IllegalStateException e) {
                onMain.compareAndSet(null, e);
            }
            startContext(loader, "DevRuntimeLifecycleTest.reentrant");
        });
        self.set(runtime);
        try {
            runtime.start(new String[0]);
            assertTrue(String.valueOf(onMain.get()).contains("which the reload waits for"), String.valueOf(onMain.get()));

            long start = System.nanoTime();
            // a reload listener runs on the reload thread: its own restart is refused rather than waited for
            runtime.restart();
            runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMinutes(1)) < 0);
            Throwable outcome = ReentrantReloadListener.OUTCOME.get();
            assertTrue(outcome instanceof IllegalStateException && outcome.getMessage().contains("micronaut-dev-reload"), String.valueOf(outcome));
            assertEquals(2, runtime.generation());
        } finally {
            runtime.close();
        }
    }

    private static ApplicationContext startContext(ClassLoader loader, String specName) {
        return ApplicationContext.builder()
            .classLoader(loader)
            .properties(Map.of("spec.name", specName))
            .start();
    }

    private static void awaitSize(List<?> list, int size) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        while (list.size() < size && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(size, list.size());
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = failure; cause != null && !causes.contains(cause); cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
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
