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
package io.micronaut.dev.compile;

import io.micronaut.dev.DevRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded Kotlin compiler needs Kotlin 2.4: an older one on the launch classpath makes the compiler
 * unavailable, with a warning naming both versions, rather than fail to link at the first compilation. The
 * probe runs in a JVM of its own, with Kotlin 2.3 on the classpath in place of the test classpath's.
 */
class KotlinVersionRequirementTest {

    private static final String UNSUPPORTED_CLASSPATH = "micronaut.dev.test.unsupported-kotlin-classpath";
    private static final String PROBE = "probe: ";

    @TempDir
    Path project;

    @Test
    void theVersionDecision() {
        for (String older : List.of("1.9.25", "2.2.21-release-469", "2.3.0", "2.3.21", "2.3.21-release-298")) {
            assertFalse(KotlinSourceCompiler.isSupported(older), older);
        }
        // what cannot be read is left to the compiler rather than refused
        for (String supported : List.of("2.4.0-Beta1", "2.4.0", "2.4.10-release-377", "2.10.0", "3.0.0", "snapshot")) {
            assertTrue(KotlinSourceCompiler.isSupported(supported), supported);
        }
    }

    @Test
    void theMessageNamesTheVersionFoundAndTheVersionNeeded() {
        String message = KotlinSourceCompiler.unsupportedMessage("2.3.21");
        assertTrue(message.contains("Kotlin 2.3.21"), message);
        assertTrue(message.contains("2.4"), message);
    }

    @Test
    void theKotlinOfTheTestClasspathIsSupported() {
        assertTrue(new KotlinSourceCompiler().isAvailable());
    }

    @Test
    void anOlderKotlinMakesTheCompilerUnavailableWithAWarning() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // the test classpath without its own Kotlin compiler, then the older one
        String classpath = Stream.concat(
            Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> {
                    String name = Path.of(entry).getFileName().toString();
                    return !name.startsWith("kotlin-") && !name.startsWith("symbol-processing");
                }),
            Stream.of(System.getProperty(UNSUPPORTED_CLASSPATH))
        ).collect(Collectors.joining(File.pathSeparator));
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + classpath.replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, Probe.class.getName()));
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
        boolean exited = process.waitFor(2, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        reader.join(10_000);
        String all = output.toString();
        assertTrue(exited, "the probe did not finish:\n" + all);
        assertEquals(0, process.exitValue(), all);
        assertTrue(all.contains(PROBE + "available=false"), all);
        assertTrue(all.lines().anyMatch(line -> line.startsWith(PROBE + "compilers=") && !line.contains("KOTLIN")), all);
        String warning = KotlinSourceCompiler.unsupportedMessage("2.3.21");
        assertEquals(1, all.lines().filter(line -> line.contains(warning)).count(), all);
        assertFalse(all.contains("NoClassDefFoundError"), all);
    }

    /**
     * Asks for the compilers as the launcher does, with Kotlin 2.3 on the classpath.
     */
    public static final class Probe {

        public static void main(String[] args) {
            KotlinSourceCompiler compiler = new KotlinSourceCompiler();
            System.out.println(PROBE + "available=" + compiler.isAvailable());
            System.out.println(PROBE + "compilers=" + DevRuntime.availableCompilers().keySet());
        }
    }
}
