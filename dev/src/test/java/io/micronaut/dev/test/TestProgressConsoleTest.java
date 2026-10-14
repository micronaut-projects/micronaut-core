package io.micronaut.dev.test;

import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestProgressConsoleTest {

    private static final String REPORT = "http://localhost:35729/tests/";
    private static final String CALCULATOR = "com.example.CalculatorTest";

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final AtomicLong clock = new AtomicLong();

    @Test
    void aPassingRunShowsEveryTestAndTheSummaryWithTheReport() {
        TestProgressConsole console = console(true, true, REPORT);
        start(console, 2);
        TestId adds = test(CALCULATOR, "adds");
        TestId subtracts = test(CALCULATOR, "subtracts");
        console.testStarted(adds);
        console.testStarted(subtracts);
        advance(300);
        console.repaint();
        assertEquals(String.join("\n",
            row("⠋ Running tests · 0/2 done", "0.3s"),
            row("  └ CalculatorTest.adds()", "0.3s"),
            row("  └ CalculatorTest.subtracts()", "0.3s")), screen());

        console.testFinished(adds, passed(120));
        assertEquals(String.join("\n",
            "  ✓ CalculatorTest.adds() (0.1s)",
            row("⠋ Running tests · 1/2 done", "0.3s"),
            row("  └ CalculatorTest.subtracts()", "0.3s")), screen());

        console.testFinished(subtracts, passed(250));
        advance(200);
        console.runFinished(summary(2, 0, 0, 0, 500, false));

        assertEquals("""
              ✓ CalculatorTest.adds() (0.1s)
              ✓ CalculatorTest.subtracts() (0.3s)
            [1.5s] ✓ 2 tests passed (0.5s)
              Report: http://localhost:35729/tests/""", screen());
        String raw = raw();
        assertTrue(raw.contains("\033[32m✓\033[0m CalculatorTest.adds()"), raw);
        assertTrue(raw.contains("\033]8;;" + REPORT + "\033\\" + REPORT + "\033]8;;\033\\"), raw);
    }

    @Test
    void aFailingRunShowsTheFailuresWithTheirFramesAndTheSkippedTests() {
        TestProgressConsole console = console(true, true, null);
        start(console, 5);
        TestId adds = test(CALCULATOR, "adds");
        TestId fails = test(CALCULATOR, "fails");
        console.testStarted(adds);
        console.testFinished(adds, passed(10));
        console.testStarted(fails);
        console.testFinished(fails, new TestOutcome(TestStatus.FAILED, Duration.ofMillis(40), assertionFailure(), null));
        console.testFinished(test(CALCULATOR, "later"), new TestOutcome(TestStatus.SKIPPED, Duration.ZERO, null, "not yet"));
        console.testFinished(new TestId("[engine:junit-jupiter]/[class:com.example.BrokenTest]/initializationError", "com.example.BrokenTest",
                JUnitPlatformExecution.CLASS_ERROR, "BrokenTest"),
            new TestOutcome(TestStatus.ERRORED, Duration.ZERO, new TestFailure(IllegalStateException.class.getName(), "no context", ""), null));
        console.testStarted(test(CALCULATOR, "slow"));
        advance(100);
        console.repaint();
        assertEquals("""
              ✓ CalculatorTest.adds() (0.0s)
              ✗ CalculatorTest.fails() (0.0s)
                  AssertionFailedError: wrong sum ==> expected: <3> but was: <2>
                    at com.example.Calculator.add(Calculator.java:7)
                    at com.example.CalculatorTest.fails(CalculatorTest.java:21)
              – CalculatorTest.later() (skipped: not yet)
              ✗ BrokenTest (error)
                  IllegalStateException: no context
            """ + row("⠋ Running tests · 3/5 done, 1 failed, 1 error", "0.1s") + "\n" + row("  └ CalculatorTest.slow()", "0.1s"), screen());

        console.testFinished(test(CALCULATOR, "slow"), passed(100));
        console.runFinished(summary(2, 1, 1, 1, 900, false));

        assertEquals("""
              ✓ CalculatorTest.adds() (0.0s)
              ✗ CalculatorTest.fails() (0.0s)
                  AssertionFailedError: wrong sum ==> expected: <3> but was: <2>
                    at com.example.Calculator.add(Calculator.java:7)
                    at com.example.CalculatorTest.fails(CalculatorTest.java:21)
              – CalculatorTest.later() (skipped: not yet)
              ✗ BrokenTest (error)
                  IllegalStateException: no context
              ✓ CalculatorTest.slow() (0.1s)
            [1.1s] ✗ 1 of 3 tests failed, 1 error, 1 skipped (0.9s)""", screen());
        assertTrue(raw().contains("\033[31m✗\033[0m \033[1mCalculatorTest.fails()\033[0m"), raw());
    }

    @Test
    void aChangeThatDoesNotCompileShowsItsErrorsAndTheKeys() {
        TestProgressConsole console = console(true, true, null);
        console.keyHints("Continuous Testing Active. Press SPACE to run again, or \"q\" to exit.");
        advance(2000);
        console.compilationFailed(new CompileFailure(SourceKind.JAVA, List.of(
            new CompileDiagnostic(CompileDiagnostic.Severity.WARNING, "deprecated", Path.of("src/main/java/app/Old.java"), 3, 1),
            new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "cannot find symbol\n  symbol: variable b", Path.of("src/main/java/app/Greeter.java"), 12, 5)),
            Instant.now()));

        assertEquals("""
            --------------------------------------
            Continuous Testing Active. Press SPACE to run again, or "q" to exit.
            [2.0s] ✗ Java compilation failed: 1 error
                  src/main/java/app/Greeter.java:12: cannot find symbol
                      symbol: variable b
            --------------------------------------
            Continuous Testing Active. Press SPACE to run again, or "q" to exit.""", screen());
    }

    @Test
    void withoutATerminalTheConsolePrintsPlainLines() {
        TestProgressConsole console = console(false, false, REPORT);
        console.keyHints("Continuous Testing Active.");
        start(console, 3);
        TestId adds = test(CALCULATOR, "adds");
        TestId fails = test(CALCULATOR, "fails");
        console.testStarted(adds);
        console.testFinished(adds, passed(120));
        console.testStarted(fails);
        console.testFinished(fails, new TestOutcome(TestStatus.FAILED, Duration.ofMillis(40), assertionFailure(), null));
        console.testFinished(test(CALCULATOR, "later"), new TestOutcome(TestStatus.SKIPPED, Duration.ZERO, null, "not yet"));
        console.note("Nothing to test: no test depends on what changed");
        console.runFinished(summary(1, 1, 0, 1, 1200, false));
        console.compilationFailed(new CompileFailure(SourceKind.KOTLIN, List.of(
            new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "unresolved reference: b", null, 0, 0)), Instant.now()));

        assertEquals("""
            --------------------------------------
            Continuous Testing Active.
            > CalculatorTest.adds()
            PASSED CalculatorTest.adds() (0.1s)
            > CalculatorTest.fails()
            FAILED CalculatorTest.fails() (0.0s)
                  AssertionFailedError: wrong sum ==> expected: <3> but was: <2>
                    at com.example.Calculator.add(Calculator.java:7)
                    at com.example.CalculatorTest.fails(CalculatorTest.java:21)
            SKIPPED CalculatorTest.later() (skipped: not yet)
            Nothing to test: no test depends on what changed
            1 of 2 tests failed, 1 skipped in 1.2s
            Test report: http://localhost:35729/tests/
            --------------------------------------
            Continuous Testing Active.
            COMPILATION FAILED Kotlin compilation failed: 1 error
                  unresolved reference: b
            --------------------------------------
            Continuous Testing Active.
            """, raw());
        assertFalse(raw().contains("\033"), raw());
    }

    @Test
    void theAsciiSymbolsStandInWhereTheTerminalHasNoUnicode() {
        TestProgressConsole console = console(true, false, null);
        start(console, 1);
        TestId adds = test(CALCULATOR, "adds");
        console.testStarted(adds);
        assertEquals(row("- Running tests - 0/1 done", "0.0s") + "\n" + row("  L CalculatorTest.adds()", "0.0s"), screen());
        console.testFinished(adds, passed(100));
        console.runFinished(summary(1, 0, 0, 0, 100, false));
        assertEquals("""
              + CalculatorTest.adds() (0.1s)
            [1.0s] + 1 test passed (0.1s)""", screen());
    }

    @Test
    void theSummaryCountsWhatRan() {
        assertEquals("[1.0s] ! No tests ran (0.0s)", summaryOf(List.of(), summary(0, 0, 0, 0, 0, false)));
        assertEquals("[1.0s] ! No tests ran, 2 skipped (0.0s)",
            summaryOf(List.of(skippedOutcome(), skippedOutcome()), summary(0, 0, 0, 2, 0, false)));
        // a test that threw something other than an assertion failure is an error, as the reports count it
        assertEquals("[1.0s] ✗ 1 of 2 tests failed, 1 error (0.0s)",
            summaryOf(List.of(failedOutcome(TestStatus.FAILED), failedOutcome(TestStatus.ERRORED)), summary(0, 1, 1, 0, 0, false)));
        assertEquals("[1.0s] ✗ 1 of 2 tests passed, 1 error (0.0s)",
            summaryOf(List.of(passed(0), failedOutcome(TestStatus.ERRORED)), summary(1, 0, 1, 0, 0, false)));
        assertEquals("[1.0s] ✗ No tests passed, 2 errors (0.0s)",
            summaryOf(List.of(failedOutcome(TestStatus.ERRORED), failedOutcome(TestStatus.ERRORED)), summary(0, 0, 2, 0, 0, false)));
        assertEquals("[1.0s] ! Cancelled after 2 tests: 1 failed (0.0s)",
            summaryOf(List.of(passed(0), failedOutcome(TestStatus.FAILED)), summary(1, 1, 0, 0, 0, true)));
    }

    @Test
    void theFramesShownAreTheProjectsAndAtMostSix() {
        StringBuilder trace = new StringBuilder("java.lang.IllegalStateException: boom\n");
        trace.append("\tat java.base/java.util.Objects.requireNonNull(Objects.java:259)\n");
        for (int i = 0; i < 8; i++) {
            trace.append("\tat com.example.Service.call").append(i).append("(Service.java:").append(i).append(")\n");
        }
        trace.append("\tat org.junit.platform.Foo.run(Foo.java:1)\n");
        trace.append("Caused by: java.io.IOException: closed\n\tat com.example.Io.read(Io.java:3)\n");
        List<String> lines = TestProgressConsole.failureLines(new TestFailure(IllegalStateException.class.getName(), "boom\nsecond line", trace.toString()));
        assertEquals(List.of("IllegalStateException: boom", "second line",
            "  at com.example.Service.call0(Service.java:0)", "  at com.example.Service.call1(Service.java:1)",
            "  at com.example.Service.call2(Service.java:2)", "  at com.example.Service.call3(Service.java:3)",
            "  at com.example.Service.call4(Service.java:4)", "  at com.example.Service.call5(Service.java:5)", "  ..."), lines);

        assertEquals(List.of("NullPointerException"), TestProgressConsole.failureLines(new TestFailure("java.lang.NullPointerException", null, "")));
        String longMessage = String.join("\n", java.util.Collections.nCopies(40, "line"));
        List<String> trimmed = TestProgressConsole.failureLines(new TestFailure("AssertionError", longMessage, ""));
        assertEquals(31, trimmed.size());
        assertEquals("  ... (10 more lines in the report)", trimmed.get(30));
    }

    @Test
    void testsAreNamedByTheirSimpleClassOrTheirFile() {
        assertEquals("CalculatorTest.adds()", TestProgressConsole.name(test(CALCULATOR, "adds")));
        assertEquals("tests/test_app.py::test_index",
            TestProgressConsole.name(new TestId("[engine:pytest]/[test:tests/test_app.py::test_index]", "tests/test_app.py", "test_index", "tests/test_app.py::test_index")));
    }

    @Test
    void aClosedConsolePrintsNothingMore() {
        TestProgressConsole console = console(true, true, null);
        start(console, 1);
        console.testStarted(test(CALCULATOR, "adds"));
        console.close();
        assertEquals("", screen());
        console.testFinished(test(CALCULATOR, "adds"), passed(1));
        console.runFinished(summary(1, 0, 0, 0, 1, false));
        console.note("ignored");
        assertEquals("", screen());
    }

    @Test
    void theSummaryCountsErrorsAndSingleTests() {
        assertEquals("[1.0s] ✓ 1 test passed (0.0s)", summaryOf(List.of(passed(0)), summary(1, 0, 0, 0, 0, false)));
        assertEquals("[1.0s] ✗ 1 test passed, 2 errors (0.0s)",
            summaryOfErrors(List.of(passed(0)), 2, summary(1, 0, 2, 0, 0, false)));
        assertEquals("[1.0s] ✗ No tests ran, 1 error (0.0s)", summaryOfErrors(List.of(), 1, summary(0, 0, 1, 0, 0, false)));
        assertEquals("[1.0s] ! Cancelled after 1 test: 0 failed (0.0s)", summaryOf(List.of(passed(0)), summary(1, 0, 0, 0, 0, true)));
    }

    @Test
    void theRowsShowAtMostSixRunningTestsAndTheTestsDoneBeyondThoseDiscovered() {
        TestProgressConsole console = console(true, true, null);
        // dynamic tests: more done than discovered
        start(console, 0);
        for (int i = 0; i < 8; i++) {
            console.testStarted(test(CALCULATOR, "t" + i));
        }
        console.testFinished(test(CALCULATOR, "done"), new TestOutcome(TestStatus.FAILED, Duration.ZERO, null, null));
        console.testFinished(test(CALCULATOR, "skipped"), new TestOutcome(TestStatus.SKIPPED, Duration.ZERO, null, " "));
        List<String> lines = screen().lines().toList();
        assertEquals(List.of("  ✗ CalculatorTest.done() (0.0s)", "  – CalculatorTest.skipped() (skipped)",
            row("⠋ Running tests · 2 done, 1 failed", "0.0s")), lines.subList(0, 3));
        assertEquals(row("  └ CalculatorTest.t5()", "0.0s"), lines.get(8));
        assertEquals("  └ … 2 more", lines.get(9));
        assertEquals(10, lines.size());
    }

    @Test
    void outsideARunOrOnceClosedTheConsoleDrawsNoRows() {
        TestProgressConsole console = console(true, true, null);
        console.repaint();
        console.testStarted(test(CALCULATOR, "adds"));
        assertEquals("", raw());
        console.close();
        console.keyHints("Continuous Testing Active.");
        console.runStarted(new TestRunStarted("run-2", "junit-platform", TestSelection.all(), Instant.now()));
        console.testStarted(test(CALCULATOR, "adds"));
        console.compilationFailed(new CompileFailure(SourceKind.JAVA, List.of(), Instant.now()));
        assertEquals("", raw());
    }

    @Test
    void aChangeThatDoesNotCompileWithoutErrorsShowsItsDiagnostics() {
        TestProgressConsole console = console(false, false, null);
        List<CompileDiagnostic> diagnostics = new java.util.ArrayList<>();
        diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.WARNING, "  ", Path.of("Unknown.java"), 0, 0));
        for (int i = 0; i < 31; i++) {
            diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.WARNING, "warning " + i, null, 0, 0));
        }
        console.compilationFailed(new CompileFailure(SourceKind.GROOVY, diagnostics, Instant.now()));
        List<String> lines = raw().lines().toList();
        assertEquals("COMPILATION FAILED Groovy compilation failed", lines.get(0));
        assertEquals("      Unknown.java: ", lines.get(1));
        assertEquals("      warning 0", lines.get(2));
        assertEquals("        ... (2 more lines in the report)", lines.get(31));
        assertEquals(32, lines.size());
    }

    @Test
    void theFailureLinesKeepPythonMessagesAndFramesOutsideModules() {
        assertEquals(List.of("def test_index():", ">   assert 1 == 2"),
            TestProgressConsole.failureLines(new TestFailure("PythonAssertionError", "def test_index():\n>   assert 1 == 2", "")));
        assertEquals(List.of("AssertionError: expected 1", "  at com.example.Native.call", "  at com.example.A.b(A.java:1/2)"),
            TestProgressConsole.failureLines(new TestFailure("java.lang.AssertionError", "AssertionError: expected 1",
                "\tat com.example.Native.call\n\tat com.example.A.b(A.java:1/2)\n")));
        TestProgressConsole console = console(false, false, null);
        console.testFinished(test(CALCULATOR, "fails"), new TestOutcome(TestStatus.FAILED, Duration.ZERO, null, null));
        assertEquals("FAILED CalculatorTest.fails() (0.0s)\n", raw());
    }

    @Test
    void testsOfFilesOrWithoutAClassAreNamedByTheirDisplayName() {
        assertEquals("adds()", TestProgressConsole.name(new TestId("[engine:x]/[test:adds]", "", "adds", "adds()")));
        assertEquals("test_a", TestProgressConsole.name(new TestId("[engine:x]/[test:a]", "tests\\test_a.py", "test_a", "test_a")));
        assertEquals("test_a", TestProgressConsole.name(new TestId("[engine:x]/[test:a]", "test_a.py", "test_a", "test_a")));
        assertEquals("Suite", TestProgressConsole.name(new TestId("[engine:x]/[class:Suite]", "Suite", "Suite", "Suite")));
        assertEquals("CalculatorTest.adds()", TestProgressConsole.name(test("CalculatorTest", "adds")));
    }

    @Test
    void theTerminalIsDetectedFromTheEnvironment() {
        IntSupplier stty = () -> 132;
        assertEquals(new TestProgressConsole.Terminal(false, false, 80),
            TestProgressConsole.Terminal.detect(environment(Map.of()), false, "UTF-8", "Mac OS X", stty));
        assertEquals(new TestProgressConsole.Terminal(false, false, 80),
            TestProgressConsole.Terminal.detect(environment(Map.of("TERM", "DUMB")), true, "UTF-8", "Mac OS X", stty));
        assertEquals(new TestProgressConsole.Terminal(false, false, 80),
            TestProgressConsole.Terminal.detect(environment(Map.of("NO_COLOR", "1")), true, "UTF-8", "Mac OS X", stty));
        assertEquals(new TestProgressConsole.Terminal(true, true, 120),
            TestProgressConsole.Terminal.detect(environment(Map.of("TERM", "xterm-256color", "COLUMNS", " 120 ")), true, "UTF-8", "Linux", stty));
        // too narrow, or no number: asked of the terminal
        assertEquals(new TestProgressConsole.Terminal(true, false, 132),
            TestProgressConsole.Terminal.detect(environment(Map.of("COLUMNS", "30")), true, "US-ASCII", "Linux", stty));
        assertEquals(new TestProgressConsole.Terminal(true, false, 132),
            TestProgressConsole.Terminal.detect(environment(Map.of("COLUMNS", "wide")), true, "no-such-encoding", "Linux", stty));
        // Windows has no stty
        assertEquals(new TestProgressConsole.Terminal(true, true, 80),
            TestProgressConsole.Terminal.detect(environment(Map.of()), true, "UTF-8", "Windows 11", () -> {
                throw new AssertionError("stty asked on Windows");
            }));
        // the test JVM prints to no terminal
        assertFalse(TestProgressConsole.create(() -> null).isInteractive());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theWidthIsTheColumnsSttyPrints() {
        assertEquals(132, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "echo 24 132"));
        assertEquals(80, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "echo 24 20"));
        assertEquals(80, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "echo 24 wide"));
        assertEquals(80, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "echo 24"));
        assertEquals(80, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "echo 24 132; exit 1"));
        assertEquals(80, TestProgressConsole.Terminal.sttyWidth("/no/such/stty"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void anSttyThatHangsCannotBlockTheStartPastItsTimeout() {
        long start = System.nanoTime();
        assertEquals(80, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "sleep 30"));
        long elapsed = System.nanoTime() - start;
        assertTrue(elapsed < Duration.ofSeconds(4).toNanos(), "blocked for " + Duration.ofNanos(elapsed));
        // a process left holding the output open does not hold up the width the command printed
        start = System.nanoTime();
        assertEquals(132, TestProgressConsole.Terminal.sttyWidth("/bin/sh", "-c", "echo 24 132; sleep 5 &"));
        elapsed = System.nanoTime() - start;
        assertTrue(elapsed < Duration.ofSeconds(4).toNanos(), "blocked for " + Duration.ofNanos(elapsed));
    }

    @Test
    void aTestThatErrorsIsShownAsAnError() {
        TestProgressConsole console = console(true, true, null);
        start(console, 2);
        TestId breaks = test(CALCULATOR, "breaks");
        console.testStarted(breaks);
        console.testFinished(breaks, new TestOutcome(TestStatus.ERRORED, Duration.ofMillis(40),
            new TestFailure(IllegalStateException.class.getName(), "no connection", ""), null));
        console.testStarted(test(CALCULATOR, "slow"));
        console.repaint();
        assertEquals("""
              ✗ CalculatorTest.breaks() (error, 0.0s)
                  IllegalStateException: no connection
            """ + row("⠋ Running tests · 1/2 done, 1 error", "0.0s") + "\n" + row("  └ CalculatorTest.slow()", "0.0s"), screen());

        buffer.reset();
        TestProgressConsole plain = console(false, true, null);
        start(plain, 1);
        plain.testFinished(breaks, new TestOutcome(TestStatus.ERRORED, Duration.ofMillis(40),
            new TestFailure(IllegalStateException.class.getName(), "no connection", ""), null));
        plain.runFinished(summary(0, 0, 1, 0, 100, false));
        assertTrue(raw().contains("ERROR CalculatorTest.breaks() (0.0s)\n"), raw());
        assertTrue(raw().contains("No tests passed, 1 error in 0.1s\n"), raw());
        assertFalse(raw().contains("FAILED"), raw());
    }

    @Test
    void wideTestNamesFitTheRowsWithoutWrapping() {
        TestProgressConsole console = console(true, true, null);
        start(console, 2);
        TestId wide = test(CALCULATOR, "加算のテスト".repeat(10));
        console.testStarted(wide);
        console.testStarted(test(CALCULATOR, "slow"));
        console.repaint();
        advance(100);
        console.repaint();
        List<String> lines = screen().lines().toList();
        assertEquals(3, lines.size(), screen());
        for (String line : lines) {
            assertEquals(79, LiveRegion.width(line), line);
        }
        assertTrue(lines.get(1).contains("…"), lines.get(1));
        assertTrue(lines.get(1).endsWith("0.1s"), lines.get(1));
    }

    private static Function<String, String> environment(Map<String, String> variables) {
        return variables::get;
    }

    private String summaryOfErrors(List<TestOutcome> outcomes, int errors, TestRunSummary summary) {
        buffer.reset();
        clock.set(0);
        TestProgressConsole interactive = console(true, true, null);
        start(interactive, outcomes.size());
        int i = 0;
        for (TestOutcome outcome : outcomes) {
            interactive.testFinished(test(CALCULATOR, "t" + i++), outcome);
        }
        for (int e = 0; e < errors; e++) {
            interactive.testFinished(new TestId("[engine:junit-jupiter]/[class:com.example.Broken" + e + "]/initializationError", "com.example.Broken" + e,
                JUnitPlatformExecution.CLASS_ERROR, "Broken" + e), failedOutcome(TestStatus.ERRORED));
        }
        interactive.runFinished(summary);
        List<String> lines = screen().lines().toList();
        return lines.get(lines.size() - 1);
    }

    private String summaryOf(List<TestOutcome> outcomes, TestRunSummary summary) {
        buffer.reset();
        clock.set(0);
        TestProgressConsole interactive = console(true, true, null);
        start(interactive, outcomes.size());
        int i = 0;
        for (TestOutcome outcome : outcomes) {
            interactive.testFinished(test(CALCULATOR, "t" + i++), outcome);
        }
        interactive.runFinished(summary);
        List<String> lines = screen().lines().toList();
        return lines.get(lines.size() - 1);
    }

    /**
     * A row of the live region at 80 columns: the text on the left, the time right-aligned in the 79 drawn.
     */
    private static String row(String left, String right) {
        return left + " ".repeat(79 - left.length() - right.length()) + right;
    }

    private TestProgressConsole console(boolean interactive, boolean unicode, String report) {
        return new TestProgressConsole(new PrintStream(buffer, true, StandardCharsets.UTF_8),
            new TestProgressConsole.Terminal(interactive, unicode, 80), clock::get, false, () -> report);
    }

    private void start(TestProgressConsole console, int planned) {
        advance(1000);
        console.runStarted(new TestRunStarted("run-1", "junit-platform", TestSelection.all(), Instant.now()));
        console.testsPlanned(planned);
    }

    private void advance(long millis) {
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private static TestId test(String className, String method) {
        return new TestId("[engine:junit-jupiter]/[class:" + className + "]/[method:" + method + "()]", className, method, method + "()");
    }

    private static TestOutcome passed(long millis) {
        return new TestOutcome(TestStatus.PASSED, Duration.ofMillis(millis), null, null);
    }

    private static TestOutcome skippedOutcome() {
        return new TestOutcome(TestStatus.SKIPPED, Duration.ZERO, null, null);
    }

    private static TestOutcome failedOutcome(TestStatus status) {
        return new TestOutcome(status, Duration.ZERO, new TestFailure("java.lang.AssertionError", "no", ""), null);
    }

    private static TestFailure assertionFailure() {
        return new TestFailure("org.opentest4j.AssertionFailedError", "wrong sum ==> expected: <3> but was: <2>", """
            org.opentest4j.AssertionFailedError: wrong sum ==> expected: <3> but was: <2>
            \tat org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
            \tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:563)
            \tat com.example.Calculator.add(Calculator.java:7)
            \tat com.example.CalculatorTest.fails(CalculatorTest.java:21)
            \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)
            \tat io.micronaut.dev.test.JUnitPlatformExecution.run(JUnitPlatformExecution.java:120)
            """);
    }

    private static TestRunSummary summary(int passed, int failed, int errored, int skipped, long millis, boolean cancelled) {
        return new TestRunSummary("run-1", passed, failed, errored, skipped, Duration.ofMillis(millis), cancelled, !cancelled);
    }

    private String raw() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private String screen() {
        return TerminalScreen.render(raw());
    }
}
