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
import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.compile.CompileDiagnostic;
import org.jspecify.annotations.Nullable;

import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Shows test runs on the console as Pyronaut's {@code pyronaut test} does, so that test mode looks alike from every
 * build tool and from Pyronaut. On a terminal, a spinner row counts the tests done and failed with the tests running
 * under it; every test that ends becomes a permanent line, {@code ✓} passed, {@code ✗} failed with its message and
 * the first frames of the project beneath, {@code –} skipped; a run ends with a summary line, the report to open and,
 * in continuous testing, the keys. Without a terminal, with {@code TERM=dumb}, or with {@code NO_COLOR} set, it prints
 * the same as plain lines with no escape sequence: {@code PASSED}, {@code FAILED}, {@code SKIPPED} and {@code ERROR}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class TestProgressConsole implements TestReportListener {

    static final int MAX_RUNNING_ROWS = 6;
    static final String SEPARATOR = "--------------------------------------";
    private static final int MAX_FAILURE_LINES = 30;
    private static final int MAX_STACK_FRAMES = 6;
    private static final int DEFAULT_WIDTH = 80;
    private static final int MIN_WIDTH = 40;
    // the indent of the lines under a test or a summary line
    private static final String DETAIL_INDENT = "      ";
    // the frames of the platform, the JDK, the build and test mode itself, which say nothing of the failure
    private static final List<String> HIDDEN_FRAMES = List.of("org.junit.", "java.", "jdk.", "sun.", "com.sun.", "org.opentest4j.",
        "com.gradle.", "org.gradle.", "worker.", "org.apache.maven.", "io.micronaut.dev.", "io.micronaut.test.");

    private final PrintStream output;
    private final boolean interactive;
    private final boolean animate;
    private final LongSupplier clock;
    private final long epoch;
    private final Supplier<@Nullable String> reportLocation;
    private final LiveRegion region;
    // the only lock: the region draws under it too
    private final Object lock = new Object();
    private final Map<String, Running> running = new LinkedHashMap<>();
    private @Nullable String keyHints;
    private boolean live;
    private boolean closed;
    private long planned;
    private long runStarted;
    private int passed;
    private int failed;
    private int errors;
    private int skipped;

    /**
     * @param output Where to print
     * @param terminal What the console can show
     * @param clock The time, in nanoseconds
     * @param animate Whether a ticker repaints the rows on a terminal, and the standard streams print above them
     * @param reportLocation Where the report a person opens is, or null
     */
    TestProgressConsole(PrintStream output, Terminal terminal, LongSupplier clock, boolean animate, Supplier<@Nullable String> reportLocation) {
        this.output = output;
        this.interactive = terminal.interactive();
        this.animate = animate;
        this.clock = clock;
        this.epoch = clock.getAsLong();
        this.reportLocation = reportLocation;
        this.region = new LiveRegion(output, terminal.unicode(), terminal.width(), clock, lock);
    }

    /**
     * A console on the standard output, as the process's terminal allows.
     *
     * @param reportLocation Where the report a person opens is, or null
     * @return The console
     */
    public static TestProgressConsole create(Supplier<@Nullable String> reportLocation) {
        return new TestProgressConsole(System.out, Terminal.detect(), System::nanoTime, true, reportLocation);
    }

    /**
     * @return Whether the console draws on a terminal, rather than printing plain lines
     */
    public boolean isInteractive() {
        return interactive;
    }

    /**
     * Sets the keys of continuous testing, shown under a separator after every run and change that did not compile,
     * and shows them at once.
     *
     * @param hints The keys, or null for none
     */
    public void keyHints(@Nullable String hints) {
        synchronized (lock) {
            keyHints = hints;
            printKeyHints();
        }
    }

    /**
     * Prints a neutral line, such as why no test ran.
     *
     * @param message The line
     */
    public void note(String message) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            line(LiveRegion.stamp(clock.getAsLong() - epoch, null, glyphs().bullet(), message, null), message);
        }
    }

    /**
     * Clears the rows of a run under way, and prints no more.
     */
    public void close() {
        synchronized (lock) {
            if (live) {
                region.stop();
                live = false;
            }
            closed = true;
        }
    }

    /**
     * Repaints the rows of a run under way, as the ticker does.
     */
    void repaint() {
        synchronized (lock) {
            if (live) {
                region.refresh();
            }
        }
    }

    @Override
    public void runStarted(TestRunStarted event) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            running.clear();
            planned = 0;
            passed = 0;
            failed = 0;
            errors = 0;
            skipped = 0;
            runStarted = clock.getAsLong();
            if (interactive) {
                live = true;
                region.start(this::rows, animate);
            }
        }
    }

    @Override
    public void testsPlanned(long count) {
        synchronized (lock) {
            planned = count;
            if (live) {
                region.refresh();
            }
        }
    }

    @Override
    public void testStarted(TestId test) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            running.put(test.uniqueId(), new Running(name(test), clock.getAsLong()));
            if (live) {
                region.refresh();
            } else if (!interactive) {
                output.println("> " + name(test));
            }
        }
    }

    @Override
    public void testFinished(TestId test, TestOutcome outcome) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            running.remove(test.uniqueId());
            String name = name(test);
            if (isClassError(test)) {
                // a class that failed to set up or tear down, or the platform: not a test of its own
                errors++;
                line("  " + paint(LiveRegion.RED, glyphs().cross()) + " " + paint(LiveRegion.BOLD, test.displayName()) + " " + paint(LiveRegion.DIM, "(error)"),
                    "ERROR " + test.displayName());
                failure(outcome.failure());
                return;
            }
            String took = LiveRegion.duration(outcome.duration().toNanos());
            switch (outcome.status()) {
                case PASSED -> {
                    passed++;
                    line("  " + paint(LiveRegion.GREEN, glyphs().check()) + " " + name + " " + paint(LiveRegion.DIM, "(" + took + ")"),
                        "PASSED " + name + " (" + took + ")");
                }
                case SKIPPED -> {
                    skipped++;
                    String reason = outcome.skipReason();
                    String detail = reason == null || reason.isBlank() ? "skipped" : "skipped: " + reason.lines().findFirst().orElse("").strip();
                    line("  " + paint(LiveRegion.DIM, glyphs().skipped() + " " + name + " (" + detail + ")"), "SKIPPED " + name + " (" + detail + ")");
                }
                default -> {
                    failed++;
                    line("  " + paint(LiveRegion.RED, glyphs().cross()) + " " + paint(LiveRegion.BOLD, name) + " " + paint(LiveRegion.DIM, "(" + took + ")"),
                        "FAILED " + name + " (" + took + ")");
                    failure(outcome.failure());
                }
            }
        }
    }

    @Override
    public void runFinished(TestRunSummary summary) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            running.clear();
            String message;
            String tone;
            String symbol;
            int ran = passed + failed;
            String skippedText = skipped == 0 ? "" : ", " + skipped + " skipped";
            String errorText = errors == 0 ? "" : ", " + errors + (errors == 1 ? " error" : " errors");
            if (summary.cancelled()) {
                int done = ran + skipped;
                message = "Cancelled after " + done + (done == 1 ? " test" : " tests") + ": " + failed + " failed" + errorText + skippedText;
                tone = LiveRegion.YELLOW;
                symbol = glyphs().warning();
            } else if (failed > 0) {
                message = failed + " of " + ran + " tests failed" + errorText + skippedText;
                tone = LiveRegion.RED;
                symbol = glyphs().cross();
            } else if (ran == 0) {
                message = "No tests ran" + errorText + skippedText;
                tone = errors > 0 ? LiveRegion.RED : LiveRegion.YELLOW;
                symbol = errors > 0 ? glyphs().cross() : glyphs().warning();
            } else {
                message = ran + (ran == 1 ? " test passed" : " tests passed") + errorText + skippedText;
                tone = errors > 0 ? LiveRegion.RED : LiveRegion.GREEN;
                symbol = errors > 0 ? glyphs().cross() : glyphs().check();
            }
            String took = LiveRegion.duration(summary.duration().toNanos());
            line(LiveRegion.stamp(clock.getAsLong() - epoch, tone, symbol, message, "(" + took + ")"), message + " in " + took);
            String location = reportLocation.get();
            if (location != null) {
                line(paint(LiveRegion.DIM, "  Report: " + LiveRegion.link(location, location)), "Test report: " + location);
            }
            if (live) {
                region.stop();
                live = false;
            }
            printKeyHints();
        }
    }

    @Override
    public void compilationFailed(CompileFailure failure) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            List<CompileDiagnostic> problems = failure.errors().isEmpty() ? failure.diagnostics() : failure.errors();
            int count = failure.errors().size();
            String kind = failure.kind().name().toLowerCase(Locale.ROOT);
            String message = Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " compilation failed"
                + (count == 0 ? "" : ": " + count + (count == 1 ? " error" : " errors"));
            line(LiveRegion.stamp(clock.getAsLong() - epoch, LiveRegion.RED, glyphs().cross(), message, null), "COMPILATION FAILED " + message);
            List<String> lines = new ArrayList<>();
            for (CompileDiagnostic diagnostic : problems) {
                StringBuilder text = new StringBuilder();
                if (diagnostic.file() != null) {
                    text.append(diagnostic.file());
                    if (diagnostic.line() > 0) {
                        text.append(':').append(diagnostic.line());
                    }
                    text.append(": ");
                }
                List<String> messageLines = diagnostic.message().strip().lines().toList();
                text.append(messageLines.isEmpty() ? "" : messageLines.get(0));
                lines.add(text.toString());
                for (int i = 1; i < messageLines.size(); i++) {
                    lines.add("  " + messageLines.get(i));
                }
            }
            for (String text : trim(lines)) {
                line(DETAIL_INDENT + paint(LiveRegion.RED, text), DETAIL_INDENT + text);
            }
            printKeyHints();
        }
    }

    /**
     * @param test A test
     * @return The name the console shows: {@code SimpleClass.method()} for a test of a class, else its display name,
     * such as a pytest node id
     */
    static String name(TestId test) {
        String className = test.className();
        if (className.isEmpty() || className.contains("/") || className.contains("\\") || className.endsWith(".py")
            || className.equals(test.displayName())) {
            return test.displayName();
        }
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        return simple + "." + test.displayName();
    }

    private static boolean isClassError(TestId test) {
        return JUnitPlatformExecution.CLASS_ERROR.equals(test.name());
    }

    /**
     * The lines a failure shows: its message, then the first frames that are not of the platform, the JDK or the
     * build, as Pyronaut shows them.
     *
     * @param failure The failure
     * @return The lines
     */
    static List<String> failureLines(TestFailure failure) {
        List<String> lines = new ArrayList<>();
        String type = failure.type();
        String simpleType = type.substring(type.lastIndexOf('.') + 1);
        String message = failure.message();
        if (message == null || message.isBlank()) {
            lines.add(simpleType);
        } else {
            List<String> messageLines = message.lines().toList();
            boolean python = type.contains("Python") || type.contains("pytest");
            if (python || messageLines.get(0).startsWith(simpleType)) {
                lines.addAll(messageLines);
            } else {
                lines.add(simpleType + ": " + messageLines.get(0));
                lines.addAll(messageLines.subList(1, messageLines.size()));
            }
        }
        int frames = 0;
        for (String traceLine : failure.stackTrace().lines().toList()) {
            String frame = traceLine.strip();
            if (frame.startsWith("Caused by:")) {
                // the frames of the cause follow: those of the failure are all shown
                break;
            }
            if (!frame.startsWith("at ")) {
                continue;
            }
            String location = frame.substring(3);
            // a frame of a named module: at java.base/java.lang.Thread.run(Thread.java:1583)
            int slash = location.indexOf('/');
            int paren = location.indexOf('(');
            String className = slash >= 0 && (paren < 0 || slash < paren) ? location.substring(slash + 1) : location;
            if (HIDDEN_FRAMES.stream().anyMatch(className::startsWith)) {
                continue;
            }
            if (frames == MAX_STACK_FRAMES) {
                lines.add("  ...");
                break;
            }
            lines.add("  at " + location);
            frames++;
        }
        return trim(lines);
    }

    private static List<String> trim(List<String> lines) {
        if (lines.size() <= MAX_FAILURE_LINES) {
            return lines;
        }
        List<String> trimmed = new ArrayList<>(lines.subList(0, MAX_FAILURE_LINES));
        trimmed.add("  ... (" + (lines.size() - MAX_FAILURE_LINES) + " more lines in the report)");
        return trimmed;
    }

    private void failure(@Nullable TestFailure failure) {
        if (failure == null) {
            return;
        }
        for (String text : failureLines(failure)) {
            line(DETAIL_INDENT + paint(LiveRegion.RED, text), DETAIL_INDENT + text);
        }
    }

    private void printKeyHints() {
        String hints = keyHints;
        if (hints == null || closed) {
            return;
        }
        line(paint(LiveRegion.DIM, SEPARATOR), SEPARATOR);
        line(hints, hints);
    }

    /**
     * Prints a permanent line: the terminal's, above the rows when a run is under way, or the plain one.
     */
    private void line(String terminalLine, String plainLine) {
        if (!interactive) {
            output.println(plainLine);
        } else if (live) {
            region.printAbove(terminalLine);
        } else {
            output.println(terminalLine);
            output.flush();
        }
    }

    private String paint(String tone, String text) {
        return interactive ? LiveRegion.paint(tone, text) : text;
    }

    private LiveRegion.Glyphs glyphs() {
        return region.glyphs();
    }

    /**
     * The rows of a run under way: a spinner header with the tests done and failed, and the tests running under it.
     */
    private List<String> rows(int spinnerFrame, int columns) {
        // the region calls it under the lock
        List<String> lines = new ArrayList<>();
        long now = clock.getAsLong();
        long done = (long) passed + failed + skipped;
        StringBuilder detail = new StringBuilder();
        detail.append(done);
        // the platform counts the tests it discovered: dynamic tests come on top
        if (planned > 0 && done <= planned) {
            detail.append('/').append(planned);
        }
        detail.append(" done");
        if (failed + errors > 0) {
            detail.append(", ").append(failed + errors).append(" failed");
        }
        lines.add(region.headerRow(spinnerFrame, "Running tests", detail.toString(), now - runStarted, columns));
        int shown = 0;
        for (Running test : running.values()) {
            if (shown == MAX_RUNNING_ROWS) {
                lines.add(LiveRegion.paint(LiveRegion.DIM, "  " + glyphs().branch() + " " + glyphs().ellipsis() + " " + (running.size() - shown) + " more"));
                break;
            }
            String elapsed = LiveRegion.duration(now - test.since());
            String name = glyphs().truncate(test.name(), Math.max(12, columns - 12));
            int padding = Math.max(1, columns - (4 + name.length()) - elapsed.length());
            lines.add(LiveRegion.paint(LiveRegion.DIM, "  " + glyphs().branch() + " ") + name + " ".repeat(padding) + LiveRegion.paint(LiveRegion.DIM, elapsed));
            shown++;
        }
        return lines;
    }

    /**
     * A test running.
     *
     * @param name Its name
     * @param since When it started
     */
    private record Running(String name, long since) {
    }

    /**
     * What the console can show.
     *
     * @param interactive Whether it draws with ANSI escapes on a terminal, rather than printing plain lines
     * @param unicode Whether the terminal renders Unicode symbols
     * @param width The terminal's width in columns
     */
    record Terminal(boolean interactive, boolean unicode, int width) {

        /**
         * @return What the standard output can show: a terminal, unless {@code TERM} is {@code dumb} or
         * {@code NO_COLOR} is set
         */
        static Terminal detect() {
            // a console exists when the streams are redirected too: it tells whether they are a terminal
            Console console = System.console();
            return detect(System::getenv, console != null && console.isTerminal(),
                System.getProperty("stdout.encoding", System.getProperty("native.encoding", Charset.defaultCharset().name())),
                System.getProperty("os.name", ""), () -> sttyWidth("/bin/sh", "-c", "stty size < /dev/tty"));
        }

        /**
         * @param environment The environment variables
         * @param terminal Whether the standard output is a terminal
         * @param encoding The encoding of the standard output
         * @param osName The name of the operating system
         * @param stty The width {@code stty} tells
         * @return What the standard output can show
         */
        static Terminal detect(Function<String, @Nullable String> environment, boolean terminal, String encoding, String osName, IntSupplier stty) {
            String term = environment.apply("TERM");
            boolean interactive = terminal && (term == null || !term.equalsIgnoreCase("dumb")) && environment.apply("NO_COLOR") == null;
            if (!interactive) {
                return new Terminal(false, false, DEFAULT_WIDTH);
            }
            return new Terminal(true, isUtf8(encoding), width(environment.apply("COLUMNS"), osName, stty));
        }

        private static boolean isUtf8(String encoding) {
            try {
                return Charset.forName(encoding).equals(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        /**
         * @return The width of the terminal: {@code COLUMNS}, else what {@code stty} tells, else 80 columns
         */
        private static int width(@Nullable String columns, String osName, IntSupplier stty) {
            if (columns != null) {
                try {
                    int value = Integer.parseInt(columns.strip());
                    if (value >= MIN_WIDTH) {
                        return value;
                    }
                } catch (NumberFormatException e) {
                    // asked of the terminal instead
                }
            }
            if (osName.toLowerCase(Locale.ROOT).startsWith("windows")) {
                return DEFAULT_WIDTH;
            }
            return stty.getAsInt();
        }

        /**
         * @param command The command that prints the terminal's size, as {@code stty size} does: rows and columns
         * @return The columns it prints, else 80 columns
         */
        static int sttyWidth(String... command) {
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String size = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    String[] parts = size.split("\\s+");
                    int value = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
                    return value >= MIN_WIDTH ? value : DEFAULT_WIDTH;
                }
            } catch (IOException | NumberFormatException e) {
                return DEFAULT_WIDTH;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return DEFAULT_WIDTH;
        }
    }
}
