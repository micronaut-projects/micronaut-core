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
package io.micronaut.dev.python;

import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.test.TestRunSummary;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test mode keeps the GraalPy engine warm across the application contexts the test classes of a generation start,
 * and a new generation retires it, with nothing left to keep the retired generation reachable.
 */
class PythonTestModeWarmEngineTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final String WARM_ENGINE = "micronaut.dev.python.warm-engine";

    @TempDir
    Path project;

    private DevRuntime runtime;

    @BeforeEach
    void clearRecords() {
        WarmEngineRecords.ENTRIES.clear();
    }

    @AfterEach
    void close() {
        if (runtime != null) {
            runtime.close();
        }
        WarmEngineRecords.ENTRIES.clear();
        System.clearProperty(WARM_ENGINE);
    }

    @Test
    void theTestClassesOfAGenerationShareTheEngineAndANewGenerationRetiresIt() throws Exception {
        PythonFixture fixture = fixture();

        runtime = new MicronautDevMain().launch(manifest(), new String[0]);
        TestRunSummary first = runtime.lastTestRun().orElseThrow();
        assertTrue(first.isSuccess(), reports());
        List<WarmEngineRecords.Entry> firstRun = List.copyOf(WarmEngineRecords.ENTRIES);
        assertEquals(2, firstRun.size(), firstRun.toString());
        Engine warm = firstRun.get(0).engine().get();
        assertNotNull(warm, "the warm engine of the running generation was collected");
        assertSame(warm, firstRun.get(1).engine().get(), "the second test class did not get the warm engine");
        firstRun.forEach(entry -> assertEquals("Hello one", entry.greeting()));
        WeakReference<Engine> retired = new WeakReference<>(warm);
        warm = null;

        // a Java edit: the next run is on a new generation, whose engine replaces the warm one
        WarmEngineRecords.ENTRIES.clear();
        fixture.writeGreeting("Hi ");
        runtime.changed(List.of(fixture.greeting()), List.of());
        runtime.requestTests(io.micronaut.dev.TestRequest.ALL);
        List<WarmEngineRecords.Entry> secondRun = List.copyOf(WarmEngineRecords.ENTRIES);
        assertTrue(secondRun.size() >= 2, secondRun.toString());
        WarmEngineRecords.Entry last = secondRun.get(secondRun.size() - 1);
        assertEquals("Hi one", last.greeting());
        assertSame(secondRun.get(secondRun.size() - 2).engine().get(), last.engine().get());
        assertNotEquals(firstRun.get(0).engineId(), last.engineId(), "the new generation kept the retired generation's engine");

        awaitCollected(retired, "the warm engine of the retired generation");
        System.out.println("PYW warm: " + timings(firstRun) + " then " + timings(secondRun));
    }

    @Test
    void switchedOffEveryTestClassStartsItsOwnEngine() throws Exception {
        System.setProperty(WARM_ENGINE, "false");
        fixture();

        runtime = new MicronautDevMain().launch(manifest(), new String[0]);
        assertTrue(runtime.lastTestRun().orElseThrow().isSuccess(), reports());
        List<WarmEngineRecords.Entry> run = List.copyOf(WarmEngineRecords.ENTRIES);
        assertEquals(2, run.size(), run.toString());
        assertNotEquals(run.get(0).engineId(), run.get(1).engineId(), "an engine was shared with warm engines switched off");
        System.out.println("PYW cold: " + timings(run));
    }

    private PythonFixture fixture() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path tests = Files.createDirectories(project.resolve("src/test/java/app"));
        for (String name : List.of("FirstGreeterTest", "SecondGreeterTest")) {
            Files.writeString(tests.resolve(name + ".java"), """
                package app;

                import base.Greeting;
                import io.micronaut.context.ApplicationContext;
                import io.micronaut.dev.python.WarmEngineRecords;
                import io.micronaut.inject.qualifiers.Qualifiers;
                import org.graalvm.polyglot.Engine;
                import org.junit.jupiter.api.Test;

                public class %s {
                    @Test
                    void greets() {
                        long start = System.nanoTime();
                        try (ApplicationContext context = ApplicationContext.run()) {
                            String greeting = context.getBean(Greeting.class).greet();
                            long millis = (System.nanoTime() - start) / 1_000_000;
                            WarmEngineRecords.record("%s", context.getBean(Engine.class, Qualifiers.byName("python")), greeting, millis);
                        }
                    }
                }
                """.formatted(name, name));
        }
        return fixture;
    }

    private DevManifest manifest() throws Exception {
        List<String> classpath = PythonFixture.testClasspath().stream().map(Path::toString).toList();
        Files.write(project.resolve("cp.argfile"), classpath);
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.mode=test
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.test.sources.java=src/test/java
            micronaut.dev.test.compile.java.output=build/test-classes
            micronaut.dev.test.reports=build/test-results
            micronaut.dev.test.compile.java.options=-proc:none
            """);
        return DevManifest.load(manifestFile);
    }

    private String reports() {
        try (var files = Files.list(project.resolve("build/test-results"))) {
            return files.filter(file -> file.toString().endsWith(".xml")).map(file -> {
                try {
                    return Files.readString(file);
                } catch (Exception e) {
                    return e.toString();
                }
            }).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static String timings(List<WarmEngineRecords.Entry> run) {
        return run.stream().map(entry -> entry.test() + "=" + entry.startMillis() + "ms").collect(Collectors.joining(", "));
    }

    private static void awaitCollected(WeakReference<?> reference, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(200);
        }
        assertNull(reference.get(), what + " is still reachable");
    }
}
