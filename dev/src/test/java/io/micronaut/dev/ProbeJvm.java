package io.micronaut.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a probe's {@code main} in a JVM of its own, on the test classpath, where nothing the probe looks at was
 * initialized by another test, and checks that it reported what it expected.
 */
final class ProbeJvm {

    private ProbeJvm() {
    }

    /**
     * Runs the probe and checks that it printed the expected line and exited with 0.
     *
     * @param probe The class whose main runs
     * @param project The probe's project directory, its first argument
     * @param expected The line the probe prints when what it checks holds
     * @throws Exception if the probe cannot run
     */
    static void run(Class<?> probe, Path project, String expected) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + System.getProperty("java.class.path").replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, "-Xmx512m"));
        String dump = System.getProperty("probe.dump");
        if (dump != null) {
            // a heap dump of the probe when generation 1 stays reachable, to find what keeps it
            command.add("-Dprobe.dump=" + dump);
        }
        command.add(probe.getName());
        command.add(project.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                output.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                output.append(e);
            }
        });
        reader.start();
        boolean exited = process.waitFor(4, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        reader.join(10_000);
        // in the test report, to see what the probe did
        System.out.println(output);
        String tail = output.length() > 6000 ? output.substring(output.length() - 6000) : output.toString();
        assertTrue(exited, "the probe did not finish:\n" + tail);
        assertTrue(output.toString().contains(expected), tail);
        assertEquals(0, process.exitValue(), tail);
    }

    /**
     * Collects garbage until the reference is cleared, for up to a minute, and dumps the heap to the
     * {@code probe.dump} file when it is not.
     *
     * @param reference The reference to the first generation's loader
     * @return Whether it was cleared
     * @throws Exception if the wait is interrupted or the heap cannot be dumped
     */
    static boolean collected(java.lang.ref.WeakReference<?> reference) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(200);
        }
        if (reference.get() == null) {
            return true;
        }
        String dump = System.getProperty("probe.dump");
        if (dump != null) {
            java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class).dumpHeap(dump, true);
        }
        return false;
    }
}
