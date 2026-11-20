package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first generation of an application that does blocking work on Reactor's {@code boundedElastic} scheduler, and
 * work on its {@code parallel} and {@code single} schedulers, is collected after a restart: the threads these shared
 * schedulers create while the first generation runs, and the evictor {@code boundedElastic} starts, have the
 * parent tier's loader as their context class loader, not the first generation's, and work on them sees the classes
 * of the current generation.
 * <p>The application runs in a JVM of its own: Reactor's shared schedulers must not have been created there by any
 * other test.</p>
 */
class ReactorGenerationMemoryTest {

    private static final String PROBE = "probe: ";
    private static final String COLLECTED = PROBE + "generation 1 collected";

    @TempDir
    Path project;

    @Test
    void theFirstGenerationOfAReactorApplicationIsCollectedAfterARestart() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + System.getProperty("java.class.path").replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, "-Xmx512m"));
        String dump = System.getProperty("probe.dump");
        if (dump != null) {
            // a heap dump of the probe when generation 1 stays reachable, to find what keeps it
            command.add("-Dprobe.dump=" + dump);
        }
        command.add(Probe.class.getName());
        command.add(project.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                output.append(e);
            }
        });
        reader.start();
        boolean exited = process.waitFor(4, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        reader.join(10_000);
        String tail = output.length() > 6000 ? output.substring(output.length() - 6000) : output.toString();
        // what the probe reports, ahead of the tail of the application's log
        String report = output.toString().lines().filter(line -> line.startsWith(PROBE)).collect(Collectors.joining("\n"));
        tail = report + "\n" + tail;
        assertTrue(exited, "the probe did not finish:\n" + tail);
        assertTrue(output.toString().contains(COLLECTED), tail);
        assertEquals(0, process.exitValue(), tail);
    }

    /**
     * Runs a Reactor application in development mode, restarts it twice and reports whether its first generation was
     * collected.
     */
    public static final class Probe {

        public static void main(String[] args) throws Exception {
            Path project = Path.of(args[0]);
            int status;
            try {
                status = run(project);
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
                            .properties(java.util.Map.of("spec.name", "ReactorGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Files.writeString(src.resolve("BlockingWork.java"), """
                package app;
                import reactor.core.publisher.Mono;
                import reactor.core.scheduler.Schedulers;
                @io.micronaut.context.annotation.Context
                public class BlockingWork {
                    public BlockingWork() {
                        // a task of a class of the generation on each shared scheduler, on the generation's thread
                        Mono.fromCallable(() -> { Thread.sleep(10); return 1; }).subscribeOn(Schedulers.boundedElastic()).block();
                        Mono.fromCallable(() -> 2).subscribeOn(Schedulers.parallel()).block();
                        Mono.fromCallable(() -> 3).subscribeOn(Schedulers.single()).block();
                        // work on a shared scheduler sees, through its context class loader, the current generation's classes
                        Class<?> seen = Mono.fromCallable(() -> Class.forName(BlockingWork.class.getName(), false, Thread.currentThread().getContextClassLoader()))
                            .subscribeOn(Schedulers.boundedElastic()).block();
                        if (seen != BlockingWork.class) {
                            throw new IllegalStateException("a boundedElastic thread sees " + seen + " of " + seen.getClassLoader());
                        }
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
            try {
                WeakReference<ClassLoader> first = firstLoader(runtime);
                runtime.restart();
                runtime.awaitGeneration(2, Duration.ofMinutes(2));
                runtime.restart();
                runtime.awaitGeneration(3, Duration.ofMinutes(2));
                long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (first.get() != null && System.nanoTime() < deadline) {
                    System.gc();
                    Thread.sleep(200);
                }
                if (first.get() == null) {
                    System.out.println(COLLECTED);
                    return 0;
                }
                ClassLoader kept = first.get();
                for (Thread thread : Thread.getAllStackTraces().keySet()) {
                    if (thread.getContextClassLoader() == kept) {
                        System.out.println(PROBE + "thread with generation 1's loader: " + thread.getName());
                    }
                }
                kept = null;
                String dump = System.getProperty("probe.dump");
                if (dump != null) {
                    java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class).dumpHeap(dump, true);
                }
                System.out.println(PROBE + "generation 1 is still reachable");
                return 1;
            } finally {
                runtime.close();
            }
        }

        /**
         * A weak reference to the first generation's loader, taken in a frame of its own so that no local keeps it.
         */
        private static WeakReference<ClassLoader> firstLoader(DevRuntime runtime) throws ClassNotFoundException {
            ApplicationContext context = runtime.context().orElseThrow();
            if (!context.isRunning() || !context.containsBean(context.getClassLoader().loadClass("app.BlockingWork"))) {
                throw new IllegalStateException("the first generation did not do its work");
            }
            return new WeakReference<>(context.getClassLoader());
        }
    }
}
