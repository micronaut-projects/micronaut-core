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
package io.micronaut.python.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compiler profiles a compilation only when asked, timing its phases, counting the javac rounds
 * and the Python model, and inventorying the output.
 */
final class CompilationProfileTest {

    private static final String SOURCE = """
        from jakarta.inject import Singleton
        from micronaut.context.annotation import Executable


        @Singleton
        class Greeter:
            @Executable
            def greet(self, name: str) -> str:
                return "Hello " + name
        """;

    @Test
    void timesThePhasesOfACompilation(@TempDir Path directory) throws Exception {
        CompilationProfile profile = profile(directory, null);

        assertEquals(directory.resolve("classes").toAbsolutePath().toString(), profile.attributes().get("target"));
        assertNotNull(profile.attributes().get("jvm.pid"));
        assertNotNull(profile.attributes().get("jvm.uptime.ms"));
        assertNotNull(profile.attributes().get("total.ms"));

        // the phases nest: the Python phases run inside the javac task inside the compilation
        CompilationProfile.Phase compile = profile.phase("compiler.compile");
        CompilationProfile.Phase javac = profile.phase("javac.task");
        CompilationProfile.Phase transform = profile.phase("python.transform");
        CompilationProfile.Phase model = profile.phase("python.model");
        assertNotNull(compile);
        assertNotNull(javac);
        assertNotNull(transform);
        assertNotNull(model);
        assertEquals(0, compile.depth());
        assertTrue(javac.depth() > compile.depth());
        assertTrue(transform.depth() > javac.depth());
        assertTrue(compile.nanos() >= javac.nanos());
        assertTrue(javac.nanos() >= transform.nanos() + model.nanos());
        assertEquals(1, compile.invocations());
        assertNotNull(profile.phase("python.graalpy-context"));
        assertNotNull(profile.phase("python.visitors.isolating"));
        assertNotNull(profile.phase("python.bean-definitions"));
        assertNotNull(profile.phase("compiler.inventory"));
    }

    @Test
    void countsTheModelAndInventoriesTheOutput(@TempDir Path directory) throws Exception {
        CompilationProfile profile = profile(directory, null);

        // the Python processor runs in the first round and the generated stubs compile in the next
        assertTrue(profile.counters().get("javac.rounds") >= 2);
        assertTrue(profile.counters().get("javac.analyzed-units") >= 2);
        assertEquals(1L, profile.counters().get("python.classes"));
        assertEquals(1L, profile.counters().get("python.transformed-sources"));
        assertTrue(profile.counters().get("python.unique-decorators") >= 2);
        assertTrue(profile.counters().get("python.decorator-chars") > 0);

        // the inventory counts the generated Java source, the class files and the Python files
        assertTrue(profile.artifact("java-source").count() >= 1);
        assertTrue(profile.artifact("class").count() >= 2);
        assertTrue(profile.artifact("python-source").count() >= 2);
        assertTrue(profile.artifact("class").bytes() > 0);
        assertNull(profile.artifact("nothing"));
    }

    @Test
    void appendsTheRenderedProfileToTheReportFile(@TempDir Path directory) throws Exception {
        Path report = directory.resolve("reports").resolve("profile.txt");
        CompilationProfile profile = profile(directory, report);

        String rendered = Files.readString(report);
        assertTrue(rendered.startsWith("# Pyronaut compilation profile\n"));
        assertTrue(rendered.contains("counter.python.transformed-sources=1\n"));
        assertTrue(rendered.contains("phase.compiler.compile.ms="));
        assertTrue(rendered.contains("phase.javac.task.ms="));
        assertTrue(rendered.contains("counter.javac.rounds="));
        assertTrue(rendered.contains("artifact.class.count="));
        assertEquals(rendered, profile.render() + System.lineSeparator());

        // a second compilation appends its profile
        profile(directory, report);
        assertEquals(2, Files.readString(report).split("# Pyronaut compilation profile\n").length - 1);
    }

    @Test
    void reportsTheCompilationFailureWhenTheProfileCannotBeWritten(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src"));
        // the compilation fails on this source, and the report destination is a directory, so writing
        // the profile fails too: the caller needs the syntax error, not the report failure
        Files.writeString(sources.resolve("broken.py"), "class Broken(:\n");
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path report = Files.createDirectories(directory.resolve("report.txt"));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonSrc(sources.toString())
            .targetDir(output.toFile())
            .profileReportFile(report.toFile())
            .build()
            .compile());

        String message = String.valueOf(failure.getMessage());
        assertAll(
            () -> assertTrue(message.contains("broken.py") || message.contains("SyntaxError"),
                "the compilation failure is reported, but was: " + message),
            () -> assertFalse(failure instanceof UncheckedIOException, "the report failure replaced it: " + message),
            () -> assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(UncheckedIOException.class::isInstance),
                "the report failure is attached as suppressed: " + Arrays.toString(failure.getSuppressed()))
        );
    }

    @Test
    void propagatesTheReportFailureWhenTheCompilationSucceeded(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src"));
        Files.writeString(sources.resolve("greeter.py"), SOURCE);
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path report = Files.createDirectories(directory.resolve("report.txt"));

        assertThrows(UncheckedIOException.class, () -> PyronautCompiler.builder()
            .pythonSrc(sources.toString())
            .targetDir(output.toFile())
            .profileReportFile(report.toFile())
            .build()
            .compile());
    }

    @Test
    void appendsWholeBlocksWhenCompilationsShareAReportFile(@TempDir Path directory) throws Exception {
        Path report = directory.resolve("reports").resolve("profile.txt");
        int compilations = 3;
        CountDownLatch ready = new CountDownLatch(compilations);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < compilations; i++) {
            Path own = Files.createDirectories(directory.resolve("compilation" + i));
            Thread thread = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    profile(own, report);
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(List.of(), failures);

        String rendered = Files.readString(report);
        String[] blocks = rendered.split("# Pyronaut compilation profile\n");
        assertEquals(compilations + 1, blocks.length, "one block per compilation");
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            // a block interleaved with another would lose keys or carry a truncated line
            assertAll(
                () -> assertTrue(block.contains("phase.compiler.compile.ms="), "complete phases: " + block),
                () -> assertTrue(block.contains("counter.javac.rounds="), "complete counters: " + block),
                () -> assertTrue(block.contains("artifact.class.bytes="), "complete inventory: " + block),
                () -> assertTrue(block.lines().allMatch(line -> line.isEmpty() || line.matches("[\\w.-]+=.*")),
                    "every line is a whole key=value: " + block)
            );
        }
    }

    private static CompilationProfile profile(Path directory, Path report) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src"));
        Files.writeString(sources.resolve("greeter.py"), SOURCE);
        Path output = Files.createDirectories(directory.resolve("classes"));
        List<CompilationProfile> profiles = new ArrayList<>();
        PyronautCompiler.Builder builder = PyronautCompiler.builder()
            .pythonSrc(sources.toString())
            .targetDir(output.toFile())
            .profileCallback(profiles::add);
        if (report != null) {
            builder.profileReportFile(report.toFile());
        }
        builder.build().compile();
        assertEquals(1, profiles.size());
        return profiles.getFirst();
    }

    @Test
    void recordsNothingWhenProfilingIsOff(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("src"));
        Files.writeString(sources.resolve("greeter.py"), SOURCE);
        Path output = Files.createDirectories(directory.resolve("classes"));

        PyronautCompiler.builder()
            .pythonSrc(sources.toString())
            .targetDir(output.toFile())
            .build()
            .compile();

        try (var files = Files.walk(directory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".txt") && !file.getFileName().toString().equals("fileslist.txt")));
        }
    }

    @Test
    void profilesAnInMemoryCompilation() {
        List<CompilationProfile> profiles = new ArrayList<>();
        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .profileCallback(profiles::add)
            .build()
            .buildClassLoader();

        assertNotNull(classLoader);
        assertEquals(1, profiles.size());
        CompilationProfile profile = profiles.getFirst();
        assertEquals("memory", profile.attributes().get("target"));
        assertNotNull(profile.phase("compiler.build-class-loader"));
        assertNotNull(profile.phase("javac.task"));
        assertTrue(profile.artifacts().isEmpty());
    }
}
