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

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Rows repainted in place at the bottom of a terminal, with permanent lines written above them: the live part of the
 * test console, drawn with plain ANSI escapes. A ticker repaints the rows while they are shown, so that the spinner
 * and the elapsed times move between events; the rows are cleared when the region stops. The layout is Pyronaut's,
 * so that both show test runs alike.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class LiveRegion {

    static final String RESET = "\033[0m";
    static final String BOLD = "\033[1m";
    static final String DIM = "\033[2m";
    static final String RED = "\033[31m";
    static final String GREEN = "\033[32m";
    static final String YELLOW = "\033[33m";
    static final String CYAN = "\033[36m";
    private static final long FRAME_INTERVAL_MILLIS = 80L;

    private final PrintStream output;
    private final Glyphs glyphs;
    private final int width;
    private final LongSupplier clock;
    private final Object lock;
    private @Nullable Frame frame;
    private @Nullable Thread ticker;
    private volatile boolean running;
    private int drawnLines;
    private int spinnerFrame;
    private @Nullable PrintStream previousOut;
    private @Nullable PrintStream previousErr;
    private @Nullable PrintStream capturedOut;
    private @Nullable PrintStream capturedErr;

    /**
     * @param output The terminal
     * @param unicode Whether the terminal renders Unicode symbols
     * @param width The terminal's width in columns
     * @param clock The time, in nanoseconds
     * @param lock The lock of the rows' state, which the region draws under: one lock, so that no order of two can deadlock
     */
    LiveRegion(PrintStream output, boolean unicode, int width, LongSupplier clock, Object lock) {
        this.output = output;
        this.lock = lock;
        this.glyphs = unicode ? Glyphs.UNICODE : Glyphs.ASCII;
        this.width = width;
        this.clock = clock;
    }

    Glyphs glyphs() {
        return glyphs;
    }

    /**
     * Shows rows until {@link #stop()}.
     *
     * @param rows The rows
     * @param animate Whether a ticker repaints them, and the standard streams print above them
     */
    void start(Frame rows, boolean animate) {
        synchronized (lock) {
            frame = rows;
            if (animate) {
                running = true;
                Thread thread = new Thread(this::tick, "micronaut-dev-test-progress");
                thread.setDaemon(true);
                ticker = thread;
                thread.start();
                captureStandardStreams();
            }
            refreshLocked();
        }
    }

    /**
     * Clears the rows, and gives the standard streams back.
     */
    void stop() {
        synchronized (lock) {
            // the ticker sees it under the lock, and draws no more
            running = false;
            Thread thread = ticker;
            if (thread != null) {
                thread.interrupt();
            }
            ticker = null;
            restoreStandardStreams();
            output.print(clearSequence());
            output.flush();
            frame = null;
        }
    }

    /**
     * Writes a permanent line above the rows, and repaints them below it.
     *
     * @param line The line, without its line separator
     */
    void printAbove(String line) {
        synchronized (lock) {
            output.print(clearSequence() + line + '\n');
            refreshLocked();
        }
    }

    /**
     * Repaints the rows now.
     */
    void refresh() {
        synchronized (lock) {
            refreshLocked();
        }
    }

    private void tick() {
        while (running) {
            try {
                Thread.sleep(FRAME_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (lock) {
                if (!running) {
                    return;
                }
                spinnerFrame++;
                refreshLocked();
            }
        }
    }

    private String clearSequence() {
        if (drawnLines == 0) {
            return "";
        }
        String sequence = "\033[" + drawnLines + "A\r\033[J";
        drawnLines = 0;
        return sequence;
    }

    private void refreshLocked() {
        Frame rows = frame;
        if (rows == null) {
            return;
        }
        int usable = width - 1;
        List<String> lines = rows.lines(spinnerFrame, usable);
        if (lines.isEmpty() && drawnLines == 0) {
            return;
        }
        StringBuilder out = new StringBuilder();
        if (drawnLines > 0) {
            out.append("\033[").append(drawnLines).append('A');
        }
        out.append('\r');
        for (String line : lines) {
            out.append("\033[2K").append(fit(line, usable)).append('\n');
        }
        out.append("\033[J");
        drawnLines = lines.size();
        output.print(out);
        output.flush();
    }

    /**
     * Routes what the application and the tests print outside JUnit's capture, its logging included, above the rows,
     * instead of through them. Logback's console appender writes to the stream {@link System#out} holds at each write,
     * so it is routed too; a backend that keeps the stream it started with, as Log4j 2's console appender does unless
     * it follows, or the console handler of {@code java.util.logging}, prints under the rows until the next repaint.
     */
    private void captureStandardStreams() {
        previousOut = System.out;
        previousErr = System.err;
        PrintStream out = new PrintStream(new LineForwarder(), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new LineForwarder(), true, StandardCharsets.UTF_8);
        capturedOut = out;
        capturedErr = err;
        System.setOut(out);
        System.setErr(err);
    }

    private void restoreStandardStreams() {
        PrintStream out = previousOut;
        PrintStream err = previousErr;
        if (out == null || err == null) {
            return;
        }
        System.out.flush();
        System.err.flush();
        // a stream another tool set since is left to it
        if (System.out == capturedOut) {
            System.setOut(out);
        }
        if (System.err == capturedErr) {
            System.setErr(err);
        }
        previousOut = null;
        previousErr = null;
        capturedOut = null;
        capturedErr = null;
    }

    /**
     * @param tone An ANSI colour, or null
     * @param text The text
     * @return The text in the colour
     */
    static String paint(@Nullable String tone, String text) {
        if (tone == null || text.isEmpty()) {
            return text;
        }
        return tone + text + RESET;
    }

    /**
     * A permanent status line: {@code [elapsed] ✓ message (suffix)}.
     *
     * @param elapsedNanos The time since the console started
     * @param tone The colour of the symbol
     * @param symbol The symbol
     * @param message The message
     * @param suffix Dim text after the message, or null
     * @return The line
     */
    static String stamp(long elapsedNanos, @Nullable String tone, String symbol, String message, @Nullable String suffix) {
        StringBuilder line = new StringBuilder();
        line.append(paint(DIM, "[" + duration(elapsedNanos) + "]")).append(' ');
        if (!symbol.isEmpty()) {
            line.append(paint(tone, symbol)).append(' ');
        }
        line.append(message);
        if (suffix != null && !suffix.isEmpty()) {
            line.append(' ').append(paint(DIM, suffix));
        }
        return line.toString();
    }

    /**
     * A header row: {@code spinner label · detail}, with the elapsed time right-aligned.
     *
     * @param spinnerFrame The spinner's frame
     * @param label The bold label
     * @param detail Dim text after the label, or null
     * @param elapsedNanos The elapsed time
     * @param columns The usable width
     * @return The row
     */
    String headerRow(int spinnerFrame, String label, @Nullable String detail, long elapsedNanos, int columns) {
        String spinner = glyphs.spinner().get(Math.floorMod(spinnerFrame, glyphs.spinner().size()));
        String elapsed = duration(elapsedNanos);
        String detailText = detail == null || detail.isEmpty() ? "" : " " + glyphs.separator() + " " + detail;
        int padding = Math.max(1, columns - (2 + width(label) + width(detailText)) - width(elapsed));
        return paint(CYAN, spinner) + " " + paint(BOLD, label) + paint(DIM, detailText) + " ".repeat(padding) + paint(DIM, elapsed);
    }

    /**
     * Clamps a line, which may hold colours, to a width: a row that wraps breaks the cursor arithmetic of repainting.
     *
     * @param line The line
     * @param columns The width
     * @return The clamped line
     */
    static String fit(String line, int columns) {
        StringBuilder result = new StringBuilder();
        int visible = 0;
        boolean full = false;
        int index = 0;
        while (index < line.length()) {
            char ch = line.charAt(index);
            if (ch == '\033') {
                int end = line.indexOf('m', index);
                if (end < 0) {
                    break;
                }
                result.append(line, index, end + 1);
                index = end + 1;
                continue;
            }
            int codePoint = line.codePointAt(index);
            int columnsTaken = columns(codePoint);
            // a wide character that does not fit ends the line, rather than a narrower one after it filling the gap
            if (!full && visible + columnsTaken <= columns) {
                result.appendCodePoint(codePoint);
                visible += columnsTaken;
            } else {
                full = true;
            }
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    /**
     * The columns text takes on a terminal, without escape sequences.
     *
     * @param text The text
     * @return The sum of the columns of its code points
     */
    static int width(String text) {
        int total = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            total += columns(codePoint);
            index += Character.charCount(codePoint);
        }
        return total;
    }

    /**
     * The columns a code point takes on a terminal, as {@code wcwidth} tells: none for a combining mark, a format or
     * control character, two for a wide or fullwidth East Asian character or an emoji presented as one by default,
     * one otherwise.
     *
     * @param codePoint The code point
     * @return 0, 1 or 2
     */
    static int columns(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT
            || type == Character.CONTROL) {
            return 0;
        }
        if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) {
            // a regional indicator: a pair of them is a flag, shown in two columns
            return 1;
        }
        // an emoji shown as one by default is wide wherever it is, as the East Asian width of Unicode 9 on tells
        return isWide(codePoint) || Character.isEmojiPresentation(codePoint) ? 2 : 1;
    }

    private static boolean isWide(int codePoint) {
        return codePoint >= 0x1100 && (codePoint <= 0x115F // Hangul Jamo initial consonants
            || codePoint >= 0x2E80 && codePoint <= 0x303E // CJK radicals, Kangxi, ideographic punctuation
            || codePoint >= 0x3041 && codePoint <= 0x33FF // Hiragana, Katakana, Bopomofo, CJK compatibility
            || codePoint >= 0x3400 && codePoint <= 0x4DBF // CJK extension A
            || codePoint >= 0x4E00 && codePoint <= 0x9FFF // CJK unified ideographs
            || codePoint >= 0xA000 && codePoint <= 0xA4CF // Yi
            || codePoint >= 0xAC00 && codePoint <= 0xD7A3 // Hangul syllables
            || codePoint >= 0xF900 && codePoint <= 0xFAFF // CJK compatibility ideographs
            || codePoint >= 0xFE30 && codePoint <= 0xFE4F // CJK compatibility forms
            || codePoint >= 0xFF00 && codePoint <= 0xFF60 // fullwidth forms
            || codePoint >= 0xFFE0 && codePoint <= 0xFFE6 // fullwidth signs
            || codePoint >= 0x1F300 && codePoint <= 0x1F64F // pictographs and emoticons
            || codePoint >= 0x1F680 && codePoint <= 0x1F6FF // transport and map symbols
            || codePoint >= 0x1F900 && codePoint <= 0x1F9FF // supplemental symbols and pictographs
            || codePoint >= 0x20000 && codePoint <= 0x2FFFD // CJK extensions B to F
            || codePoint >= 0x30000 && codePoint <= 0x3FFFD); // CJK extension G and beyond
    }

    /**
     * @param nanos A duration
     * @return {@code 3.2s} below a minute, {@code 1m 05s} above
     */
    static String duration(long nanos) {
        double seconds = Math.max(0, nanos) / 1_000_000_000.0;
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.1fs", seconds);
        }
        long whole = (long) seconds;
        return String.format(Locale.ROOT, "%dm %02ds", whole / 60, whole % 60);
    }

    /**
     * @param label The visible text
     * @param uri The target
     * @return The text as an OSC 8 hyperlink, which capable terminals make clickable
     */
    static String link(String label, String uri) {
        return "\033]8;;" + uri + "\033\\" + label + "\033]8;;\033\\";
    }

    long now() {
        return clock.getAsLong();
    }

    /**
     * Prints each complete line written to it above the rows, under the region's lock: the only lock, so that a
     * thread printing cannot deadlock with one drawing.
     */
    private final class LineForwarder extends OutputStream {
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            synchronized (lock) {
                if (b == '\n') {
                    flushLine();
                } else {
                    pending.write(b);
                }
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            synchronized (lock) {
                for (int index = offset; index < offset + length; index++) {
                    write(bytes[index]);
                }
            }
        }

        @Override
        public void flush() {
            synchronized (lock) {
                if (pending.size() > 0) {
                    flushLine();
                }
            }
        }

        private void flushLine() {
            printAbove(pending.toString(StandardCharsets.UTF_8).replace("\r", ""));
            pending.reset();
        }
    }

    /**
     * The rows shown.
     */
    @FunctionalInterface
    interface Frame {
        /**
         * @param spinnerFrame The spinner's frame
         * @param columns The usable width
         * @return The rows, top to bottom
         */
        List<String> lines(int spinnerFrame, int columns);
    }

    /**
     * The symbols, with an ASCII set for terminals that do not render Unicode.
     *
     * @param spinner The spinner's frames
     * @param check A test that passed
     * @param cross A test that failed
     * @param warning A run cut short
     * @param bullet A note
     * @param skipped A skipped test
     * @param branch A running test
     * @param separator Between a label and its detail
     * @param ellipsis Elided text
     */
    record Glyphs(List<String> spinner, String check, String cross, String warning, String bullet, String skipped,
                  String branch, String separator, String ellipsis) {

        static final Glyphs UNICODE = new Glyphs(List.of("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"),
            "✓", "✗", "!", "•", "–", "└", "·", "…");
        static final Glyphs ASCII = new Glyphs(List.of("-", "\\", "|", "/"), "+", "x", "!", "*", "-", "L", "-", "...");

        /**
         * Shortens text to a width, eliding its middle.
         *
         * @param value The text
         * @param maxWidth The width
         * @return The text, shortened
         */
        String truncate(String value, int maxWidth) {
            if (maxWidth <= 0) {
                return "";
            }
            if (width(value) <= maxWidth) {
                return value;
            }
            int ellipsisWidth = width(ellipsis);
            if (maxWidth <= ellipsisWidth) {
                return head(value, maxWidth);
            }
            int headWidth = (maxWidth - ellipsisWidth) / 2;
            int tailWidth = maxWidth - ellipsisWidth - headWidth;
            return head(value, headWidth) + ellipsis + tail(value, tailWidth);
        }

        /**
         * The longest start of a text that fits a width, by code point.
         */
        private static String head(String value, int maxWidth) {
            int used = 0;
            int index = 0;
            while (index < value.length()) {
                int codePoint = value.codePointAt(index);
                int taken = columns(codePoint);
                if (used + taken > maxWidth) {
                    break;
                }
                used += taken;
                index += Character.charCount(codePoint);
            }
            return value.substring(0, index);
        }

        /**
         * The longest end of a text that fits a width, by code point, its combining marks with the character they mark.
         */
        private static String tail(String value, int maxWidth) {
            int used = 0;
            int index = value.length();
            while (index > 0) {
                int codePoint = value.codePointBefore(index);
                int taken = columns(codePoint);
                if (used + taken > maxWidth) {
                    break;
                }
                used += taken;
                index -= Character.charCount(codePoint);
            }
            // a mark left at the start of the tail would combine with the ellipsis
            while (index < value.length() && columns(value.codePointAt(index)) == 0) {
                index += Character.charCount(value.codePointAt(index));
            }
            return value.substring(index);
        }
    }
}
