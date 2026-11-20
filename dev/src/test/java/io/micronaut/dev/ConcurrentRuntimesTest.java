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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Development mode and test mode of one project at once, as {@code mnDev} and {@code mnTest} beside each other: two
 * processes compile the same sources into the same class output, and name the same generations directory.
 * Each edit is compiled by both, and each process loads what both write: no class is seen half written or missing, both
 * end on the last edit, and once the edits stop neither reloads for what the other wrote.
 */
class ConcurrentRuntimesTest {

    private static final int HELPERS = 40;
    private static final int EDITS = 15;

    @TempDir
    Path project;

    @Test
    void developmentAndTestModeOfOneProjectCompileTheSameEditsWithoutRacing() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.writeString(main.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("micronaut.server.port", "-1"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Path greeter = main.resolve("Greeter.java");
        Files.writeString(greeter, greeter(1));
        StringBuilder check = new StringBuilder("package app; public class Check { public static String all() { String v = H0.v();\n");
        for (int i = 0; i < HELPERS; i++) {
            // every helper depends on the greeter: each edit recompiles them all, and the promotion takes a while
            Files.writeString(main.resolve("H" + i + ".java"), "package app; @jakarta.inject.Singleton public class H" + i
                + " { public static String v() { return new Greeter().greet(); } }");
            check.append("if (!v.equals(H").append(i).append(".v())) throw new IllegalStateException(\"H").append(i).append(" is stale\");\n");
        }
        Files.writeString(main.resolve("Check.java"), check.append("return v; } }"));
        Files.writeString(test.resolve("GreeterTest.java"), """
            package app;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertTrue;
            public class GreeterTest {
                @Test
                void greets() throws Exception {
                    String version = Check.all();
                    assertTrue(version.startsWith("v"));
                    java.nio.file.Files.writeString(java.nio.file.Path.of("%s"), version);
                }
            }
            """.formatted(project.resolve("build/observed-test").toString().replace("\\", "\\\\")));
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        String application = """
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """;
        Files.writeString(project.resolve("run.properties"), application + """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            """);
        Files.writeString(project.resolve("test.properties"), application + """
            micronaut.dev.mode=test
            micronaut.dev.reloadable=build/test-classes,build/classes
            micronaut.dev.test.sources.java=src/test/java
            micronaut.dev.test.compile.java.output=build/test-classes
            micronaut.dev.test.reports=build/test-results
            micronaut.dev.generations=build/micronaut-dev/generations
            """);

        Process run = start("run");
        Process tests = start("test");
        StringBuilder runOutput = drain(run);
        StringBuilder testOutput = drain(tests);
        awaitReady(runOutput, run);
        awaitReady(testOutput, tests);

        Random random = new Random(42);
        for (int version = 2; version <= EDITS; version++) {
            write(greeter, greeter(version));
            Thread.sleep(50 + random.nextInt(400));
        }
        write(project.resolve("done"), "v" + EDITS);

        String runLog = await(run, runOutput);
        String testLog = await(tests, testOutput);
        for (String log : List.of(runLog, testLog)) {
            String tail = log.length() > 20000 ? log.substring(log.length() - 20000) : log;
            assertFalse(log.contains("probe-error"), tail);
            assertFalse(log.contains("ClassFormatError"), tail);
            assertFalse(log.contains("NoClassDefFoundError"), tail);
            assertFalse(log.contains("ClassNotFoundException"), tail);
            assertTrue(log.contains("probe-done: v" + EDITS), tail);
        }
        // the second process to start left the first one's generations alone and took a directory of its own
        assertTrue(Files.isDirectory(project.resolve("build/micronaut-dev/generations-2")));
    }

    private static String greeter(int version) {
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"v" + version + "\"; } }";
    }

    /**
     * Writes a file whole, as an editor saving through a temporary file does, so that a process never reads half of it.
     */
    private static void write(Path file, String contents) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, contents);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private Process start(String mode) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path argfile = project.resolve("jvm-" + mode + ".argfile");
        Files.write(argfile, List.of("-cp", "\"" + System.getProperty("java.class.path").replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, "-Xmx768m", ConcurrentRuntimeProbe.class.getName(), project.toString(), mode));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    private static StringBuilder drain(Process process) {
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = process.getInputStream().read(buffer)) >= 0) {
                    synchronized (output) {
                        output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                synchronized (output) {
                    output.append(e);
                }
            }
        });
        reader.setDaemon(true);
        reader.start();
        return output;
    }

    private static void awaitReady(StringBuilder output, Process process) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        while (System.nanoTime() < deadline && process.isAlive()) {
            synchronized (output) {
                if (output.indexOf("probe-ready") >= 0) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        synchronized (output) {
            throw new AssertionError("the probe did not start:\n" + output);
        }
    }

    private static String await(Process process, StringBuilder output) throws InterruptedException {
        boolean exited = process.waitFor(5, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        Thread.sleep(500);
        String log;
        synchronized (output) {
            log = output.toString();
        }
        // in the test report, to see what each process did
        System.out.println(log);
        assertTrue(exited, "the probe did not finish:\n" + log);
        assertEquals(0, process.exitValue(), log);
        return log;
    }
}
