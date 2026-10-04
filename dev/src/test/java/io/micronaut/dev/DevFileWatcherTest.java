package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.scheduling.io.watch.FileWatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevFileWatcherTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @TempDir
    Path project;

    @Test
    void aBeanWatchesADirectoryOutsideTheRootsThroughTheEngineWatcherAndLosesItWithItsGeneration() throws Exception {
        Path bundle = Files.createDirectories(project.resolve("build/bundle"));
        DevRuntime runtime = launch(bundle);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            assertInstanceOf(DevFileWatcher.class, first.getBean(FileWatcher.class));
            assertEquals(1, watcherThreads());
            // the directory joins the engine's service: one watcher for the roots and the module
            assertTrue(runtime.watchedRoots().contains(bundle.toAbsolutePath().normalize()));

            Object watching = watching(first);
            Path script = bundle.resolve("server.js");
            Files.writeString(script, "export default 1;");
            awaitSeen(watching, script);

            // the change reaches the bean and nothing else: no compilation, no restart
            Thread.sleep(500);
            assertFalse(runtime.isReloading());
            assertEquals(1, runtime.generation());
            assertTrue(first.isRunning());

            // a restart closes what the retired generation registered; the new generation registers again
            FileWatcher.Registration registration = registration(watching);
            assertTrue(registration.isActive());
            DevFileWatcher firstWatcher = (DevFileWatcher) first.getBean(FileWatcher.class);
            assertEquals(1, firstWatcher.openRegistrations());
            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertFalse(registration.isActive());
            assertFalse(firstWatcher.isRunning());
            assertEquals(0, firstWatcher.openRegistrations());
            Object next = watching(second);
            Path updated = bundle.resolve("client.js");
            Files.writeString(updated, "export default 2;");
            awaitSeen(next, updated);
            assertFalse(seen(watching).contains(updated.toAbsolutePath().normalize()));
            assertEquals(1, watcherThreads());
            assertEquals(2, runtime.generation());
            // the directory was registered once, for the process, and each generation listens over it
            assertEquals(List.of(bundle.toAbsolutePath().normalize()), List.copyOf(runtime.pinnedWatches().keySet()));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aBeanWatchingInsideTheRootsAddsAListenerNotAWatcher() throws Exception {
        Path staticRoot = Files.createDirectories(project.resolve("src/main/resources/static"));
        DevRuntime runtime = launch(staticRoot);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            assertEquals(1, watcherThreads());
            // every watched directory is one of the manifest's roots or below one
            List<Path> roots = List.of(project.resolve("src").toAbsolutePath().normalize(), project.resolve("build/classes").toAbsolutePath().normalize());
            for (Path watched : runtime.watchedRoots()) {
                assertTrue(roots.stream().anyMatch(watched::startsWith), watched + " is outside the roots");
            }
            Path css = staticRoot.resolve("site.css");
            Files.writeString(css, "body {}");
            awaitSeen(watching(first), css);
            assertEquals(1, watcherThreads());
            assertEquals(1, runtime.generation());
        } finally {
            runtime.close();
        }
    }

    private DevRuntime launch(Path watched) throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevFileWatcherTest", "app.watched", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("Watching.java"), """
            package app;
            @io.micronaut.context.annotation.Context
            public class Watching {
                public final java.util.List<java.nio.file.Path> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
                public final io.micronaut.scheduling.io.watch.FileWatcher.Registration registration;
                public Watching(io.micronaut.scheduling.io.watch.FileWatcher watcher, @io.micronaut.context.annotation.Value("${app.watched}") String watched) {
                    registration = watcher.watch(java.nio.file.Path.of(watched), batch -> batch.changes().forEach(change -> seen.add(change.path())));
                }
            }
            """);
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.resources.static=src/main/resources/static
            micronaut.dev.compile.java.output=build/classes
            """);
        return new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {watched.toAbsolutePath().toString()});
    }

    private static Object watching(ApplicationContext context) throws Exception {
        return context.getBean(context.getClassLoader().loadClass("app.Watching"));
    }

    @SuppressWarnings("unchecked")
    private static List<Path> seen(Object watching) throws Exception {
        return (List<Path>) watching.getClass().getField("seen").get(watching);
    }

    private static FileWatcher.Registration registration(Object watching) throws Exception {
        return (FileWatcher.Registration) watching.getClass().getField("registration").get(watching);
    }

    private static void awaitSeen(Object watching, Path file) throws Exception {
        Path expected = file.toAbsolutePath().normalize();
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!seen(watching).contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(seen(watching).contains(expected), "the bean never saw " + expected + ": " + seen(watching));
    }

    private static long watcherThreads() {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .filter(thread -> thread.getName().equals("micronaut-dev-watcher") || thread.getName().startsWith("micronaut-filewatch"))
            .count();
    }
}
