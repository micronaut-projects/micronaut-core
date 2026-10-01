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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The latest result of every test, across the runs a report sees, so that a report holds each class's latest results
 * rather than only those of the last run. The JUnit XML reports and the HTML reports keep them the same way.
 *
 * <p>A complete run of every test with no pattern replaces them all: a class it did not see is gone. A complete run of
 * classes or methods selected by name replaces their results, a test that is gone with them, the classes nested in a
 * selected class and the tests a selected method produced included, and a complete run of test files replaces the classes it reported. Any other run, one filtered by patterns,
 * cancelled, or cut short by the runner, only adds its results, since it says nothing of the tests it did not reach.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class TestResults {

    private final int maxOutput;
    private final Map<String, Map<String, Result>> latest = new LinkedHashMap<>();
    private final Map<String, Map<String, Result>> run = new LinkedHashMap<>();
    private final Map<String, Pending> running = new LinkedHashMap<>();
    private Instant startedAt = Instant.now();
    private TestSelection selection = TestSelection.all();

    /**
     * Results that keep all the output tests write.
     */
    public TestResults() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Results that keep at most this many characters of each stream a test writes, and how many more there were.
     *
     * @param maxOutput The characters kept per stream and test
     */
    public TestResults(int maxOutput) {
        if (maxOutput < 0) {
            throw new IllegalArgumentException("maxOutput: " + maxOutput);
        }
        this.maxOutput = maxOutput;
    }

    /**
     * A run starts.
     *
     * @param event The run
     */
    public synchronized void runStarted(TestRunStarted event) {
        run.clear();
        running.clear();
        startedAt = event.startedAt();
        selection = event.selection();
    }

    /**
     * A test starts.
     *
     * @param test The test
     */
    public synchronized void testStarted(TestId test) {
        running.put(test.uniqueId(), new Pending());
    }

    /**
     * A test wrote to a stream.
     *
     * @param test The test
     * @param stream The stream
     * @param text The text
     */
    public synchronized void output(TestId test, TestOutput stream, String text) {
        Pending current = running.computeIfAbsent(test.uniqueId(), id -> new Pending());
        (stream == TestOutput.STDOUT ? current.out : current.err).append(text, maxOutput);
    }

    /**
     * A test finished.
     *
     * @param test The test
     * @param outcome How it ended
     * @return Its result, with what it wrote
     */
    public synchronized Result testFinished(TestId test, TestOutcome outcome) {
        Pending pending = running.remove(test.uniqueId());
        Result result = new Result(test, outcome, pending == null ? "" : pending.out.text(), pending == null ? "" : pending.err.text());
        run.computeIfAbsent(test.className(), name -> new LinkedHashMap<>()).put(test.uniqueId(), result);
        return result;
    }

    /**
     * The run finished: its results are applied.
     *
     * @param summary What it did
     * @return The classes whose results changed, and whether the run replaced every result
     */
    public synchronized Applied runFinished(TestRunSummary summary) {
        Set<String> touched = new LinkedHashSet<>(run.keySet());
        boolean replaces = summary.complete() && selection.patterns().isEmpty();
        boolean full = replaces && selection.everything();
        if (full) {
            touched.addAll(latest.keySet());
            latest.clear();
        } else if (replaces) {
            for (String className : selection.classes()) {
                latest.keySet().removeIf(name -> {
                    boolean replaced = name.equals(className) || name.startsWith(className + "$");
                    if (replaced) {
                        touched.add(name);
                    }
                    return replaced;
                });
                touched.add(className);
            }
            for (String method : selection.methods()) {
                int hash = method.indexOf('#');
                if (hash > 0) {
                    String className = method.substring(0, hash);
                    String methodName = method.substring(hash + 1);
                    Map<String, Result> results = latest.get(className);
                    if (results != null) {
                        results.values().removeIf(result -> isOfMethod(result.test(), methodName));
                    }
                    touched.add(className);
                }
            }
            if (!selection.files().isEmpty()) {
                // a file ran in full: the classes it reported replace theirs, a test deleted from the file with them,
                // except a class of which only selected methods ran
                Set<String> partial = new LinkedHashSet<>();
                for (String method : selection.methods()) {
                    int hash = method.indexOf('#');
                    if (hash > 0) {
                        partial.add(method.substring(0, hash));
                    }
                }
                Set<String> replaced = new LinkedHashSet<>(run.keySet());
                // and the tests grouped under a selected file, which reports none when its last test is gone
                for (String className : latest.keySet()) {
                    if (isOfFiles(className, selection.files())) {
                        replaced.add(className);
                    }
                }
                for (String className : replaced) {
                    if (!partial.contains(className) && latest.remove(className) != null) {
                        touched.add(className);
                    }
                }
            }
        }
        run.forEach((className, results) -> latest.computeIfAbsent(className, name -> new LinkedHashMap<>()).putAll(results));
        latest.values().removeIf(Map::isEmpty);
        return new Applied(Set.copyOf(touched), full);
    }

    /**
     * Forgets a test class whose source is gone, and the classes nested in it.
     *
     * @param className The class, by binary name
     * @return The names forgotten, the class itself included
     */
    public synchronized Set<String> remove(String className) {
        Set<String> gone = new LinkedHashSet<>();
        for (String name : latest.keySet()) {
            if (name.equals(className) || name.startsWith(className + "$")) {
                gone.add(name);
            }
        }
        gone.add(className);
        gone.forEach(latest::remove);
        return gone;
    }

    /**
     * @return The results of the run in progress so far, by class, before the run applies them
     */
    public synchronized Map<String, List<Result>> inProgress() {
        Map<String, List<Result>> current = new LinkedHashMap<>();
        run.forEach((className, results) -> current.put(className, List.copyOf(results.values())));
        return current;
    }

    /**
     * @param className A class, or the file of tests without one
     * @return Its latest results, empty when it has none
     */
    public synchronized List<Result> of(String className) {
        Map<String, Result> results = latest.get(className);
        return results == null ? List.of() : List.copyOf(results.values());
    }

    /**
     * @return The latest results, by class
     */
    public synchronized Map<String, List<Result>> all() {
        Map<String, List<Result>> all = new LinkedHashMap<>();
        latest.forEach((className, results) -> all.put(className, List.copyOf(results.values())));
        return all;
    }

    /**
     * @return When the last run started
     */
    public synchronized Instant startedAt() {
        return startedAt;
    }

    /**
     * @return What the last run selected
     */
    public synchronized TestSelection selection() {
        return selection;
    }

    /**
     * Whether results are grouped under one of these files: the name is the file's path, relative or not, as a runner
     * without classes, such as pytest, groups its tests.
     */
    private static boolean isOfFiles(String className, Set<Path> files) {
        Path grouped;
        try {
            grouped = Path.of(className).normalize();
        } catch (InvalidPathException e) {
            return false;
        }
        if (grouped.getNameCount() < 2 && !grouped.isAbsolute()) {
            // a class name, or a bare file name that cannot tell two directories apart
            return files.stream().anyMatch(file -> file.normalize().equals(grouped));
        }
        for (Path file : files) {
            Path normalized = file.normalize();
            if (normalized.equals(grouped) || normalized.endsWith(grouped) || grouped.endsWith(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a test belongs to a method: it is the method, {@code adds()}, an invocation of it, {@code adds(int)[1]},
     * or a test the method produced, as a {@code @TestFactory}'s dynamic tests, whose unique identifier descends from
     * a segment naming the method, such as {@code [test-factory:generated()]}.
     */
    private static boolean isOfMethod(TestId test, String methodName) {
        String name = test.name();
        if (name.equals(methodName) || name.startsWith(methodName + "(") || name.startsWith(methodName + "[")) {
            return true;
        }
        String uniqueId = test.uniqueId();
        return uniqueId.contains(":" + methodName + "(") || uniqueId.contains(":" + methodName + "]");
    }

    /**
     * The latest result of a test.
     *
     * @param test The test
     * @param outcome How it ended
     * @param out What it wrote to standard output
     * @param err What it wrote to standard error
     */
    public record Result(TestId test, TestOutcome outcome, String out, String err) {
    }

    /**
     * What a finished run changed.
     *
     * @param classes The classes whose results changed, with or without results now
     * @param full Whether the run replaced every result, so that anything kept elsewhere for another class is stale
     */
    public record Applied(Set<String> classes, boolean full) {
    }

    /**
     * A test that started and has not finished: what it wrote so far.
     */
    private static final class Pending {
        private final Output out = new Output();
        private final Output err = new Output();
    }

    /**
     * What a test wrote to one stream, up to a limit, and how much more.
     */
    private static final class Output {
        private final StringBuilder text = new StringBuilder();
        private long dropped;

        void append(String more, int max) {
            int room = max - text.length();
            if (more.length() <= room) {
                text.append(more);
            } else {
                text.append(more, 0, Math.max(room, 0));
                dropped += more.length() - Math.max(room, 0);
            }
        }

        String text() {
            return dropped == 0 ? text.toString() : text + "\n… " + dropped + " more characters not kept";
        }
    }
}
