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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application whose context starts and whose server cannot be created, here a Netty server with no JSON module on
 * the classpath, under the launcher in a JVM of its own: the failure reaches the console and the launcher waits for the
 * change that fixes it. Run on its own, such an application logs the failure and exits with status 1. Nothing in the
 * launcher's JVM but the application keeps it alive, which only a JVM of its own shows.
 */
class LauncherFailedStartTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final Duration STILL_ALIVE = Duration.ofSeconds(3);
    private static final String CHANGED = "probe: the change started another generation";

    @TempDir
    Path project;

    @Test
    void aServerThatCannotBeCreatedIsReportedAndTheLauncherWaitsForAChange() throws Exception {
        assertFailureReportedAndChangeRestarts(entry -> false);
    }

    @Test
    void theFailureReachesTheConsoleWithNoLoggerToLogIt() throws Exception {
        assertFailureReportedAndChangeRestarts(entry -> entry.contains("logback"));
    }

    private void assertFailureReportedAndChangeRestarts(Predicate<String> alsoLeftOut) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // no JSON module, nor the HTTP client that needs one, and not the test resources with their logback-test.xml
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(entry -> !entry.contains("json-core") && !entry.contains("jackson") && !entry.contains("http-client"))
            .filter(entry -> !Files.isRegularFile(Path.of(entry).resolve("logback-test.xml")))
            .filter(alsoLeftOut.negate())
            .collect(Collectors.joining(File.pathSeparator));
        Path source = writeProject(classpath);
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + classpath.replace("\\", "\\\\") + "\""));
        Path output = project.resolve("output.txt");
        Process process = new ProcessBuilder(java, "@" + argfile, "-Xmx512m", MicronautDevMain.class.getName(), "--manifest", project.resolve("dev.properties").toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
        try {
            assertTrue(await(process, output, text -> text.contains("failed to start")), () -> "No failure reported, the launcher's status " + status(process) + ":\n" + read(output));
            String reported = read(output);
            assertTrue(reported.contains("JsonConfiguration") || reported.contains("JsonErrorResponseBodyProvider"), reported);
            assertFalse(reported.contains("generation 1 started"), reported);

            Thread.sleep(STILL_ALIVE.toMillis());
            assertTrue(process.isAlive(), () -> "The launcher exited with status " + status(process) + ":\n" + read(output));

            Files.writeString(source, source(CHANGED));
            assertTrue(await(process, output, text -> text.contains(CHANGED)), () -> "The change started no generation, the launcher's status " + status(process) + ":\n" + read(output));
            assertTrue(process.isAlive(), () -> "The launcher exited with status " + status(process) + ":\n" + read(output));
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    private Path writeProject(String classpath) throws IOException {
        Path source = Files.createDirectories(project.resolve("src/main/java/app")).resolve("Application.java");
        Files.writeString(source, """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "LauncherFailedStartTest", "micronaut.server.port", -1))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.write(project.resolve("cp.argfile"), List.of(classpath.split(File.pathSeparator)));
        Files.writeString(project.resolve("dev.properties"), """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.patch-in-place=false
            """);
        return source;
    }

    private static String source(String printed) {
        return """
            package app;
            public class Application {
                public static void main(String[] args) {
                    System.out.println("%s");
                }
            }
            """.formatted(printed);
    }

    private static boolean await(Process process, Path output, Predicate<String> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.test(read(output))) {
                return true;
            }
            if (!process.isAlive()) {
                return condition.test(read(output));
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static String status(Process process) {
        return process.isAlive() ? "running" : String.valueOf(process.exitValue());
    }

    private static String read(Path output) {
        try {
            return Files.readString(output);
        } catch (IOException e) {
            return e.toString();
        }
    }
}
