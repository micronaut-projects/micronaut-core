package io.micronaut.dev.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResultsTest {

    private static final String FILE = "tests/test_math.py";

    @Test
    void aCompleteRunOfATestFileReplacesTheResultsOfTheClassesItReported() {
        TestResults results = new TestResults();
        run(results, TestSelection.all(), "test_adds", "test_removed");
        assertEquals(2, results.of(FILE).size());

        TestSelection file = new TestSelection(false, Set.of(), Set.of(), Set.of(Path.of(FILE)), List.of(), "1 file changed");
        TestResults.Applied applied = run(results, file, "test_adds");

        assertEquals(List.of("test_adds"), results.of(FILE).stream().map(result -> result.test().name()).toList());
        assertTrue(applied.classes().contains(FILE));

        // the last test of the file is gone: the file reports nothing, and its results go
        applied = run(results, file);
        assertTrue(results.of(FILE).isEmpty());
        assertTrue(applied.classes().contains(FILE));
    }

    @Test
    void outputBeyondTheLimitIsCountedNotKept() {
        TestResults results = new TestResults(5);
        TestId test = id("test_loud");
        results.runStarted(new TestRunStarted("1", "pytest", TestSelection.all(), Instant.now()));
        results.testStarted(test);
        results.output(test, TestOutput.STDOUT, "abc");
        results.output(test, TestOutput.STDOUT, "defgh");
        results.output(test, TestOutput.STDERR, "ok");
        TestResults.Result result = results.testFinished(test, new TestOutcome(TestStatus.PASSED, Duration.ZERO, null, null));

        assertEquals("abcde\n… 3 more characters not kept", result.out());
        assertEquals("ok", result.err());
    }

    private static TestResults.Applied run(TestResults results, TestSelection selection, String... names) {
        results.runStarted(new TestRunStarted("run", "pytest", selection, Instant.now()));
        for (String name : names) {
            TestId test = id(name);
            results.testStarted(test);
            results.testFinished(test, new TestOutcome(TestStatus.PASSED, Duration.ZERO, null, null));
        }
        return results.runFinished(new TestRunSummary("run", names.length, 0, 0, 0, Duration.ZERO, false, true));
    }

    private static TestId id(String name) {
        return new TestId("[engine:pytest]/[file:" + FILE + "]/[test:" + name + "]", FILE, name, name);
    }
}
