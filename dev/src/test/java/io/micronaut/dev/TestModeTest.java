package io.micronaut.dev;

import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.test.TestRunSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModeTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    @TempDir
    Path project;

    private DevRuntime runtime;

    @AfterEach
    void close() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void aChangeRunsTheTestsItAffectsOnANewGeneration() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Path greeter = main.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Files.writeString(main.resolve("Util.java"), "package app; public class Util { public static int twice(int value) { return value * 2; } }");
        Path greeterTest = test.resolve("GreeterTest.java");
        Files.writeString(greeterTest, greeterTest("one"));
        Files.writeString(test.resolve("UtilTest.java"), """
            package app;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class UtilTest {
                @Test
                void doubles() {
                    assertEquals(4, Util.twice(2));
                }
            }
            """);

        runtime = new MicronautDevMain().launch(manifest(""), new String[0]);

        // the first run, of every test, compiled from a clean checkout
        TestRunSummary first = runtime.lastTestRun().orElseThrow();
        assertEquals(1, runtime.testRuns());
        assertEquals(2, first.passed());
        assertTrue(first.isSuccess());
        Path reports = project.resolve("build/test-results");
        assertTrue(Files.exists(reports.resolve("TEST-app.GreeterTest.xml")));
        assertTrue(Files.exists(reports.resolve("TEST-app.UtilTest.xml")));

        // a change to the class under test runs its test alone, which fails
        Files.writeString(greeter, greeter("two"));
        runtime.changed(List.of(greeter), List.of());
        TestRunSummary second = runtime.awaitTestRun(2, TIMEOUT);
        assertEquals(1, second.failed());
        assertEquals(1, second.total());
        assertEquals(Set.of("app.GreeterTest"), runtime.failedTestClasses());
        String report = Files.readString(reports.resolve("TEST-app.GreeterTest.xml"));
        assertTrue(report.contains("failures=\"1\""), report);
        assertTrue(report.contains("value=\"affected\""), report);

        // the test follows the change: it runs again on its own and passes
        Files.writeString(greeterTest, greeterTest("two"));
        runtime.changed(List.of(greeterTest), List.of());
        TestRunSummary third = runtime.awaitTestRun(3, TIMEOUT);
        assertTrue(third.isSuccess());
        assertEquals(1, third.total());
        assertTrue(runtime.failedTestClasses().isEmpty());

        // a change that does not compile runs nothing and is reported
        Files.writeString(greeter, "package app; public class Greeter { public String greet() { return 1; } }");
        runtime.changed(List.of(greeter), List.of());
        assertEquals(3, runtime.testRuns());
        assertTrue(runtime.lastFailure().isPresent());

        // the fix runs the affected test again
        Files.writeString(greeter, greeter("two"));
        runtime.changed(List.of(greeter), List.of());
        TestRunSummary fourth = runtime.awaitTestRun(4, TIMEOUT);
        assertTrue(fourth.isSuccess());
        assertTrue(runtime.lastFailure().isEmpty());

        // asked for, every test runs; the last selection runs again on request
        TestRunSummary all = runtime.requestTests(TestRequest.ALL);
        assertEquals(2, all.total());
        TestRunSummary rerun = runtime.requestTests(TestRequest.RERUN);
        assertEquals(2, rerun.total());

        // with watching off a change compiles and runs nothing until asked; then the change's tests run
        runtime.watchTests(false);
        Files.writeString(main.resolve("Util.java"), "package app; public class Util { public static int twice(int value) { return value + value; } }");
        runtime.changed(List.of(main.resolve("Util.java")), List.of());
        assertEquals(6, runtime.testRuns());
        Path config = Files.createDirectories(project.resolve("src/test/resources")).resolve("application-test.properties");
        Files.writeString(config, "a=b\n");
        runtime.changed(List.of(config), List.of());
        assertEquals(6, runtime.testRuns());
        // asking for the failures alone leaves what changed owed to the next run
        assertNull(runtime.requestTests(TestRequest.FAILED));
        runtime.watchTests(true);
        TestRunSummary asked = runtime.requestTests(TestRequest.RERUN);
        assertEquals(7, runtime.testRuns());
        assertTrue(asked.isSuccess());
        // the resource change owed a run of every test
        assertEquals(2, asked.total());
    }

    @Test
    void aContextATestRunsSeesTheGenerationAndATestResourceTheBuildCopiedOnce() throws Exception {
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(project.resolve("src/main/java/app/Greeter.java"), greeter("one"));
        Files.writeString(test.resolve("ContextTest.java"), """
            package app;
            import io.micronaut.context.ApplicationContext;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class ContextTest {
                @Test
                void runs() {
                    try (ApplicationContext context = ApplicationContext.run()) {
                        assertEquals("io.micronaut.dev.loader.GenerationClassLoader", context.getEnvironment().getClassLoader().getClass().getName());
                        assertEquals("hello", context.getProperty("greeting", String.class).orElseThrow());
                    }
                }
            }
            """);
        Path config = Files.createDirectories(project.resolve("src/test/resources")).resolve("application-test.properties");
        Files.writeString(config, "greeting=hi\n");

        runtime = new MicronautDevMain().launch(manifest("micronaut.dev.generations=target/micronaut-dev/generations\n"), new String[0]);

        TestRunSummary first = runtime.lastTestRun().orElseThrow();
        Path report = project.resolve("build/test-results/TEST-app.ContextTest.xml");
        assertEquals(1, first.failed(), Files.readString(report));

        // the build copies the test configuration into the test output, as Maven does: the copy, which is stale, is
        // the same resource as the one read live, not a duplicate
        Files.copy(config, project.resolve("build/test-classes/application-test.properties"));
        Files.writeString(config, "greeting=hello\n");
        runtime.changed(List.of(config), List.of());
        TestRunSummary second = runtime.awaitTestRun(2, TIMEOUT);
        assertTrue(second.isSuccess(), Files.readString(report));
        // the generations go where the manifest says, a Maven build's target directory, marked as the launcher's
        assertTrue(Files.isRegularFile(project.resolve("target/micronaut-dev/generations/.micronaut-dev-generations")));
        assertFalse(Files.exists(project.resolve("build/micronaut-dev")));
    }

    @Test
    void aGenerationsDirectoryHoldingFilesTheLauncherDidNotWriteIsNotEmptied() throws Exception {
        Path precious = Files.createDirectories(project.resolve("generations")).resolve("notes.txt");
        Files.writeString(precious, "mine");
        Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(project.resolve("src/main/java/app/Greeter.java"), greeter("one"));

        IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> runtime = new MicronautDevMain().launch(manifest("micronaut.dev.generations=generations\n"), new String[0]));
        assertTrue(failure.getMessage().contains("did not write"), failure.getMessage());
        assertEquals("mine", Files.readString(precious));
    }

    @Test
    void aBrokenChangeStaysBrokenUntilFixedAnInlinedConstantRunsEveryTestAndADeletedTestTakesItsReport() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Path names = main.resolve("Names.java");
        Files.writeString(names, "package app; public class Names { public static final String NAME = \"one\"; }");
        Files.writeString(main.resolve("Greeter.java"), greeter("one"));
        Path namesTest = test.resolve("NamesTest.java");
        // the test reads the constant, which javac inlines: its class file keeps no reference to Names
        Files.writeString(namesTest, """
            package app;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class NamesTest {
                @Test
                void named() {
                    assertEquals("one", Names.NAME);
                }
            }
            """);
        Path greeterTest = test.resolve("GreeterTest.java");
        Files.writeString(greeterTest, greeterTest("one"));
        runtime = new MicronautDevMain().launch(manifest(""), new String[0]);
        assertEquals(2, runtime.lastTestRun().orElseThrow().passed());

        // the changed constant reaches the test that inlined it: every test compiles and runs again
        Files.writeString(names, "package app; public class Names { public static final String NAME = \"two\"; }");
        runtime.changed(List.of(names), List.of());
        TestRunSummary constants = runtime.awaitTestRun(2, TIMEOUT);
        assertEquals(2, constants.total());
        assertEquals(1, constants.failed());
        assertEquals(Set.of("app.NamesTest"), runtime.failedTestClasses());

        // a change that does not compile: asking for a run compiles it again, and runs nothing while it is broken
        Path greeter = main.resolve("Greeter.java");
        Files.writeString(greeter, "package app; public class Greeter { public String greet() { return 1; } }");
        runtime.changed(List.of(greeter), List.of());
        assertTrue(runtime.lastFailure().isPresent());
        assertNull(runtime.requestTests(TestRequest.ALL));
        assertEquals(2, runtime.testRuns());
        assertTrue(runtime.lastFailure().isPresent());
        Files.writeString(greeter, greeter("one"));
        runtime.changed(List.of(greeter), List.of());
        assertTrue(runtime.lastFailure().isEmpty());
        assertEquals(3, runtime.testRuns());

        // a deleted test takes its report and its failure with it
        Path reports = project.resolve("build/test-results");
        assertTrue(Files.exists(reports.resolve("TEST-app.NamesTest.xml")));
        Files.delete(namesTest);
        runtime.changed(List.of(), List.of(namesTest));
        assertFalse(Files.exists(reports.resolve("TEST-app.NamesTest.xml")));
        assertTrue(runtime.failedTestClasses().isEmpty());
        assertTrue(Files.exists(reports.resolve("TEST-app.GreeterTest.xml")));
    }

    @Test
    void testsTheBuildToolCompilesRunWhenItTouchesTheTrigger() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.writeString(main.resolve("Greeter.java"), greeter("one"));
        Path greeterTest = test.resolve("GreeterTest.java");
        Files.writeString(greeterTest, greeterTest("one"));
        // the build tool compiled the tests before the launch
        javac(project.resolve("build/test-classes"), greeterTest, main.resolve("Greeter.java"));
        Path trigger = project.resolve("build/trigger");
        runtime = new MicronautDevMain().launch(manifest("""
            micronaut.dev.test.compile.java.mode=build-tool
            micronaut.dev.build-tool.trigger=build/trigger
            """), new String[0]);
        assertEquals(1, runtime.lastTestRun().orElseThrow().passed());

        // the build tool compiles a changed test, then touches the trigger
        Files.writeString(greeterTest, greeterTest("two"));
        javac(project.resolve("build/test-classes"), greeterTest, main.resolve("Greeter.java"));
        Files.writeString(trigger, String.valueOf(System.nanoTime()));
        TestRunSummary second = runtime.awaitTestRun(2, TIMEOUT);
        assertEquals(1, second.failed());
    }

    private static void javac(Path output, Path... sources) throws Exception {
        Files.createDirectories(output);
        List<String> arguments = new java.util.ArrayList<>(List.of("-d", output.toString(), "-cp", System.getProperty("java.class.path"), "-proc:none"));
        for (Path source : sources) {
            arguments.add(source.toString());
        }
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
    }

    @Test
    void theLauncherWaitsInTestModeUntilTheRuntimeIsClosed() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.writeString(main.resolve("Greeter.java"), greeter("one"));
        Files.writeString(test.resolve("GreeterTest.java"), greeterTest("one"));
        writeManifest("");
        int[] status = {-2};
        Thread launcher = new Thread(() -> {
            try {
                status[0] = new MicronautDevMain().runForStatus(new String[] {"--manifest", project.resolve("dev.properties").toString()});
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        launcher.start();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while ((DevRuntime.current() == null || DevRuntime.current().testRuns() < 1) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(launcher.isAlive(), "the launcher keeps the JVM alive while the tests are watched");
        DevRuntime.current().close();
        launcher.join(TIMEOUT.toMillis());
        assertEquals(0, status[0]);
    }

    @Test
    void theRunThatSpendsTheGenerationBudgetCompletesThenTheRuntimeClosesForARelaunch() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Path greeter = main.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Files.writeString(test.resolve("GreeterTest.java"), greeterTest("one"));
        // the first run takes the second generation: a budget of two would relaunch after it, and the relaunched process
        // would run it again, without end
        assertThrows(IllegalArgumentException.class, () -> manifest("micronaut.dev.max-generations=1\n"));
        assertThrows(IllegalArgumentException.class, () -> manifest("micronaut.dev.max-generations=2\n"));
        // without a first run, the first run is a change's: two suffice; run once, the budget does not matter
        assertThrows(IllegalArgumentException.class, () -> manifest("micronaut.dev.max-generations=1\nmicronaut.dev.test.initial-run=false\n"));
        assertEquals(2, manifest("micronaut.dev.max-generations=2\nmicronaut.dev.test.initial-run=false\n").maxGenerations());
        assertEquals(1, manifest("micronaut.dev.max-generations=1\nmicronaut.dev.test.once=true\n").maxGenerations());

        writeManifest("micronaut.dev.max-generations=3\n");
        int[] status = {-2};
        Thread launcher = new Thread(() -> {
            try {
                status[0] = new MicronautDevMain().runForStatus(new String[] {"--manifest", project.resolve("dev.properties").toString()});
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        launcher.start();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while ((DevRuntime.current() == null || DevRuntime.current().testRuns() < 1) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        DevRuntime current = DevRuntime.current();
        assertFalse(current.isGenerationBudgetSpent());

        // a change runs its tests on generation three, the last of the budget: they run, then the runtime closes
        Files.writeString(greeter, greeter("two"));
        Files.writeString(test.resolve("GreeterTest.java"), greeterTest("two"));
        current.changed(List.of(greeter, test.resolve("GreeterTest.java")), List.of());
        launcher.join(TIMEOUT.toMillis());
        assertFalse(launcher.isAlive());
        assertEquals(MicronautDevMain.RELAUNCH, status[0]);
        assertEquals(2, current.testRuns());
        assertTrue(current.lastTestRun().orElseThrow().isSuccess());
        assertTrue(current.isRelaunchRequested());
        assertNull(DevRuntime.current());

        // run once, the budget does not matter: the status is the tests'
        writeManifest("micronaut.dev.max-generations=3\nmicronaut.dev.test.once=true\n");
        assertEquals(0, new MicronautDevMain().runForStatus(new String[] {"--manifest", project.resolve("dev.properties").toString()}));
    }

    @Test
    void withoutAFirstRunAChangeIsComparedWithTheClassFilesTheRuntimeStartedWith() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.writeString(main.resolve("Greeter.java"), greeter("one"));
        Path greeterTest = test.resolve("GreeterTest.java");
        Files.writeString(greeterTest, greeterTest("one"));
        Path oldReport = Files.createDirectories(project.resolve("build/test-results")).resolve("TEST-app.GreeterTest.xml");
        Files.writeString(oldReport, "<testsuite/>");
        runtime = new MicronautDevMain().launch(manifest("micronaut.dev.test.initial-run=false\n"), new String[0]);
        assertEquals(0, runtime.testRuns());

        Files.delete(greeterTest);
        runtime.changed(List.of(), List.of(greeterTest));
        assertFalse(Files.exists(oldReport));
    }

    @Test
    void runOnceRunsEveryTestAndExitsWithItsStatus() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.writeString(main.resolve("Greeter.java"), greeter("one"));
        Files.writeString(test.resolve("GreeterTest.java"), greeterTest("two"));
        writeManifest("micronaut.dev.test.once=true\n");

        int status = new MicronautDevMain().runForStatus(new String[] {"--manifest", project.resolve("dev.properties").toString()});

        assertEquals(1, status);
        assertNull(DevRuntime.current());
        // the launcher compiles the outputs that are missing; the build tool compiles the rest before it launches
        Files.writeString(test.resolve("GreeterTest.java"), greeterTest("one"));
        try (var files = Files.walk(project.resolve("build/test-classes"))) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
        assertEquals(0, new MicronautDevMain().runForStatus(new String[] {"--manifest", project.resolve("dev.properties").toString()}));
    }

    @Test
    void theConsoleKeysAskForRuns() throws Exception {
        Path main = Files.createDirectories(project.resolve("src/main/java/app"));
        Path test = Files.createDirectories(project.resolve("src/test/java/app"));
        Files.writeString(main.resolve("Greeter.java"), greeter("one"));
        Files.writeString(test.resolve("GreeterTest.java"), greeterTest("one"));
        runtime = new MicronautDevMain().launch(manifest(""), new String[0]);
        TestConsole console = new TestConsole(runtime, System.in);

        assertTrue(console.act('a'));
        assertEquals(2, runtime.testRuns());
        assertTrue(console.act('w'));
        assertFalse(runtime.isWatchingTests());
        assertTrue(console.act('\n'));
        assertTrue(console.act('f'));
        assertEquals(2, runtime.testRuns());
    }

    private DevManifest manifest(String extra) throws Exception {
        return DevManifest.load(writeManifest(extra));
    }

    private Path writeManifest(String extra) throws Exception {
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifest = project.resolve("dev.properties");
        Files.writeString(manifest, """
            micronaut.dev.mode=test
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.test.sources.java=src/test/java
            micronaut.dev.test.compile.java.output=build/test-classes
            micronaut.dev.test.reports=build/test-results
            micronaut.dev.test.resources.config=src/test/resources
            micronaut.dev.compile.java.options=-proc:none
            micronaut.dev.test.compile.java.options=-proc:none
            """ + extra);
        return manifest;
    }

    private static String greeter(String greeting) {
        return "package app; public class Greeter { public String greet() { return \"" + greeting + "\"; } }";
    }

    private static String greeterTest(String expected) {
        return """
            package app;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class GreeterTest {
                @Test
                void greets() {
                    assertEquals("%s", new Greeter().greet());
                }
            }
            """.formatted(expected);
    }
}
