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
package io.micronaut.dev.test;

import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * One run on the JUnit Platform, for {@link JUnitPlatformTestRunner}, which loads this class only when the
 * platform is present.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class JUnitPlatformExecution {

    private static final String CAPTURE_STDOUT = "junit.platform.output.capture.stdout";
    private static final String CAPTURE_STDERR = "junit.platform.output.capture.stderr";
    private static final String STDOUT_KEY = "stdout";
    private static final String STDERR_KEY = "stderr";
    private static final String CLASS_ERROR = "initializationError";
    // the languages whose tests are classes of the test class output, which the class path roots select
    private static final Set<SourceKind> JVM_KINDS = Set.of(SourceKind.JAVA, SourceKind.KOTLIN, SourceKind.GROOVY);

    private final TestRunRequest request;
    private final TestEventListener listener;
    private final Cancellation cancellation;
    private final Map<String, Long> started = new ConcurrentHashMap<>();
    private final Set<String> finished = ConcurrentHashMap.newKeySet();
    private int passed;
    private int failed;
    private int errored;
    private int skipped;
    @Nullable
    private TestPlan plan;

    JUnitPlatformExecution(TestRunRequest request, TestEventListener listener, Cancellation cancellation) {
        this.request = request;
        this.listener = listener;
        this.cancellation = cancellation;
    }

    TestRunSummary run() {
        long start = System.nanoTime();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(request.classLoader());
        boolean cancelled = cancellation.isCancelled();
        boolean aborted = false;
        try {
            listener.runStarted(new TestRunStarted(request.runId(), JUnitPlatformTestRunner.ID, request.selection(), Instant.now()));
            try {
                if (cancellation.isCancelled()) {
                    cancelled = true;
                } else {
                    Launcher launcher = LauncherFactory.create();
                    // discovery can take a while: a cancellation that comes meanwhile stops the run before any test runs
                    TestPlan testPlan = launcher.discover(discoveryRequest());
                    if (cancellation.isCancelled()) {
                        cancelled = true;
                    } else {
                        launcher.execute(testPlan, new Listener());
                        // the platform this runs on cannot stop a run under way: a cancellation that came meanwhile still counts
                        cancelled = cancellation.isCancelled();
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                // discovery or an engine failed before any test could report it: the run is not complete
                aborted = true;
                report(new TestId(request.runId() + "/launcher", JUnitPlatformTestRunner.ID, CLASS_ERROR, "The JUnit Platform failed"),
                    new TestOutcome(TestStatus.ERRORED, Duration.ZERO, TestFailure.of(e), null));
            }
            TestRunSummary summary = new TestRunSummary(request.runId(), passed, failed, errored, skipped, Duration.ofNanos(System.nanoTime() - start),
                cancelled, !cancelled && !aborted);
            // the last event too, under the run's loader: a report may load the project's resources through it
            listener.runFinished(summary);
            return summary;
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private LauncherDiscoveryRequest discoveryRequest() {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
        TestSelection selection = request.selection();
        List<DiscoverySelector> selectors = new ArrayList<>();
        if (selection.everything()) {
            selectors.addAll(DiscoverySelectors.selectClasspathRoots(new LinkedHashSet<>(request.testClassOutputs())));
            // the tests an engine discovers from their source files, as pytest's, are in no class output
            for (SourceRoot root : request.testSources()) {
                if (!JVM_KINDS.contains(root.kind()) && Files.isDirectory(root.path())) {
                    selectors.add(DiscoverySelectors.selectDirectory(root.path().toFile()));
                }
            }
        } else {
            for (String className : selection.classes()) {
                // a test without a class, as a pytest function, is grouped under its file: the file is selected
                if (isFileName(className)) {
                    // a file that is gone runs nothing: the run, complete, clears its results
                    Path file = sourceFile(className);
                    if (file != null) {
                        selectors.add(DiscoverySelectors.selectFile(file.toFile()));
                    }
                    continue;
                }
                Class<?> type = load(className);
                if (type != null) {
                    selectors.add(DiscoverySelectors.selectClass(type));
                }
            }
            for (String method : selection.methods()) {
                int hash = method.indexOf('#');
                if (hash <= 0 || hash == method.length() - 1) {
                    throw new IllegalArgumentException("A test method is selected as className#methodName: " + method);
                }
                Class<?> type = load(method.substring(0, hash));
                if (type != null) {
                    selectors.add(DiscoverySelectors.selectMethod(type, method.substring(hash + 1)));
                }
            }
            for (Path file : selection.files()) {
                selectors.add(DiscoverySelectors.selectFile(file.toFile()));
            }
        }
        builder.selectors(selectors);
        if (!selection.patterns().isEmpty()) {
            builder.filters(new PatternFilter(selection.patterns()));
        }
        Map<String, String> parameters = new HashMap<>();
        parameters.put(CAPTURE_STDOUT, "true");
        parameters.put(CAPTURE_STDERR, "true");
        parameters.putAll(request.parameters());
        builder.configurationParameters(parameters);
        return builder.build();
    }

    /**
     * Whether a name of results is a file's rather than a class's: a binary name holds no separator and does not
     * end with a source file's extension.
     */
    private boolean isFileName(String name) {
        if (name.contains("/") || name.contains("\\")) {
            return true;
        }
        for (SourceRoot root : request.testSources()) {
            if (!JVM_KINDS.contains(root.kind())) {
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
     * The test source file a name of results stands for: a path to a file of a test source root of a language whose
     * tests are discovered from their files, rather than a class name.
     */
    @Nullable
    private Path sourceFile(String name) {
        Path given;
        try {
            given = Path.of(name);
        } catch (InvalidPathException e) {
            return null;
        }
        for (SourceRoot root : request.testSources()) {
            if (JVM_KINDS.contains(root.kind())) {
                continue;
            }
            Path base = root.path().toAbsolutePath().normalize();
            // relative to the root, or to the directory holding it, as a file's results are named
            List<Path> candidates = given.isAbsolute() ? List.of(given) : base.getParent() == null ? List.of(base.resolve(given)) : List.of(base.resolve(given), base.getParent().resolve(given));
            for (Path candidate : candidates) {
                Path file = candidate.normalize();
                if (file.startsWith(base) && Files.isRegularFile(file)) {
                    return file;
                }
            }
        }
        return null;
    }

    /**
     * Loads a selected class through the request's loader, without initializing it; a class that is not there is
     * reported as an error of its own rather than failing the run.
     */
    @Nullable
    private Class<?> load(String className) {
        try {
            return Class.forName(className, false, request.classLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            report(new TestId(request.runId() + "/" + className, className, CLASS_ERROR, className),
                new TestOutcome(TestStatus.ERRORED, Duration.ZERO, TestFailure.of(e), null));
            return null;
        }
    }

    private synchronized void report(TestId test, TestOutcome outcome) {
        if (!finished.add(test.uniqueId())) {
            return;
        }
        switch (outcome.status()) {
            case PASSED -> passed++;
            case FAILED -> failed++;
            case ERRORED -> errored++;
            case SKIPPED -> skipped++;
            default -> throw new IllegalStateException("Unknown status " + outcome.status());
        }
        listener.testFinished(test, outcome);
    }

    private TestId testId(TestIdentifier identifier) {
        return new TestId(identifier.getUniqueId(), classNameOf(identifier), identifier.getLegacyReportingName(), identifier.getDisplayName());
    }

    /**
     * What a report groups a test under: the class of its own source or of its nearest container's, the file of
     * a test declared in a file, or else the nearest container's name.
     */
    private String classNameOf(TestIdentifier identifier) {
        TestPlan testPlan = plan;
        TestIdentifier current = identifier;
        while (current != null) {
            Optional<TestSource> source = current.getSource();
            if (source.isPresent()) {
                TestSource testSource = source.get();
                if (testSource instanceof MethodSource method) {
                    return fileOrClass(method.getClassName());
                }
                if (testSource instanceof ClassSource type) {
                    return fileOrClass(type.getClassName());
                }
                if (testSource instanceof FileSource file) {
                    return relativeFile(file.getFile().toPath());
                }
            }
            current = testPlan == null ? null : testPlan.getParent(current).orElse(null);
        }
        if (testPlan != null) {
            Optional<TestIdentifier> parent = testPlan.getParent(identifier);
            if (parent.isPresent()) {
                return parent.get().getLegacyReportingName();
            }
        }
        return JUnitPlatformTestRunner.ID;
    }

    /**
     * A class name as it is, or, when an engine names a test's class by its file's absolute path, as pytest's does, that
     * file relative to its test source root, as a test declared in a file is named.
     */
    private String fileOrClass(String className) {
        // a binary name is never an absolute path, on any platform
        try {
            Path file = Path.of(className);
            if (file.isAbsolute()) {
                return relativeFile(file);
            }
        } catch (InvalidPathException e) {
            // not a path: a class name
        }
        return className;
    }

    /**
     * A test file's path relative to the test source root that holds it, so reports read the same on every machine.
     */
    private String relativeFile(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        for (var root : request.testSources()) {
            Path rootPath = root.path().toAbsolutePath().normalize();
            if (absolute.startsWith(rootPath)) {
                return rootPath.relativize(absolute).toString().replace('\\', '/');
            }
        }
        // an engine may name the file by its real path, through the links the root's path goes through, as /var on macOS
        Path real = realPath(absolute);
        for (var root : request.testSources()) {
            Path rootPath = realPath(root.path().toAbsolutePath().normalize());
            if (real.startsWith(rootPath)) {
                return rootPath.relativize(real).toString().replace('\\', '/');
            }
        }
        return absolute.toString();
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    private static TestOutcome outcome(TestExecutionResult result, Duration duration) {
        Throwable throwable = result.getThrowable().orElse(null);
        return switch (result.getStatus()) {
            case SUCCESSFUL -> new TestOutcome(TestStatus.PASSED, duration, null, null);
            case ABORTED -> new TestOutcome(TestStatus.SKIPPED, duration, null, throwable == null ? null : throwable.getMessage());
            case FAILED -> new TestOutcome(throwable instanceof AssertionError ? TestStatus.FAILED : TestStatus.ERRORED, duration,
                throwable == null ? null : TestFailure.of(throwable), null);
        };
    }

    /**
     * Reports the events of the platform as the listener's, one at a time: an engine that runs tests in parallel
     * calls it from several threads, and the listener expects one sequence of events.
     */
    private final class Listener implements TestExecutionListener {

        @Override
        public void testPlanExecutionStarted(TestPlan testPlan) {
            plan = testPlan;
        }

        @Override
        public synchronized void executionStarted(TestIdentifier identifier) {
            if (identifier.isTest()) {
                started.put(identifier.getUniqueId(), System.nanoTime());
                listener.testStarted(testId(identifier));
            }
        }

        @Override
        public synchronized void executionSkipped(TestIdentifier identifier, String reason) {
            if (identifier.isTest()) {
                report(testId(identifier), new TestOutcome(TestStatus.SKIPPED, Duration.ZERO, null, reason));
            } else {
                skipDescendants(identifier, reason);
            }
        }

        @Override
        public synchronized void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
            if (identifier.isTest()) {
                Long since = started.remove(identifier.getUniqueId());
                Duration duration = since == null ? Duration.ZERO : Duration.ofNanos(System.nanoTime() - since);
                report(testId(identifier), outcome(result, duration));
                return;
            }
            switch (result.getStatus()) {
                case FAILED -> {
                    // a class that failed to set up or tear down: its tests never ran, or ran and the class failed after
                    Throwable throwable = result.getThrowable().orElse(null);
                    String className = classNameOf(identifier);
                    report(new TestId(identifier.getUniqueId() + "/" + CLASS_ERROR, className, CLASS_ERROR, identifier.getDisplayName()),
                        new TestOutcome(throwable instanceof AssertionError ? TestStatus.FAILED : TestStatus.ERRORED, Duration.ZERO,
                            throwable == null ? null : TestFailure.of(throwable), null));
                    skipDescendants(identifier, "The test class failed: " + (throwable == null ? "unknown" : throwable.getMessage()));
                }
                case ABORTED -> skipDescendants(identifier, result.getThrowable().map(Throwable::getMessage).orElse("Aborted"));
                default -> {
                    // a container that succeeded reports nothing of its own
                }
            }
        }

        @Override
        public synchronized void reportingEntryPublished(TestIdentifier identifier, ReportEntry entry) {
            if (!identifier.isTest()) {
                return;
            }
            TestId test = testId(identifier);
            Map<String, String> values = new LinkedHashMap<>(entry.getKeyValuePairs());
            String out = values.get(STDOUT_KEY);
            if (out != null) {
                listener.output(test, TestOutput.STDOUT, out);
            }
            String err = values.get(STDERR_KEY);
            if (err != null) {
                listener.output(test, TestOutput.STDERR, err);
            }
        }

        /**
         * The tests of a container that will not run, reported as skipped unless they already ended.
         */
        private void skipDescendants(TestIdentifier container, @Nullable String reason) {
            TestPlan testPlan = plan;
            if (testPlan == null) {
                return;
            }
            for (TestIdentifier descendant : testPlan.getDescendants(container)) {
                if (descendant.isTest() && !started.containsKey(descendant.getUniqueId())) {
                    report(testId(descendant), new TestOutcome(TestStatus.SKIPPED, Duration.ZERO, null, reason));
                }
            }
        }
    }

    /**
     * The {@code --tests} patterns, matched as the build tools match them: a test runs when a pattern matches its
     * class, or its class and method joined by a dot, by the class's binary name or its simple name. {@code *}
     * matches any characters, dots included, so {@code CalculatorTest.*}, {@code *.saves*} and
     * {@code com.example.*Test} all work. Only tests are filtered: the platform prunes the containers left without
     * any. A container declared by a method, such as a {@code @TestFactory} or a parameterized test, is matched as
     * its method is. A test's class and method come from its own source or, when it has none of its own, from the nearest
     * container's: the method is then its display name, and a file stands for the class of tests declared in a
     * file. A test with no source anywhere above it is kept, since nothing can be matched.
     */
    private static final class PatternFilter implements PostDiscoveryFilter {

        private final List<TestPattern> patterns = new ArrayList<>();

        PatternFilter(List<String> patterns) {
            for (String pattern : patterns) {
                this.patterns.add(TestPattern.of(pattern));
            }
        }

        @Override
        public FilterResult apply(org.junit.platform.engine.TestDescriptor descriptor) {
            // a container declared by a method, a @TestFactory or a parameterized test, is a test method as far as the
            // patterns go: its tests exist only once it runs, past this filter
            boolean methodContainer = descriptor.getSource().orElse(null) instanceof MethodSource;
            if (!descriptor.isTest() && !methodContainer) {
                return FilterResult.included("not a test");
            }
            String className = null;
            String methodName = descriptor.getDisplayName();
            org.junit.platform.engine.TestDescriptor current = descriptor;
            while (current != null && className == null) {
                TestSource source = current.getSource().orElse(null);
                if (source instanceof MethodSource method) {
                    className = method.getClassName();
                    if (current == descriptor) {
                        methodName = method.getMethodName();
                    }
                } else if (source instanceof ClassSource type) {
                    className = type.getClassName();
                } else if (source instanceof FileSource file) {
                    className = file.getFile().getName();
                }
                current = current.getParent().orElse(null);
            }
            if (className == null) {
                return FilterResult.included("no class or file to match");
            }
            for (TestPattern pattern : patterns) {
                if (pattern.matches(className, methodName)) {
                    return FilterResult.included("matches " + pattern.text());
                }
            }
            return FilterResult.excluded("matches no --tests pattern");
        }
    }

    /**
     * One {@code --tests} pattern.
     *
     * @param text The pattern as given
     * @param pattern The pattern as a regular expression
     */
    record TestPattern(String text, Pattern pattern) {

        static TestPattern of(String text) {
            return new TestPattern(text, glob(text.strip()));
        }

        boolean matches(String className, String methodName) {
            String simple = className.substring(className.lastIndexOf('.') + 1);
            return pattern.matcher(className).matches()
                || pattern.matcher(simple).matches()
                || pattern.matcher(className + "." + methodName).matches()
                || pattern.matcher(simple + "." + methodName).matches();
        }

        private static Pattern glob(String glob) {
            StringBuilder regex = new StringBuilder();
            for (String part : glob.split("\\*", -1)) {
                if (!regex.isEmpty()) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(part));
            }
            return Pattern.compile(regex.toString());
        }
    }
}
