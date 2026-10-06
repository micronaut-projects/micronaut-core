package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * A bean retained across restarts that started threads as the first generation created it, as a connection pool starts
 * its housekeeper, keeps them running, and the threads, which took the first generation's loader as their context
 * class loader, no longer keep that generation once a restart retired it.
 * <p>The application runs in a JVM of its own, where no other test started threads from a generation.</p>
 */
class ThreadGenerationMemoryTest {

    private static final String PASSED = "the housekeeper was retained, its threads run, and generation 1 collected";

    @TempDir
    Path project;

    @Test
    void theThreadsOfARetainedBeanDoNotKeepTheFirstGeneration() throws Exception {
        ProbeJvm.run(Probe.class, project, PASSED);
    }

    /**
     * Runs an application with a retained housekeeper in development mode, restarts it twice and reports whether the
     * housekeeper and its threads survived and whether the first generation was collected.
     */
    public static final class Probe {

        public static void main(String[] args) throws Exception {
            int status;
            try {
                status = run(Path.of(args[0]));
            } catch (Throwable e) {
                e.printStackTrace(System.out);
                status = 2;
            }
            System.out.flush();
            System.exit(status);
        }

        private static int run(Path project) throws Exception {
            Path src = Files.createDirectories(project.resolve("src/main/java/app"));
            Files.writeString(src.resolve("Application.java"), """
                package app;
                public class Application {
                    public static void main(String[] args) {
                        io.micronaut.runtime.Micronaut.build(args)
                            .properties(java.util.Map.of("spec.name", "ThreadGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
            Path manifestFile = project.resolve("dev.properties");
            Files.writeString(manifestFile, """
                micronaut.dev.main-class=app.Application
                micronaut.dev.strategy=restart
                micronaut.dev.reloadable=build/classes
                micronaut.dev.compile-classpath=@cp.argfile
                micronaut.dev.processor-path=@cp.argfile
                micronaut.dev.sources.java=src/main/java
                micronaut.dev.compile.java.output=build/classes
                micronaut.dev.patch-in-place=false
                """);
            DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
            Housekeeper housekeeper;
            WeakReference<ClassLoader> first;
            try {
                first = firstLoader(runtime);
                housekeeper = runtime.context().orElseThrow().getBean(Housekeeper.class);
                awaitTicks(housekeeper);
                if (!(housekeeper.thread().getContextClassLoader() instanceof GenerationClassLoader)) {
                    System.out.println("the housekeeper's thread did not start with the first generation's loader: the probe proves nothing");
                    return 1;
                }
                for (int generation = 2; generation <= 3; generation++) {
                    runtime.restart();
                    runtime.awaitGeneration(generation, Duration.ofMinutes(2));
                    if (runtime.context().orElseThrow().getBean(Housekeeper.class) != housekeeper) {
                        System.out.println("generation " + generation + " has a new housekeeper");
                        return 1;
                    }
                }
                awaitTicks(housekeeper);
                if (Housekeeper.CREATED.get() != 1 || !housekeeper.thread().isAlive() || !housekeeper.schedulerThread().isAlive()) {
                    System.out.println("the housekeeper was created " + Housekeeper.CREATED.get() + " time(s), its threads alive: "
                        + housekeeper.thread().isAlive() + ", " + housekeeper.schedulerThread().isAlive());
                    return 1;
                }
                if (!ProbeJvm.collected(first)) {
                    System.out.println("generation 1 is still reachable");
                    return 1;
                }
            } finally {
                runtime.close();
            }
            if (!housekeeper.isClosed() || housekeeper.thread().isAlive()) {
                System.out.println("the housekeeper was not closed with the last generation");
                return 1;
            }
            System.out.println(PASSED);
            return 0;
        }

        /**
         * Waits until the housekeeper's scheduled task ran again, on the thread its executor started.
         */
        private static void awaitTicks(Housekeeper housekeeper) throws InterruptedException {
            long seen = housekeeper.ticks();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while ((housekeeper.ticks() <= seen + 1 || housekeeper.schedulerThread() == null) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            if (housekeeper.ticks() <= seen + 1) {
                throw new IllegalStateException("the housekeeper's scheduled task does not run");
            }
        }

        /**
         * A weak reference to the first generation's loader, taken in a frame of its own so that no local keeps it.
         */
        private static WeakReference<ClassLoader> firstLoader(DevRuntime runtime) {
            ApplicationContext context = runtime.context().orElseThrow();
            if (!context.isRunning()) {
                throw new IllegalStateException("the first generation did not start");
            }
            return new WeakReference<>(context.getClassLoader());
        }
    }
}
