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

            // an engine that names a test's class by its file's absolute path, as pytest's does, is reported by the file
            // relative to its test source root, and that name runs the file again
            Files.writeString(tests.resolve("test_b.py"), "fails");
            Map<String, String> absolute = Map.of("junit.jupiter.extensions.autodetection.enabled", "false", FileTestEngine.ABSOLUTE, "true");
            RecordingListener named = new RecordingListener();
            runner.run(new TestRunRequest("run-4", loader, List.of(classes), sources, TestSelection.all(), absolute), named, new Cancellation());
            assertEquals(Set.of("test_a.py.test_a.py", "test_b.py.test_b.py"), named.outcomes.keySet());
            RecordingListener byRelativeName = new RecordingListener();
            TestRunSummary relativeRerun = runner.run(new TestRunRequest("run-5", loader, List.of(classes), sources,
                TestSelection.ofClasses(Set.of("test_b.py"), "failed"), absolute), byRelativeName, new Cancellation());
            assertEquals(1, relativeRerun.total());
            assertEquals(TestStatus.FAILED, byRelativeName.status("test_b.py.test_b.py"));
        }
    }

    @Test
    void twoRootsHoldingAFileAtTheSameRelativePathNameItsResultsByTheRootOnlyForThatFile() throws Exception {
        Path unit = Files.createDirectories(directory.resolve("project/src/test/python"));
        Path integration = Files.createDirectories(directory.resolve("project/src/integration/python"));
        Files.writeString(unit.resolve("test_app.py"), "ok");
        Files.writeString(unit.resolve("test_only.py"), "ok");
        Files.writeString(integration.resolve("test_app.py"), "fails");
        Path classes = Files.createDirectories(directory.resolve("project/test-classes"));
        Path services = Files.createDirectories(directory.resolve("services/META-INF/services"));
        Files.writeString(services.resolve("org.junit.platform.engine.TestEngine"), FileTestEngine.class.getName());
        JUnitPlatformTestRunner runner = new JUnitPlatformTestRunner();
        // named by their absolute paths, as pytest's engine names them
        Map<String, String> absolute = Map.of("junit.jupiter.extensions.autodetection.enabled", "false", FileTestEngine.ABSOLUTE, "true");

        try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.resolve("services").toUri().toURL()}, getClass().getClassLoader())) {
            List<SourceRoot> sources = List.of(new SourceRoot(SourceKind.PYTHON, unit), new SourceRoot(SourceKind.PYTHON, integration));

            // the two test_app.py are told apart by their roots' paths under the directory the roots share; a file only
            // one root holds keeps its plain relative name
            RecordingListener all = new RecordingListener();
            TestRunSummary everything = runner.run(new TestRunRequest("run-1", loader, List.of(classes), sources, TestSelection.all(), absolute), all, new Cancellation());
            assertEquals(3, everything.total());
            assertEquals(Set.of("test/python/test_app.py.test_app.py", "integration/python/test_app.py.test_app.py", "test_only.py.test_only.py"),
                all.outcomes.keySet());
            assertEquals(TestStatus.FAILED, all.status("integration/python/test_app.py.test_app.py"));

            // the failed file runs again by that name, and only it
            RecordingListener again = new RecordingListener();
            TestRunSummary rerun = runner.run(new TestRunRequest("run-2", loader, List.of(classes), sources,
                TestSelection.ofClasses(Set.of("integration/python/test_app.py"), "failed"), absolute), again, new Cancellation());
            assertEquals(1, rerun.total());
            assertEquals(Set.of("integration/python/test_app.py.test_app.py"), again.outcomes.keySet());

            // one root alone: the plain relative name
            RecordingListener single = new RecordingListener();
            runner.run(new TestRunRequest("run-3", loader, List.of(classes), List.of(new SourceRoot(SourceKind.PYTHON, integration)), TestSelection.all(), absolute),
                single, new Cancellation());
            assertEquals(Set.of("test_app.py.test_app.py"), single.outcomes.keySet());
        }
    }
}
