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
package io.micronaut.dev;

import io.micronaut.context.reload.ResourceKind;
import io.micronaut.dev.change.ChangeSet;
import io.micronaut.dev.compile.ClassDependencyIndex;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.TestSettings;
import io.micronaut.dev.test.Cancellation;
import io.micronaut.dev.test.JUnitXmlReportWriter;
import io.micronaut.dev.test.TestEventListener;
import io.micronaut.dev.test.TestEventListeners;
import io.micronaut.dev.test.TestId;
import io.micronaut.dev.test.TestOutcome;
import io.micronaut.dev.test.TestReportListener;
import io.micronaut.dev.test.TestRunRequest;
import io.micronaut.dev.test.TestRunStarted;
import io.micronaut.dev.test.TestRunSummary;
import io.micronaut.dev.test.TestRunner;
import io.micronaut.dev.test.TestSelection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Test mode: what a batch of changes does when the runtime runs the tests rather than the application.
 *
 * <p>The application's sources compile as in development mode, then the test sources, against the application's
 * output, with the classes the first compilation changed as the seed of the second: a test that references a
 * changed class compiles again. The tests to run are those whose classes reference a changed class, directly or
 * through other classes of either output, as the class files tell, together with the classes that failed last;
 * every test runs instead after a change of configuration or resources, when a changed class declares
 * compile-time constants, which javac inlines without a reference to follow, or when the settings ask for it. The
 * run happens on a new generation of the reloadable loader, so the tests and the classes under test are the
 * ones just compiled, and its events go to the JUnit XML reports and to the report listeners registered as
 * services.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class TestSession {

    private static final Logger LOG = LoggerFactory.getLogger(TestSession.class);

    private final DevRuntime runtime;
    private final DevManifest manifest;
    private final DevManifest tests;
    private final Map<SourceKind, SourceKind> testJoint;
    private final TestSettings settings;
    private final TestRunner runner;
    private final JUnitXmlReportWriter xml;
    private final List<TestReportListener> reports;
    // guarded by this
    private final Set<String> failedClasses = new LinkedHashSet<>();
    // what changed and no complete run has covered yet: a run cancelled, or not run while watching was off, leaves it owed
    private final Set<String> owedChanges = new LinkedHashSet<>();
    private boolean owedAll;
    private final Map<SourceKind, DevRuntime.SourceChanges> retrySources = new LinkedHashMap<>();
    private final Map<SourceKind, DevRuntime.SourceChanges> retryTestSources = new LinkedHashMap<>();
    // the class files as the last batch left them: the dependency indexes, the test classes, and the classes declaring constants
    private List<ClassDependencyIndex> indexes = List.of();
    private Set<String> knownTestClasses = Set.of();
    private Set<String> constantDeclarers = Set.of();
    private TestSelection lastSelection = TestSelection.all();
    private @Nullable TestRunSummary last;
    private int runs;
    private boolean compileFailed;
    private volatile boolean watching = true;
    private volatile @Nullable Cancellation current;

    TestSession(DevRuntime runtime, DevManifest manifest, Map<SourceKind, SourceCompiler> compilers) {
        this.runtime = runtime;
        this.manifest = manifest;
        this.tests = manifest.testView();
        this.testJoint = DevRuntime.jointOwners(tests, compilers);
        this.settings = manifest.testSettings();
        this.runner = runner(settings.runner());
        this.xml = new JUnitXmlReportWriter(settings.reports());
        this.reports = TestEventListeners.reportListeners(TestSession.class.getClassLoader());
    }

    /**
     * @return The tests seen as a manifest of their own
     */
    DevManifest tests() {
        return tests;
    }

    /**
     * @return The settings
     */
    TestSettings settings() {
        return settings;
    }

    private static TestRunner runner(String id) {
        List<String> available = new ArrayList<>();
        for (TestRunner candidate : ServiceLoader.load(TestRunner.class, TestRunner.class.getClassLoader())) {
            if (candidate.isAvailable()) {
                if (candidate.id().equals(id)) {
                    return candidate;
                }
                available.add(candidate.id());
            }
        }
        throw new IllegalStateException("No test runner " + id + " is available" + (available.isEmpty() ? ": put junit-platform-launcher on the launch classpath" : ", only " + available));
    }

    /**
     * Compiles a batch's changes and runs the tests they affect, or those asked for.
     */
    void handle(Map<SourceKind, DevRuntime.SourceChanges> sources, Map<SourceKind, DevRuntime.SourceChanges> testSources,
                Map<ResourceKind, DevRuntime.SourceChanges> resources, boolean full, @Nullable TestRequest requested) {
        // the sources of a batch that did not compile are compiled again with the next one, a request included
        Map<SourceKind, DevRuntime.SourceChanges> allSources;
        Map<SourceKind, DevRuntime.SourceChanges> allTestSources;
        Set<String> previousConstants;
        synchronized (this) {
            allSources = merge(retrySources, sources);
            allTestSources = merge(retryTestSources, testSources);
            previousConstants = constantDeclarers;
        }
        DevRuntime.CompileRound application = runtime.compileRound(manifest, runtime.jointOwners(), allSources, full, Set.of());
        if (application.failure() != null) {
            compilationFailed(application.failure(), allSources, allTestSources);
            return;
        }
        List<ClassDependencyIndex> applicationIndexes = scan(outputs(manifest, manifest.sourceRoots()));
        Set<String> applicationConstants = constantDeclarers(applicationIndexes);
        // javac inlined the constants of a changed class into the tests that read them, without a reference to follow,
        // whether the class declares them now or declared them before the change: every test compiles and runs again
        boolean constants = application.affectedClasses().stream().anyMatch(name -> previousConstants.contains(name) || applicationConstants.contains(name));
        DevRuntime.CompileRound testRound = runtime.compileRound(tests, testJoint, allTestSources, full || constants, application.affectedClasses());
        if (testRound.failure() != null) {
            compilationFailed(testRound.failure(), allSources, allTestSources);
            return;
        }
        synchronized (this) {
            retrySources.clear();
            retryTestSources.clear();
        }
        compilationRecovered();
        List<ClassDependencyIndex> testIndexes = scan(outputs(tests, tests.sourceRoots()));
        List<ClassDependencyIndex> indexes = new ArrayList<>(applicationIndexes);
        indexes.addAll(testIndexes);
        Set<String> testClasses = new HashSet<>();
        for (ClassDependencyIndex index : testIndexes) {
            testClasses.addAll(index.classes());
        }
        Set<String> removedTests;
        Set<String> currentConstants = constantDeclarers(indexes);
        // a test helper's constants: its compiler recompiled the tests that inlined them, and they all run
        constants |= testRound.affectedClasses().stream().anyMatch(name -> previousConstants.contains(name) || currentConstants.contains(name));
        synchronized (this) {
            removedTests = new LinkedHashSet<>(knownTestClasses);
            removedTests.removeAll(testClasses);
            knownTestClasses = Set.copyOf(testClasses);
            constantDeclarers = currentConstants;
            this.indexes = List.copyOf(indexes);
        }
        if (!removedTests.isEmpty()) {
            testClassesRemoved(removedTests);
        }
        // a file of tests discovered from their files, deleted, or failed and deleted since: its results go, and its failure
        Set<String> goneFiles = new LinkedHashSet<>();
        allTestSources.forEach((kind, change) -> {
            if (!isJvmKind(kind)) {
                for (Path deleted : change.deleted()) {
                    goneFiles.addAll(resultNamesOf(deleted));
                }
            }
        });
        synchronized (this) {
            for (String failed : failedClasses) {
                if (isTestFileName(failed) && !isTestFile(failed)) {
                    goneFiles.add(failed);
                }
            }
        }
        if (!goneFiles.isEmpty()) {
            testClassesRemoved(goneFiles);
        }
        // a test discovered from its source file, as a pytest function, has no class whose change can be followed
        boolean fileTestsChanged = allTestSources.entrySet().stream()
            .anyMatch(entry -> !isJvmKind(entry.getKey()) && (!entry.getValue().changed().isEmpty() || !entry.getValue().deleted().isEmpty()));
        ChangeSet changes = runtime.takeOutputChanges();
        Set<String> changed = new LinkedHashSet<>(application.affectedClasses());
        changed.addAll(testRound.affectedClasses());
        for (String className : changes.classNames()) {
            changed.add(ClassDependencyIndex.topLevelOf(className));
        }
        changed.removeAll(removedTests);
        // nor can a change of the application be followed to them: with such tests, every change runs every test
        boolean fileTests = tests.sourceRoots().stream().anyMatch(root -> !isJvmKind(root.kind()));
        fileTestsChanged |= fileTests && (!changed.isEmpty() || allSources.values().stream().anyMatch(change -> !change.changed().isEmpty() || !change.deleted().isEmpty()));
        boolean resourcesChanged = resources.values().stream().anyMatch(change -> !change.changed().isEmpty() || !change.deleted().isEmpty())
            || !changes.changedResources().isEmpty() || !changes.removedResources().isEmpty();
        synchronized (this) {
            owedChanges.addAll(changed);
            owedAll |= full || resourcesChanged || fileTestsChanged || constants || !settings.affectedOnly() && !changed.isEmpty();
        }
        if (requested != null) {
            run(requested(requested), requested != TestRequest.FAILED);
            return;
        }
        if (!watching) {
            LOG.info("Compiled; watching is off, so no test runs until one is asked for");
            return;
        }
        TestSelection selection = owed("affected");
        if (selection == null) {
            LOG.info("Nothing to test: no test depends on what changed");
            return;
        }
        run(selection, true);
    }

    private static Map<SourceKind, DevRuntime.SourceChanges> merge(Map<SourceKind, DevRuntime.SourceChanges> first, Map<SourceKind, DevRuntime.SourceChanges> second) {
        Map<SourceKind, DevRuntime.SourceChanges> merged = new LinkedHashMap<>();
        merged.putAll(first);
        second.forEach((kind, changes) -> merged.merge(kind, changes, DevRuntime.SourceChanges::merge));
        return merged;
    }

    private static List<ClassDependencyIndex> scan(List<Path> outputs) {
        List<ClassDependencyIndex> scanned = new ArrayList<>();
        for (Path output : outputs) {
            scanned.add(ClassDependencyIndex.scan(output));
        }
        return scanned;
    }

    private static Set<String> constantDeclarers(List<ClassDependencyIndex> indexes) {
        Set<String> declarers = new HashSet<>();
        for (ClassDependencyIndex index : indexes) {
            for (String className : index.classes()) {
                if (index.declaresConstants(className)) {
                    declarers.add(className);
                }
            }
        }
        return Set.copyOf(declarers);
    }

    /**
     * Test classes whose sources are gone: their results go, from the reports and from the failures.
     */
    private void testClassesRemoved(Set<String> classNames) {
        synchronized (this) {
            // the classes nested in a removed one went with it: their failures are recorded under their own names
            failedClasses.removeIf(name -> classNames.contains(name) || classNames.stream().anyMatch(removed -> name.startsWith(removed + "$")));
        }
        for (String className : classNames) {
            xml.remove(className);
        }
        for (TestReportListener report : reports) {
            try {
                report.testClassesRemoved(classNames);
            } catch (RuntimeException e) {
                LOG.error("The test report {} failed: {}", report.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * The tests that what is owed affects: every test when a full run is owed, else those the owed changes affect.
     *
     * @return The selection, null when nothing is owed, in which case nothing stays owed
     */
    private @Nullable TestSelection owed(String description) {
        Set<String> changed;
        synchronized (this) {
            if (owedAll) {
                return TestSelection.all();
            }
            changed = new LinkedHashSet<>(owedChanges);
        }
        TestSelection selection = changed.isEmpty() ? null : affected(changed, description);
        if (selection == null) {
            synchronized (this) {
                owedChanges.removeAll(changed);
            }
        }
        return selection;
    }

    /**
     * Reads the class files as they are when the runtime starts: the dependency indexes, the test classes and the
     * classes declaring constants, which the first change is compared with.
     */
    void prime() {
        List<ClassDependencyIndex> scanned = scan(outputs(manifest, manifest.sourceRoots()));
        List<ClassDependencyIndex> testIndexes = scan(outputs(tests, tests.sourceRoots()));
        scanned.addAll(testIndexes);
        Set<String> testClasses = new HashSet<>();
        for (ClassDependencyIndex index : testIndexes) {
            testClasses.addAll(index.classes());
        }
        synchronized (this) {
            indexes = List.copyOf(scanned);
            knownTestClasses = Set.copyOf(testClasses);
            constantDeclarers = constantDeclarers(scanned);
        }
    }

    private TestSelection requested(TestRequest requested) {
        TestSelection previous;
        Set<String> failed;
        synchronized (this) {
            previous = lastSelection;
            failed = new LinkedHashSet<>(failedClasses);
        }
        return switch (requested) {
            case ALL -> TestSelection.all();
            case FAILED -> failed.isEmpty() ? TestSelection.ofClasses(Set.of(), "failed") : TestSelection.ofClasses(failed, "failed");
            case RERUN -> {
                if (previous.everything()) {
                    yield TestSelection.all();
                }
                TestSelection affected = owed("rerun");
                if (affected != null && affected.everything()) {
                    yield affected;
                }
                Set<String> classes = new LinkedHashSet<>(previous.classes());
                classes.addAll(failed);
                if (affected != null) {
                    classes.addAll(affected.classes());
                }
                yield new TestSelection(false, classes, previous.methods(), previous.files(), List.of(), "rerun");
            }
        };
    }

    /**
     * The test classes the changed classes can affect, with the classes that failed last; every test when a changed
     * class declares compile-time constants.
     *
     * @return The selection, null when no test depends on the change
     */
    private @Nullable TestSelection affected(Set<String> changed, String description) {
        List<ClassDependencyIndex> scanned;
        Set<String> testClasses;
        synchronized (this) {
            scanned = indexes;
            testClasses = knownTestClasses;
        }
        Set<String> reached = new LinkedHashSet<>(changed);
        Deque<String> queue = new ArrayDeque<>(changed);
        while (!queue.isEmpty()) {
            String next = queue.poll();
            for (ClassDependencyIndex index : scanned) {
                for (String dependent : index.dependentsOf(next)) {
                    if (reached.add(dependent)) {
                        queue.add(dependent);
                    }
                }
            }
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String className : reached) {
            if (testClasses.contains(className)) {
                selected.add(className);
            }
        }
        synchronized (this) {
            for (String failed : failedClasses) {
                // a class of the outputs, or a file of tests an engine discovers from their source files, as pytest's
                if (testClasses.contains(failed) || isTestFile(failed)) {
                    selected.add(failed);
                }
            }
        }
        return selected.isEmpty() ? null : TestSelection.ofClasses(selected, description);
    }

    /**
     * Whether results are grouped under a file of a test source root whose tests are discovered from their files, as
     * pytest's, rather than under a class: the file is selected again by that name.
     */
    private boolean isTestFile(String name) {
        for (SourceRoot root : tests.sourceRoots()) {
            SourceKind kind = root.kind();
            if (isJvmKind(kind)) {
                continue;
            }
            for (String extension : kind.extensions()) {
                if (name.endsWith("." + extension)
                    && (Files.isRegularFile(root.path().resolve(name).normalize()) || Files.isRegularFile(root.path().resolveSibling(name).normalize()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a name of results names a file of tests discovered from their files, whether or not it is there.
     */
    private boolean isTestFileName(String name) {
        for (SourceRoot root : tests.sourceRoots()) {
            if (!isJvmKind(root.kind())) {
                for (String extension : root.kind().extensions()) {
                    if (name.endsWith("." + extension)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The names the results of a file of tests discovered from their files may be grouped under: its path relative to
     * its test source root, or to the directory holding the root.
     */
    private Set<String> resultNamesOf(Path file) {
        Set<String> names = new LinkedHashSet<>();
        Path absolute = file.toAbsolutePath().normalize();
        for (SourceRoot root : tests.sourceRoots()) {
            Path base = root.path().toAbsolutePath().normalize();
            if (!isJvmKind(root.kind()) && absolute.startsWith(base)) {
                names.add(base.relativize(absolute).toString().replace('\\', '/'));
                if (base.getParent() != null) {
                    names.add(base.getParent().relativize(absolute).toString().replace('\\', '/'));
                }
            }
        }
        return names;
    }

    private static boolean isJvmKind(SourceKind kind) {
        return kind == SourceKind.JAVA || kind == SourceKind.KOTLIN || kind == SourceKind.GROOVY;
    }

    private static List<Path> outputs(DevManifest target, List<SourceRoot> roots) {
        Set<Path> outputs = new LinkedHashSet<>();
        for (SourceRoot root : roots) {
            outputs.add(target.classOutput(root.kind()));
        }
        return List.copyOf(outputs);
    }

    /**
     * Runs tests on a new generation.
     *
     * @param requested Which tests
     * @param coversOwed Whether the run covers what is owed, so that it is no longer owed once the run completes
     */
    private void run(TestSelection requested, boolean coversOwed) {
        TestSelection selection = requested.withPatterns(settings.patterns());
        if (selection.isEmpty()) {
            LOG.info("No test to run for {}", selection.description());
            return;
        }
        String runId;
        synchronized (this) {
            runId = "run-" + (runs + 1);
        }
        if (runtime.isGenerationBudgetSpent()) {
            // the run is owed to the relaunched process, which runs the tests again when it starts
            runtime.requestRelaunch();
            return;
        }
        ClassLoader generation = runtime.newGeneration();
        Cancellation cancellation = new Cancellation();
        current = cancellation;
        List<TestEventListener> listeners = new ArrayList<>();
        listeners.add(xml);
        listeners.addAll(reports);
        listeners.add(new FailureTracker());
        TestRunRequest request = new TestRunRequest(runId, generation, outputs(tests, tests.sourceRoots()), tests.sourceRoots(), selection, settings.parameters());
        TestRunSummary summary;
        try {
            summary = runner.run(request, TestEventListeners.composite(listeners), cancellation);
        } finally {
            current = null;
        }
        synchronized (this) {
            last = summary;
            lastSelection = requested;
            runs++;
            if (summary.complete() && (coversOwed || selection.everything() && selection.patterns().isEmpty())) {
                // the batches that arrive during a run are handled after it: what is owed now is what this run covered
                owedChanges.clear();
                owedAll = false;
            }
            notifyAll();
        }
        LOG.info("{} {}: {} passed, {} failed, {} errored, {} skipped in {} ms{}", selection.everything() ? "All tests" : "Tests " + selection.description(),
            summary.isSuccess() ? "passed" : "failed", summary.passed(), summary.failed(), summary.errored(), summary.skipped(), summary.duration().toMillis(),
            summary.cancelled() ? " (cancelled)" : "");
        runtime.detectLeaks();
    }

    private void compilationFailed(CompileFailure failure, Map<SourceKind, DevRuntime.SourceChanges> sources, Map<SourceKind, DevRuntime.SourceChanges> testSources) {
        runtime.compilationFailed(failure);
        synchronized (this) {
            compileFailed = true;
            retrySources.clear();
            retrySources.putAll(sources);
            retryTestSources.clear();
            retryTestSources.putAll(testSources);
        }
        for (TestReportListener report : reports) {
            try {
                report.compilationFailed(failure);
            } catch (RuntimeException e) {
                LOG.error("The test report {} failed: {}", report.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    private void compilationRecovered() {
        runtime.compilationRecovered();
        boolean recovered;
        synchronized (this) {
            recovered = compileFailed;
            compileFailed = false;
        }
        if (recovered) {
            for (TestReportListener report : reports) {
                try {
                    report.compilationRecovered();
                } catch (RuntimeException e) {
                    LOG.error("The test report {} failed: {}", report.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Asks the run under way, if any, to stop: a change arrived that the next run covers.
     */
    void cancelRun() {
        Cancellation cancellation = current;
        if (cancellation != null) {
            cancellation.cancel();
        }
    }

    void watching(boolean watching) {
        this.watching = watching;
    }

    boolean isWatching() {
        return watching;
    }

    synchronized @Nullable TestRunSummary lastRun() {
        return last;
    }

    synchronized int runs() {
        return runs;
    }

    synchronized Set<String> failedClasses() {
        return Set.copyOf(failedClasses);
    }

    /**
     * Waits for a run to finish.
     *
     * @param run The run, counted from one
     * @param timeout How long to wait
     * @return Its summary
     * @throws TimeoutException if it did not finish in time
     */
    synchronized TestRunSummary awaitRun(int run, Duration timeout) throws TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (runs < run) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException("Test run " + run + " did not finish within " + timeout);
            }
            try {
                wait(Math.max(1, remaining / 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TimeoutException("Interrupted while waiting for test run " + run);
            }
        }
        TestRunSummary summary = last;
        if (summary == null) {
            throw new IllegalStateException("No test run finished");
        }
        return summary;
    }

    /**
     * Keeps the classes with failures: a complete run replaces what it knows of the classes it ran, one cut short
     * only adds to it.
     */
    private final class FailureTracker implements TestEventListener {
        private final Set<String> ran = new HashSet<>();
        private final Set<String> failed = new LinkedHashSet<>();

        @Override
        public void runStarted(TestRunStarted event) {
            ran.clear();
            failed.clear();
        }

        @Override
        public void testFinished(TestId test, TestOutcome outcome) {
            ran.add(test.className());
            if (outcome.status().isFailure()) {
                failed.add(test.className());
            }
        }

        @Override
        public void runFinished(TestRunSummary summary) {
            synchronized (TestSession.this) {
                if (summary.complete()) {
                    failedClasses.removeAll(ran);
                }
                failedClasses.addAll(failed);
            }
        }
    }
}
