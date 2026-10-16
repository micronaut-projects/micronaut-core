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
package io.micronaut.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application's {@code logback.xml} configures Logback under the launcher, as it does when the application runs on
 * its own: Logback lives in the parent tier, whose own lookup does not see the application's resources. The probe
 * runs in a JVM of its own, without the test resources, whose {@code logback-test.xml} Logback would find first.
 */
class LauncherLogbackTest {

    private static final String PROBE = "probe: ";
    private static final String APPLIED = PROBE + "the application's logback.xml applied";

    @TempDir
    Path project;

    @Test
    void theApplicationsLogbackXmlConfiguresLogbackInTheFirstGenerationAndAfterAChange() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // the test resources hold a logback-test.xml, which Logback's lookup would find ahead of the application's
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(entry -> !Files.isRegularFile(Path.of(entry).resolve("logback-test.xml")))
            .collect(Collectors.joining(File.pathSeparator));
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + classpath.replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, "-Xmx512m", Probe.class.getName(), project.toString()));
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
        String all = output.toString();
        String tail = all.length() > 6000 ? all.substring(all.length() - 6000) : all;
        String report = all.lines().filter(line -> line.startsWith(PROBE)).collect(Collectors.joining("\n"));
        tail = report + "\n" + tail;
        assertTrue(exited, "the probe did not finish:\n" + tail);
        assertTrue(all.contains(APPLIED), tail);
        assertEquals(0, process.exitValue(), tail);
        // nothing logs at DEBUG: neither the launcher before the first generation nor the application
        List<String> debug = all.lines().filter(line -> line.contains(" DEBUG ")).limit(5).toList();
        assertTrue(debug.isEmpty(), "DEBUG lines in the output:\n" + String.join("\n", debug));
    }

    /**
     * Runs an application whose {@code logback.xml} sets the root level to WARN, and whose {@code logger.levels} make
     * its logging system refresh Logback, then changes the file to set ERROR.
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
                            .properties(java.util.Map.of("spec.name", "LauncherLogbackTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Path resources = Files.createDirectories(project.resolve("src/main/resources"));
            Files.writeString(resources.resolve("application.properties"), "logger.levels.app.custom=TRACE\n");
            Path logbackXml = resources.resolve("logback.xml");
            Files.writeString(logbackXml, logback("WARN"));
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
                micronaut.dev.resources.config=src/main/resources
                micronaut.dev.patch-in-place=false
                """);
            DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
            try {
                LoggerContext logback = (LoggerContext) LoggerFactory.getILoggerFactory();
                Level root = logback.getLogger(Logger.ROOT_LOGGER_NAME).getLevel();
                Level custom = logback.getLogger("app.custom").getLevel();
                if (root != Level.WARN || custom != Level.TRACE) {
                    System.out.println(PROBE + "generation 1: root " + root + ", app.custom " + custom);
                    return 1;
                }
                Files.writeString(logbackXml, logback("ERROR"));
                runtime.changed(List.of(logbackXml), List.of());
                root = logback.getLogger(Logger.ROOT_LOGGER_NAME).getLevel();
                if (root != Level.ERROR) {
                    System.out.println(PROBE + "after the change: root " + root);
                    return 1;
                }
                runtime.restart();
                runtime.awaitGeneration(2, java.time.Duration.ofMinutes(2));
                root = logback.getLogger(Logger.ROOT_LOGGER_NAME).getLevel();
                custom = logback.getLogger("app.custom").getLevel();
                if (root != Level.ERROR || custom != Level.TRACE) {
                    System.out.println(PROBE + "generation 2: root " + root + ", app.custom " + custom);
                    return 1;
                }
                System.out.println(APPLIED);
                return 0;
            } finally {
                runtime.close();
            }
        }

        private static String logback(String level) {
            return """
                <configuration>
                    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                        <encoder>
                            <pattern>%%-5level %%logger{36} - %%msg%%n</pattern>
                        </encoder>
                    </appender>
                    <root level="%s">
                        <appender-ref ref="STDOUT"/>
                    </root>
                </configuration>
                """.formatted(level);
        }
    }
}
