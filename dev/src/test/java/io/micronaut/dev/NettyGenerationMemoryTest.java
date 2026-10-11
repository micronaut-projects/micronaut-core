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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first generation of an application that runs the Netty server is collected after a restart: what Netty
 * initializes once per JVM, such as the exception {@code PlatformDependent} keeps for why {@code Unsafe} is not
 * used, is created on the launcher's thread, not on the first generation's, whose classes its stack trace would keep.
 * <p>The application runs in a JVM of its own: Netty must not have been initialized there by any other test.</p>
 */
class NettyGenerationMemoryTest {

    private static final String COLLECTED = "generation 1 collected";

    @TempDir
    Path project;

    @Test
    void theFirstGenerationOfANettyApplicationIsCollectedAfterARestart() throws Exception {
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
        assertTrue(exited, "the probe did not finish:\n" + tail);
        assertTrue(output.toString().contains(COLLECTED), tail);
        assertEquals(0, process.exitValue(), tail);
    }

    /**
     * Runs a Netty application in development mode, restarts it twice and reports whether its first generation was
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
                            .properties(java.util.Map.of("spec.name", "NettyGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Files.writeString(src.resolve("HelloController.java"), """
                package app;
                @io.micronaut.http.annotation.Controller("/hello")
                public class HelloController {
                    @io.micronaut.http.annotation.Get(produces = "text/plain")
                    public String hello() { return "hello"; }
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
                String dump = System.getProperty("probe.dump");
                if (dump != null) {
                    java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class).dumpHeap(dump, true);
                }
                System.out.println("generation 1 is still reachable");
                return 1;
            } finally {
                runtime.close();
            }
        }

        /**
         * A weak reference to the first generation's loader, taken in a frame of its own so that no local keeps it.
         */
        private static WeakReference<ClassLoader> firstLoader(DevRuntime runtime) throws InterruptedException {
            ApplicationContext context = runtime.context().orElseThrow();
            // the server binds a moment after the context started
            io.micronaut.runtime.server.EmbeddedServer server = context.getBean(io.micronaut.runtime.server.EmbeddedServer.class);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (!server.isRunning() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            if (!context.isRunning() || !server.isRunning()) {
                throw new IllegalStateException("the first generation did not start its server");
            }
            return new WeakReference<>(context.getClassLoader());
        }
    }
}
