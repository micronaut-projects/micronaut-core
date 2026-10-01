package io.micronaut.dev.test;

import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that an engine discovers from their source files, as pytest's, run with every test and again by their file.
 */
class FileDiscoveredTestsTest {

    @TempDir
    Path directory;

    @Test
    void everyTestSelectsTheTestDirectoriesAndAFileOfResultsRunsAgainByItself() throws Exception {
        Path tests = Files.createDirectories(directory.resolve("project/tests"));
        Files.writeString(tests.resolve("test_a.py"), "ok");
        Files.writeString(tests.resolve("test_b.py"), "fails");
        Path classes = Files.createDirectories(directory.resolve("project/test-classes"));
        // the engine is a service of the run's loader only, so it never joins the JUnit Platform running this test
        Path services = Files.createDirectories(directory.resolve("services/META-INF/services"));
        Files.writeString(services.resolve("org.junit.platform.engine.TestEngine"), FileTestEngine.class.getName());
        JUnitPlatformTestRunner runner = new JUnitPlatformTestRunner();
        Map<String, String> onlyFiles = Map.of("junit.jupiter.extensions.autodetection.enabled", "false");

        try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.resolve("services").toUri().toURL()}, getClass().getClassLoader())) {
            List<SourceRoot> sources = List.of(new SourceRoot(SourceKind.JAVA, directory.resolve("project/java")), new SourceRoot(SourceKind.PYTHON, tests));

            RecordingListener all = new RecordingListener();
            TestRunSummary everything = runner.run(new TestRunRequest("run-1", loader, List.of(classes), sources, TestSelection.all(), onlyFiles), all, new Cancellation());
            assertEquals(1, everything.passed());
            assertEquals(1, everything.failed());
            assertEquals(TestStatus.FAILED, all.status("tests/test_b.py.test_b.py"));

            // the failed file runs again, selected by the name its results are grouped under
            RecordingListener again = new RecordingListener();
            TestRunSummary rerun = runner.run(new TestRunRequest("run-2", loader, List.of(classes), sources,
                TestSelection.ofClasses(Set.of("tests/test_b.py"), "failed"), onlyFiles), again, new Cancellation());
            assertEquals(1, rerun.total());
            assertEquals(Set.of("tests/test_b.py.test_b.py"), again.outcomes.keySet());

            // a file that is gone runs nothing, rather than failing as a class that is not there
            Files.delete(tests.resolve("test_b.py"));
            RecordingListener gone = new RecordingListener();
            TestRunSummary none = runner.run(new TestRunRequest("run-3", loader, List.of(classes), sources,
                TestSelection.ofClasses(Set.of("tests/test_b.py"), "failed"), onlyFiles), gone, new Cancellation());
            assertEquals(0, none.total());
            assertTrue(none.complete());
        }
    }
}
