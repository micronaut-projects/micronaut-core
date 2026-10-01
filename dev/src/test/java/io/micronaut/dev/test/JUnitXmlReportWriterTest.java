package io.micronaut.dev.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JUnitXmlReportWriterTest {

    @TempDir
    Path project;

    @Test
    void aRunIsReportedAsOneSurefireFilePerClass() throws Exception {
        Path reports = project.resolve("build/test-results/mnTest");
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(reports);
        try (TestFixture fixture = TestFixture.compile(project)) {
            new JUnitPlatformTestRunner().run(fixture.request("run-1", TestSelection.all()), writer, new Cancellation());
        }

        Element calculator = suite(writer.reportOf(TestFixture.CALCULATOR));
        assertEquals(TestFixture.CALCULATOR, calculator.getAttribute("name"));
        assertEquals("5", calculator.getAttribute("tests"));
        assertEquals("1", calculator.getAttribute("failures"));
        assertEquals("1", calculator.getAttribute("errors"));
        assertEquals("2", calculator.getAttribute("skipped"));
        assertFalse(calculator.getAttribute("timestamp").isEmpty());
        Element property = (Element) calculator.getElementsByTagName("property").item(0);
        assertEquals(JUnitXmlReportWriter.SELECTION_PROPERTY, property.getAttribute("name"));
        assertEquals("all", property.getAttribute("value"));

        Element adds = testcase(calculator, "adds()");
        assertEquals(TestFixture.CALCULATOR, adds.getAttribute("classname"));
        assertTrue(adds.getAttribute("time").matches("\\d+\\.\\d{3}"));
        assertEquals("adding <one> & one\n", adds.getElementsByTagName("system-out").item(0).getTextContent());
        assertEquals("on stderr\n", adds.getElementsByTagName("system-err").item(0).getTextContent());

        Element fails = (Element) testcase(calculator, "fails()").getElementsByTagName("failure").item(0);
        assertEquals("wrong sum ==> expected: <3> but was: <2>", fails.getAttribute("message"));
        assertEquals("org.opentest4j.AssertionFailedError", fails.getAttribute("type"));
        assertTrue(fails.getTextContent().contains("CalculatorTest.fails"));
        Element errors = (Element) testcase(calculator, "errors()").getElementsByTagName("error").item(0);
        assertEquals(IllegalStateException.class.getName(), errors.getAttribute("type"));
        Element skipped = (Element) testcase(calculator, "disabled()").getElementsByTagName("skipped").item(0);
        assertEquals("not yet", skipped.getAttribute("message"));

        Element broken = suite(writer.reportOf(TestFixture.BROKEN));
        assertEquals("1", broken.getAttribute("errors"));
        assertEquals("1", broken.getAttribute("skipped"));
        assertEquals(1, testcase(broken, "initializationError").getElementsByTagName("error").getLength());
    }

    @Test
    void aRunReplacesTheReportsOfTheClassesItRanAndLeavesTheOthers() throws Exception {
        Path reports = project.resolve("reports");
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(reports);
        try (TestFixture fixture = TestFixture.compile(project)) {
            JUnitPlatformTestRunner runner = new JUnitPlatformTestRunner();
            runner.run(fixture.request("run-1", TestSelection.all()), writer, new Cancellation());
            String calculator = Files.readString(writer.reportOf(TestFixture.CALCULATOR));

            runner.run(fixture.request("run-2", TestSelection.ofClasses(Set.of(TestFixture.OTHER), "affected")), writer, new Cancellation());

            assertEquals(calculator, Files.readString(writer.reportOf(TestFixture.CALCULATOR)));
            Element other = suite(writer.reportOf(TestFixture.OTHER));
            assertEquals("affected", ((Element) other.getElementsByTagName("property").item(0)).getAttribute("value"));
        }
        try (var files = Files.list(reports)) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void aFullRunRemovesTheReportOfAClassThatIsGone() throws Exception {
        Path reports = project.resolve("reports");
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(reports);
        Files.createDirectories(reports);
        Path gone = writer.reportOf("fixture.DeletedTest");
        Files.writeString(gone, "<testsuite/>");
        Path unrelated = reports.resolve("notes.txt");
        Files.writeString(unrelated, "kept");
        try (TestFixture fixture = TestFixture.compile(project)) {
            JUnitPlatformTestRunner runner = new JUnitPlatformTestRunner();
            runner.run(fixture.request("run-1", TestSelection.ofClasses(Set.of(TestFixture.OTHER), "affected")), writer, new Cancellation());
            assertTrue(Files.exists(gone), "an affected run keeps the reports of the classes it did not run");
            runner.run(fixture.request("run-2", TestSelection.all()), writer, new Cancellation());
        }
        assertFalse(Files.exists(gone));
        assertTrue(Files.exists(writer.reportOf(TestFixture.CALCULATOR)));
        assertTrue(Files.exists(unrelated));
    }

    @Test
    void aRerunOfOneMethodKeepsTheLatestResultsOfTheOthersInItsClassReport() throws Exception {
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(project.resolve("reports"));
        try (TestFixture fixture = TestFixture.compile(project)) {
            JUnitPlatformTestRunner runner = new JUnitPlatformTestRunner();
            runner.run(fixture.request("run-1", TestSelection.all()), writer, new Cancellation());
            TestSelection failedFirst = new TestSelection(false, Set.of(), Set.of(TestFixture.CALCULATOR + "#adds"), Set.of(), List.of(), "failed");
            runner.run(fixture.request("run-2", failedFirst), writer, new Cancellation());
        }
        Element calculator = suite(writer.reportOf(TestFixture.CALCULATOR));
        assertEquals("5", calculator.getAttribute("tests"));
        assertEquals("1", calculator.getAttribute("failures"));
        assertEquals("failed", ((Element) calculator.getElementsByTagName("property").item(0)).getAttribute("value"));
    }

    @Test
    void aRerunReplacesTheResultsOfNestedClassesAndOfTestsAMethodProduced() throws Exception {
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(project.resolve("reports"));
        TestId nested = new TestId("[engine:junit-jupiter]/[class:fixture.OuterTest]/[nested-class:Inner]/[method:gone()]", "fixture.OuterTest$Inner", "gone()", "gone()");
        TestId dynamic = new TestId("[engine:junit-jupiter]/[class:fixture.FactoryTest]/[test-factory:generated()]/[dynamic-test:#3]", "fixture.FactoryTest", "three", "three");
        writer.runStarted(new TestRunStarted("run-1", "junit-platform", TestSelection.all(), Instant.now()));
        writer.testFinished(nested, new TestOutcome(TestStatus.FAILED, Duration.ZERO, null, null));
        writer.testFinished(dynamic, new TestOutcome(TestStatus.FAILED, Duration.ZERO, null, null));
        writer.runFinished(new TestRunSummary("run-1", 0, 2, 0, 0, Duration.ZERO, false, true));
        assertTrue(Files.exists(writer.reportOf("fixture.OuterTest$Inner")));

        // the outer class reran with its nested test gone, and the factory reran without its third test
        writer.runStarted(new TestRunStarted("run-2", "junit-platform", TestSelection.ofClasses(Set.of("fixture.OuterTest"), "affected"), Instant.now()));
        writer.runFinished(new TestRunSummary("run-2", 0, 0, 0, 0, Duration.ZERO, false, true));
        assertFalse(Files.exists(writer.reportOf("fixture.OuterTest$Inner")));
        writer.runStarted(new TestRunStarted("run-3", "junit-platform",
            new TestSelection(false, Set.of(), Set.of("fixture.FactoryTest#generated"), Set.of(), List.of(), "failed"), Instant.now()));
        writer.runFinished(new TestRunSummary("run-3", 0, 0, 0, 0, Duration.ZERO, false, true));
        assertFalse(Files.exists(writer.reportOf("fixture.FactoryTest")));
    }

    @Test
    void aFullRunCutShortRemovesNothing() throws Exception {
        Path reports = project.resolve("reports");
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(reports);
        Files.createDirectories(reports);
        Path earlier = writer.reportOf("fixture.EarlierTest");
        Files.writeString(earlier, "<testsuite/>");
        writer.runStarted(new TestRunStarted("run-1", "junit-platform", TestSelection.all(), Instant.now()));
        writer.runFinished(new TestRunSummary("run-1", 0, 0, 1, 0, Duration.ZERO, false, false));
        assertTrue(Files.exists(earlier));
    }

    @Test
    void theReportOfASelectedClassWithNoTestLeftIsRemoved() throws Exception {
        Path reports = project.resolve("reports");
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(reports);
        Files.createDirectories(reports);
        Path emptied = writer.reportOf("fixture.EmptiedTest");
        Files.writeString(emptied, "<testsuite/>");
        try (TestFixture fixture = TestFixture.compile(project)) {
            // the class exists but holds no test any more: it reports nothing
            new JUnitPlatformTestRunner().run(fixture.request("run-1", TestSelection.ofClasses(Set.of("fixture.EmptiedTest", TestFixture.OTHER), "affected")),
                writer, new Cancellation());
        }
        assertFalse(Files.exists(emptied));
        assertTrue(Files.exists(writer.reportOf(TestFixture.OTHER)));
    }

    @Test
    void testsWithoutAClassAreGroupedByTheirFileAndWhatXmlCannotHoldIsReplaced() throws Exception {
        JUnitXmlReportWriter writer = new JUnitXmlReportWriter(project);
        TestId test = new TestId("[engine:pytest]/[file:tests/test_books.py]/[test:test_saves]", "tests/test_books.py", "test_saves", "test saves");
        writer.runStarted(new TestRunStarted("run-1", "pyronaut", TestSelection.all(), Instant.now()));
        writer.testStarted(test);
        writer.output(test, TestOutput.STDOUT, "bell \u0007 and ok\n");
        writer.testFinished(test, new TestOutcome(TestStatus.PASSED, Duration.ofMillis(1500), null, null));
        writer.runFinished(new TestRunSummary("run-1", 1, 0, 0, 0, Duration.ofSeconds(2), false, true));

        Path report = project.resolve("TEST-tests-2Ftest_books.py.xml");
        assertEquals(report, writer.reportOf("tests/test_books.py"));
        // two files never share a report, and a class keeps its binary name as Surefire names it
        assertFalse(writer.reportOf("tests/a/b.py").equals(writer.reportOf("tests/a_b.py")));
        assertFalse(writer.reportOf("a/b").equals(writer.reportOf("a_2Fb")));
        assertFalse(writer.reportOf("a/b").equals(writer.reportOf("a-2Fb")));
        assertEquals(project.resolve("TEST-com.example.My_Test$Inner.xml"), writer.reportOf("com.example.My_Test$Inner"));
        Element suite = suite(report);
        Element testcase = testcase(suite, "test_saves");
        assertEquals("tests/test_books.py", testcase.getAttribute("classname"));
        assertEquals("1.500", testcase.getAttribute("time"));
        assertEquals("bell � and ok\n", testcase.getElementsByTagName("system-out").item(0).getTextContent());
        assertEquals("a�b😀", JUnitXmlReportWriter.clean("a\u0000b😀"));
        assertEquals("unpaired �", JUnitXmlReportWriter.clean("unpaired \uD83D"));
    }

    @Test
    void aBrokenListenerDoesNotKeepTheOthersFromTheirEvents() {
        List<String> seen = new ArrayList<>();
        TestEventListener broken = new TestEventListener() {
            @Override
            public void runStarted(TestRunStarted event) {
                throw new IllegalStateException("broken report");
            }
        };
        TestEventListener working = new TestEventListener() {
            @Override
            public void runStarted(TestRunStarted event) {
                seen.add(event.runId());
            }
        };
        TestEventListeners.composite(List.of(broken, working)).runStarted(new TestRunStarted("run-1", "junit-platform", TestSelection.all(), Instant.now()));
        assertEquals(List.of("run-1"), seen);
    }

    private static Element suite(Path report) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report.toFile());
        return document.getDocumentElement();
    }

    private static Element testcase(Element suite, String name) {
        NodeList cases = suite.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element element = (Element) cases.item(i);
            if (element.getAttribute("name").equals(name)) {
                return element;
            }
        }
        throw new AssertionError("No test case " + name + " in " + suite.getAttribute("name"));
    }
}
