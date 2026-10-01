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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Writes the JUnit XML reports of a run, the format CI servers, IDEs and the build tools read: one
 * {@code TEST-<class>.xml} per class in the Ant and Surefire shape, a {@code testsuite} of {@code testcase}s
 * with their {@code classname}, {@code time}, failure, error or skip, and captured {@code system-out} and
 * {@code system-err}. A test without a class, such as a pytest function, is grouped under its file.
 *
 * <p>The writer keeps the latest result of every test across the runs it sees, so a report always holds a
 * class's latest results rather than only those of the last run. A complete run of every test with no pattern
 * replaces them all, and removes the reports of classes it did not see, because they were deleted or renamed.
 * A complete run of classes or methods selected by name replaces their results, removing a test that is gone,
 * and the report of a class left with none. Any other run, one filtered by patterns, cancelled, or cut short by
 * the runner, only adds its results, since it says nothing of the tests it did not reach. Results kept are
 * those of this writer's runs: a report an earlier process wrote is replaced when its class runs again. Each
 * report is replaced through a rename, so a reader never sees half a file, and characters XML cannot hold are
 * replaced. The run's selection is recorded as the
 * {@code micronaut.dev.test.selection} property, so a reader can tell an affected run from a full one.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class JUnitXmlReportWriter implements TestReportListener {

    /**
     * The property recording why the run selected its tests.
     */
    public static final String SELECTION_PROPERTY = "micronaut.dev.test.selection";

    private static final Logger LOG = LoggerFactory.getLogger(JUnitXmlReportWriter.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String REPORT_PREFIX = "TEST-";
    private static final String REPORT_SUFFIX = ".xml";

    private final Path directory;
    // the latest result of every test, across the runs of this writer, by class and by test
    private final Map<String, Map<String, Case>> latest = new LinkedHashMap<>();
    private final Map<String, Map<String, Case>> run = new LinkedHashMap<>();
    private final Map<String, Case> running = new LinkedHashMap<>();
    private Instant startedAt = Instant.now();
    private TestSelection selection = TestSelection.all();

    /**
     * @param directory Where the reports go
     */
    public JUnitXmlReportWriter(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * @return Where the reports go
     */
    public Path directory() {
        return directory;
    }

    @Override
    public synchronized void runStarted(TestRunStarted event) {
        run.clear();
        running.clear();
        startedAt = event.startedAt();
        selection = event.selection();
    }

    @Override
    public synchronized void testStarted(TestId test) {
        running.put(test.uniqueId(), new Case(test));
    }

    @Override
    public synchronized void output(TestId test, TestOutput stream, String text) {
        Case current = running.computeIfAbsent(test.uniqueId(), id -> new Case(test));
        (stream == TestOutput.STDOUT ? current.out : current.err).append(text);
    }

    @Override
    public synchronized void testFinished(TestId test, TestOutcome outcome) {
        Case finished = running.remove(test.uniqueId());
        if (finished == null) {
            finished = new Case(test);
        }
        finished.outcome = outcome;
        run.computeIfAbsent(test.className(), name -> new LinkedHashMap<>()).put(test.uniqueId(), finished);
    }

    @Override
    public synchronized void runFinished(TestRunSummary summary) {
        Set<String> touched = new LinkedHashSet<>(run.keySet());
        boolean replaces = summary.complete() && selection.patterns().isEmpty();
        boolean full = replaces && selection.everything();
        if (full) {
            // every test there is ran: whatever the earlier runs saw and this one did not is gone
            touched.addAll(latest.keySet());
            latest.clear();
        } else if (replaces) {
            // what was selected by name ran in full: its earlier results are replaced, a test that is gone with them
            for (String className : selection.classes()) {
                // the class and the classes nested in it, whose tests are grouped under their own names
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
                    Map<String, Case> cases = latest.get(className);
                    if (cases != null) {
                        cases.values().removeIf(test -> isOfMethod(test.id, methodName));
                    }
                    touched.add(className);
                }
            }
        }
        run.forEach((className, cases) -> latest.computeIfAbsent(className, name -> new LinkedHashMap<>()).putAll(cases));
        try {
            Files.createDirectories(directory);
            Set<Path> written = new HashSet<>();
            for (String className : touched) {
                Map<String, Case> cases = latest.get(className);
                Path report = reportOf(className);
                if (cases == null || cases.isEmpty()) {
                    latest.remove(className);
                    Files.deleteIfExists(report);
                } else {
                    write(className, cases.values());
                    written.add(report);
                }
            }
            if (full) {
                // reports an earlier process left for classes that are gone
                try (Stream<Path> reports = Files.list(directory)) {
                    for (Path report : reports.filter(JUnitXmlReportWriter::isReport).toList()) {
                        if (!written.contains(report)) {
                            Files.deleteIfExists(report);
                        }
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            LOG.error("Cannot write the test reports to {}: {}", directory, e.getMessage(), e);
        }
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
     * The report file of a class: {@code TEST-<class>.xml} with the binary name as it is, as Surefire names it. A
     * name that is not a Java binary name, such as a test file's path, is escaped so that two names never share a
     * file: every character but an ASCII letter, a digit, {@code .}, {@code _} and {@code $} becomes {@code -} and
     * the two hex digits of each of its UTF-8 bytes. A binary name never holds a {@code -}, and an escaped name
     * always does, so the two kinds cannot meet.
     *
     * @param className The class, or the file of tests without one
     * @return The file
     */
    public Path reportOf(String className) {
        StringBuilder name = new StringBuilder(REPORT_PREFIX);
        if (isBinaryName(className)) {
            name.append(className);
        } else {
            for (byte b : className.getBytes(StandardCharsets.UTF_8)) {
                char c = (char) (b & 0xFF);
                if (c < 0x80 && (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '$')) {
                    name.append(c);
                } else {
                    name.append('-').append(String.format(Locale.ROOT, "%02X", b & 0xFF));
                }
            }
        }
        return directory.resolve(name.append(REPORT_SUFFIX).toString());
    }

    private static boolean isBinaryName(String name) {
        if (name.isEmpty() || name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c < 0x80 && (Character.isLetterOrDigit(c) || c == '.' || c == '$' || c == '_'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReport(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith(REPORT_PREFIX) && name.endsWith(REPORT_SUFFIX) && Files.isRegularFile(file);
    }

    private void write(String className, Collection<Case> cases) throws IOException {
        Path report = reportOf(className);
        Path temporary = report.resolveSibling(report.getFileName() + ".tmp");
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        Duration time = Duration.ZERO;
        for (Case test : cases) {
            TestOutcome outcome = test.outcome();
            time = time.plus(outcome.duration());
            switch (outcome.status()) {
                case FAILED -> failures++;
                case ERRORED -> errors++;
                case SKIPPED -> skipped++;
                default -> {
                    // passed
                }
            }
        }
        try (OutputStream out = Files.newOutputStream(temporary)) {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            xml.writeStartElement("testsuite");
            xml.writeAttribute("name", clean(className));
            xml.writeAttribute("tests", String.valueOf(cases.size()));
            xml.writeAttribute("skipped", String.valueOf(skipped));
            xml.writeAttribute("failures", String.valueOf(failures));
            xml.writeAttribute("errors", String.valueOf(errors));
            xml.writeAttribute("timestamp", TIMESTAMP.format(LocalDateTime.ofInstant(startedAt.truncatedTo(ChronoUnit.SECONDS), ZoneId.systemDefault())));
            xml.writeAttribute("time", seconds(time));
            xml.writeCharacters("\n  ");
            xml.writeStartElement("properties");
            xml.writeCharacters("\n    ");
            xml.writeEmptyElement("property");
            xml.writeAttribute("name", SELECTION_PROPERTY);
            xml.writeAttribute("value", clean(selection.description()));
            xml.writeCharacters("\n  ");
            xml.writeEndElement();
            for (Case test : cases) {
                writeCase(xml, className, test);
            }
            xml.writeCharacters("\n");
            xml.writeEndElement();
            xml.writeCharacters("\n");
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot write " + report + ": " + e.getMessage(), e);
        }
        try {
            Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeCase(XMLStreamWriter xml, String className, Case test) throws XMLStreamException {
        TestOutcome outcome = test.outcome();
        xml.writeCharacters("\n  ");
        xml.writeStartElement("testcase");
        xml.writeAttribute("name", clean(test.id.name()));
        xml.writeAttribute("classname", clean(className));
        xml.writeAttribute("time", seconds(outcome.duration()));
        TestFailure failure = outcome.failure();
        switch (outcome.status()) {
            case FAILED, ERRORED -> {
                xml.writeCharacters("\n    ");
                xml.writeStartElement(outcome.status() == TestStatus.FAILED ? "failure" : "error");
                if (failure != null) {
                    if (failure.message() != null) {
                        xml.writeAttribute("message", clean(failure.message()));
                    }
                    xml.writeAttribute("type", clean(failure.type()));
                    xml.writeCharacters(clean(failure.stackTrace()));
                }
                xml.writeEndElement();
            }
            case SKIPPED -> {
                xml.writeCharacters("\n    ");
                xml.writeEmptyElement("skipped");
                if (outcome.skipReason() != null) {
                    xml.writeAttribute("message", clean(outcome.skipReason()));
                }
            }
            default -> {
                // passed
            }
        }
        writeOutput(xml, "system-out", test.out);
        writeOutput(xml, "system-err", test.err);
        xml.writeCharacters("\n  ");
        xml.writeEndElement();
    }

    private static void writeOutput(XMLStreamWriter xml, String element, StringBuilder text) throws XMLStreamException {
        if (text.isEmpty()) {
            return;
        }
        xml.writeCharacters("\n    ");
        xml.writeStartElement(element);
        xml.writeCharacters(clean(text.toString()));
        xml.writeEndElement();
    }

    private static String seconds(Duration duration) {
        return String.format(Locale.ROOT, "%.3f", duration.toNanos() / 1_000_000_000.0);
    }

    /**
     * Replaces what XML 1.0 cannot hold, control characters and unpaired surrogates, with a replacement character.
     */
    static String clean(String text) {
        StringBuilder cleaned = null;
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            boolean allowed = codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
            if (!allowed && cleaned == null) {
                cleaned = new StringBuilder(text.length()).append(text, 0, i);
            }
            if (cleaned != null) {
                if (allowed) {
                    cleaned.appendCodePoint(codePoint);
                } else {
                    cleaned.append('\uFFFD');
                }
            }
            if (Character.isSupplementaryCodePoint(codePoint)) {
                i++;
            }
        }
        return cleaned == null ? text : cleaned.toString();
    }

    /**
     * One test case of a report.
     */
    private static final class Case {
        private final TestId id;
        private final StringBuilder out = new StringBuilder();
        private final StringBuilder err = new StringBuilder();
        @Nullable
        private TestOutcome outcome;

        Case(TestId id) {
            this.id = id;
        }

        TestOutcome outcome() {
            TestOutcome current = outcome;
            return current != null ? current : new TestOutcome(TestStatus.ERRORED, Duration.ZERO, null, null);
        }
    }
}
