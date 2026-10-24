package io.micronaut.dev;

import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How test mode follows tests an engine discovers from their source files, as pytest's: by the names of their files
 * rather than by classes.
 */
class TestSessionFileTestsTest {

    @TempDir
    Path project;

    @Test
    void aFileOfTestsIsNamedRelativeToItsRootOrTheDirectoryHoldingIt() throws Exception {
        Path tests = Files.createDirectories(project.resolve("tests/unit"));
        Files.writeString(tests.resolve("test_a.py"), "ok");
        List<SourceRoot> roots = roots(tests);

        assertTrue(TestSession.isTestFile(roots, "test_a.py"));
        assertTrue(TestSession.isTestFile(roots, "unit/test_a.py"));
        assertFalse(TestSession.isTestFile(roots, "unit/test_gone.py"));
        // a class name, even of a class whose source is there, is no file of tests
        assertFalse(TestSession.isTestFile(roots, "app.GreeterTest"));

        assertTrue(TestSession.isTestFileName(roots, "unit/test_gone.py"));
        assertFalse(TestSession.isTestFileName(roots, "app.GreeterTest"));
        assertFalse(TestSession.isTestFileName(List.of(new SourceRoot(SourceKind.JAVA, project.resolve("src/test/java"))), "test_a.py"));

        assertEquals(Set.of("test_a.py", "unit/test_a.py"), TestSession.resultNamesOf(roots, tests.resolve("test_a.py")));
        assertEquals(Set.of(), TestSession.resultNamesOf(roots, project.resolve("src/test/java/app/GreeterTest.java")));
    }

    @Test
    void aDeletedFileOrAFailedFileThatIsGoneHasItsResultsRemoved() throws Exception {
        Path tests = Files.createDirectories(project.resolve("tests"));
        Files.writeString(tests.resolve("test_kept.py"), "ok");
        List<SourceRoot> roots = roots(tests);
        Map<SourceKind, DevRuntime.SourceChanges> deleted = Map.of(
            SourceKind.PYTHON, new DevRuntime.SourceChanges(Set.of(), Set.of(tests.resolve("test_deleted.py"))),
            SourceKind.JAVA, new DevRuntime.SourceChanges(Set.of(), Set.of(project.resolve("src/test/java/app/GoneTest.java"))));

        Set<String> gone = TestSession.goneTestFiles(roots, deleted, List.of("tests/test_kept.py", "tests/test_failed.py", "app.FailedTest"));

        assertEquals(Set.of("test_deleted.py", "tests/test_deleted.py", "tests/test_failed.py"), gone);
        assertEquals(Set.of(), TestSession.goneTestFiles(roots, Map.of(), List.of("tests/test_kept.py")));
    }

    @Test
    void withFileTestsAnyChangeRunsEveryTest() {
        Path tests = project.resolve("tests");
        List<SourceRoot> roots = roots(tests);
        List<SourceRoot> classesOnly = List.of(new SourceRoot(SourceKind.JAVA, project.resolve("src/test/java")));
        Map<SourceKind, DevRuntime.SourceChanges> none = Map.of();
        Map<SourceKind, DevRuntime.SourceChanges> pythonTest = Map.of(SourceKind.PYTHON, new DevRuntime.SourceChanges(Set.of(tests.resolve("test_a.py")), Set.of()));
        Map<SourceKind, DevRuntime.SourceChanges> javaTest = Map.of(SourceKind.JAVA, new DevRuntime.SourceChanges(Set.of(project.resolve("src/test/java/app/ATest.java")), Set.of()));
        Map<SourceKind, DevRuntime.SourceChanges> application = Map.of(SourceKind.JAVA, new DevRuntime.SourceChanges(Set.of(), Set.of(project.resolve("src/main/java/app/A.java"))));

        // a changed file of tests
        assertTrue(TestSession.fileTestsChanged(classesOnly, none, pythonTest, Set.of()));
        // a change of the application, followed to no test, with file tests to run
        assertTrue(TestSession.fileTestsChanged(roots, application, none, Set.of()));
        assertTrue(TestSession.fileTestsChanged(roots, none, none, Set.of("app.A")));
        // nothing changed, or only classes followed to their tests
        assertFalse(TestSession.fileTestsChanged(roots, none, none, Set.of()));
        assertFalse(TestSession.fileTestsChanged(roots, none, javaTest, Set.of()));
        assertFalse(TestSession.fileTestsChanged(classesOnly, application, javaTest, Set.of("app.A")));
    }

    private List<SourceRoot> roots(Path tests) {
        return List.of(new SourceRoot(SourceKind.JAVA, project.resolve("src/test/java")), new SourceRoot(SourceKind.PYTHON, tests));
    }
}
