package io.micronaut.dev.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JUnitPlatformTestRunnerTest {

    @TempDir
    Path project;

    private TestFixture fixture;
    private final JUnitPlatformTestRunner runner = new JUnitPlatformTestRunner();

    @BeforeEach
    void compile() throws Exception {
        fixture = TestFixture.compile(project);
    }

    @AfterEach
    void close() throws Exception {
        fixture.close();
    }

    @Test
    void theRunnerIsAvailableAndRegisteredAsAService() {
        assertTrue(runner.isAvailable());
        assertEquals("junit-platform", runner.id());
        assertTrue(ServiceLoader.load(TestRunner.class).stream().anyMatch(provider -> provider.type() == JUnitPlatformTestRunner.class));
    }

    @Test
    void everyTestOfTheOutputsRunsThroughTheGivenLoaderAndEachOutcomeIsReported() {
        RecordingListener events = new RecordingListener();
        ClassLoader before = Thread.currentThread().getContextClassLoader();

        TestRunSummary summary = runner.run(fixture.request("run-1", TestSelection.all()), events, new Cancellation());

        assertSame(before, Thread.currentThread().getContextClassLoader());
        assertEquals("run-1", events.started.runId());
        assertEquals("runStarted", events.events.getFirst());
        assertEquals("runFinished", events.events.getLast());
        assertSame(summary, events.summary);

        assertEquals(TestStatus.PASSED, events.status(TestFixture.CALCULATOR + ".adds()"));
        assertEquals(TestStatus.FAILED, events.status(TestFixture.CALCULATOR + ".fails()"));
        assertEquals("wrong sum ==> expected: <3> but was: <2>", events.outcomes.get(TestFixture.CALCULATOR + ".fails()").failure().message());
        assertEquals(TestStatus.ERRORED, events.status(TestFixture.CALCULATOR + ".errors()"));
        assertEquals(IllegalStateException.class.getName(), events.outcomes.get(TestFixture.CALCULATOR + ".errors()").failure().type());
        assertEquals(TestStatus.SKIPPED, events.status(TestFixture.CALCULATOR + ".disabled()"));
        assertEquals("not yet", events.outcomes.get(TestFixture.CALCULATOR + ".disabled()").skipReason());
        assertEquals(TestStatus.SKIPPED, events.status(TestFixture.CALCULATOR + ".assumes()"));
        assertTrue(events.outcomes.get(TestFixture.CALCULATOR + ".assumes()").skipReason().contains("no network"));

        // a class that fails to set up: one error for the class, its tests skipped
        assertEquals(TestStatus.ERRORED, events.status(TestFixture.BROKEN + ".initializationError"));
        assertTrue(events.outcomes.get(TestFixture.BROKEN + ".initializationError").failure().message().contains("no database"));
        assertEquals(TestStatus.SKIPPED, events.status(TestFixture.BROKEN + ".never()"));

        // the test class came from the run's loader, and its output was captured per test and stream
        assertEquals("loaded by fixture-generation\n", events.output.get(TestFixture.OTHER + ".other() STDOUT").toString());
        assertEquals("adding <one> & one\n", events.output.get(TestFixture.CALCULATOR + ".adds() STDOUT").toString());
        assertEquals("on stderr\n", events.output.get(TestFixture.CALCULATOR + ".adds() STDERR").toString());
        assertTrue(events.events.indexOf("started " + TestFixture.CALCULATOR + ".adds()") < events.events.indexOf("finished " + TestFixture.CALCULATOR + ".adds()"));

        assertEquals(5, summary.passed());
        assertEquals(1, summary.failed());
        assertEquals(2, summary.errored());
        assertEquals(3, summary.skipped());
        assertEquals(11, summary.total());
        assertFalse(summary.isSuccess());
        assertFalse(summary.cancelled());
    }

    @Test
    void theRunsLoaderIsTheContextLoaderUntilTheLastEvent() {
        ClassLoader[] seen = new ClassLoader[2];
        TestEventListener listener = new TestEventListener() {
            @Override
            public void runStarted(TestRunStarted event) {
                seen[0] = Thread.currentThread().getContextClassLoader();
            }

            @Override
            public void runFinished(TestRunSummary summary) {
                seen[1] = Thread.currentThread().getContextClassLoader();
            }
        };
        runner.run(fixture.request("run-0", TestSelection.ofClasses(Set.of(TestFixture.OTHER), "affected")), listener, new Cancellation());
        assertSame(fixture.loader, seen[0]);
        assertSame(fixture.loader, seen[1]);
    }

    @Test
    void aSelectionOfClassesOrMethodsRunsThoseOnly() {
        RecordingListener classes = new RecordingListener();
        runner.run(fixture.request("run-2", TestSelection.ofClasses(Set.of(TestFixture.OTHER), "affected")), classes, new Cancellation());
        assertEquals(Set.of(TestFixture.OTHER + ".other()"), classes.outcomes.keySet());
        assertEquals("affected", classes.started.selection().description());

        RecordingListener methods = new RecordingListener();
        TestSelection oneMethod = new TestSelection(false, Set.of(), Set.of(TestFixture.CALCULATOR + "#adds"), Set.of(), List.of(), "failed");
        TestRunSummary summary = runner.run(fixture.request("run-3", oneMethod), methods, new Cancellation());
        assertEquals(Set.of(TestFixture.CALCULATOR + ".adds()"), methods.outcomes.keySet());
        assertTrue(summary.isSuccess());
    }

    @Test
    void patternsFilterByClassAndMethodAsTheBuildToolsDo() {
        RecordingListener byMethod = new RecordingListener();
        runner.run(fixture.request("run-4", TestSelection.all().withPatterns(List.of("CalculatorTest.add*"))), byMethod, new Cancellation());
        assertEquals(Set.of(TestFixture.CALCULATOR + ".adds()"), byMethod.outcomes.keySet());

        RecordingListener everyMethod = new RecordingListener();
        runner.run(fixture.request("run-4b", TestSelection.all().withPatterns(List.of("CalculatorTest.*"))), everyMethod, new Cancellation());
        assertEquals(5, everyMethod.outcomes.size());
        assertTrue(everyMethod.outcomes.keySet().stream().allMatch(key -> key.startsWith(TestFixture.CALCULATOR + ".")));

        RecordingListener wildcardMethod = new RecordingListener();
        runner.run(fixture.request("run-4c", TestSelection.all().withPatterns(List.of("fixture.CalculatorTest.*s"))), wildcardMethod, new Cancellation());
        assertEquals(Set.of(TestFixture.CALCULATOR + ".adds()", TestFixture.CALCULATOR + ".fails()", TestFixture.CALCULATOR + ".errors()",
            TestFixture.CALCULATOR + ".assumes()"), wildcardMethod.outcomes.keySet());

        // a @TestFactory's tests exist only once it runs: the factory itself is matched as its method
        RecordingListener plainOnly = new RecordingListener();
        runner.run(fixture.request("run-4d", TestSelection.all().withPatterns(List.of("FactoryTest.plain"))), plainOnly, new Cancellation());
        assertEquals(Set.of(TestFixture.FACTORY + ".plain()"), plainOnly.outcomes.keySet());
        RecordingListener generatedOnly = new RecordingListener();
        runner.run(fixture.request("run-4e", TestSelection.all().withPatterns(List.of("FactoryTest.generated"))), generatedOnly, new Cancellation());
        assertEquals(2, generatedOnly.outcomes.size());
        assertTrue(generatedOnly.outcomes.keySet().stream().noneMatch(key -> key.endsWith("plain()")));

        RecordingListener byClass = new RecordingListener();
        runner.run(fixture.request("run-5", TestSelection.all().withPatterns(List.of("fixture.*Other*"))), byClass, new Cancellation());
        assertEquals(Set.of(TestFixture.OTHER + ".other()"), byClass.outcomes.keySet());
    }

    @Test
    void aSelectedClassThatIsNotThereIsReportedAsAnErrorOfItsOwn() {
        RecordingListener events = new RecordingListener();
        TestRunSummary summary = runner.run(fixture.request("run-6", TestSelection.ofClasses(Set.of("fixture.GoneTest", TestFixture.OTHER), "affected")), events, new Cancellation());
        assertEquals(TestStatus.ERRORED, events.status("fixture.GoneTest.initializationError"));
        assertEquals(ClassNotFoundException.class.getName(), events.outcomes.get("fixture.GoneTest.initializationError").failure().type());
        assertEquals(TestStatus.PASSED, events.status(TestFixture.OTHER + ".other()"));
        assertEquals(1, summary.errored());
        assertEquals(1, summary.passed());
    }

    @Test
    void aCancellationRunsEachCallbackOnce() {
        Cancellation cancellation = new Cancellation();
        java.util.concurrent.atomic.AtomicInteger before = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger after = new java.util.concurrent.atomic.AtomicInteger();
        cancellation.onCancel(before::incrementAndGet);
        cancellation.cancel();
        cancellation.cancel();
        cancellation.onCancel(after::incrementAndGet);
        assertEquals(1, before.get());
        assertEquals(1, after.get());
        assertTrue(cancellation.isCancelled());
    }

    @Test
    void aFailingCancellationCallbackDoesNotKeepTheOthersFromRunning() {
        Cancellation cancellation = new Cancellation();
        java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
        cancellation.onCancel(() -> {
            throw new IllegalStateException("first");
        });
        cancellation.onCancel(ran::incrementAndGet);
        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, cancellation::cancel);
        assertEquals("first", thrown.getMessage());
        assertEquals(1, ran.get());
    }

    @Test
    void aRunCancelledWhileItStartsRunsNoTest() {
        Cancellation cancellation = new Cancellation();
        TestEventListener cancelsOnStart = new TestEventListener() {
            @Override
            public void runStarted(TestRunStarted event) {
                cancellation.cancel();
            }
        };
        RecordingListener events = new RecordingListener();
        TestRunSummary summary = runner.run(fixture.request("run-8", TestSelection.all()), TestEventListeners.composite(List.of(cancelsOnStart, events)), cancellation);
        assertTrue(summary.cancelled());
        assertEquals(0, summary.total());
        assertTrue(events.outcomes.isEmpty());
    }

    @Test
    void aRunCancelledBeforeItStartsRunsNothingAndSaysSo() {
        Cancellation cancellation = new Cancellation();
        cancellation.cancel();
        RecordingListener events = new RecordingListener();
        TestRunSummary summary = runner.run(fixture.request("run-7", TestSelection.all()), events, cancellation);
        assertTrue(summary.cancelled());
        assertEquals(0, summary.total());
        assertEquals(List.of("runStarted", "runFinished"), events.events);
        assertNotNull(events.summary);
    }
}
