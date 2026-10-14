package io.micronaut.dev.test;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveRegionTest {

    private static final String TICKER = "micronaut-dev-test-progress";

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Object lock = new Object();

    @Test
    void theStandardStreamsPrintAboveTheRowsWhileTheyAreShownAndAreGivenBackAfter() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        LiveRegion region = region(40);
        try {
            region.start((frame, columns) -> List.of("Running tests"), true);
            assertNotSame(out, System.out);
            assertNotSame(err, System.err);
            System.out.println("from the application");
            System.err.print("no line separator");
            System.err.flush();
            System.out.write('x');
            System.out.write('\n');
            System.out.print("a carriage return\r\n");
            assertEquals("""
                from the application
                no line separator
                x
                a carriage return
                Running tests""", screen());
        } finally {
            region.stop();
        }
        assertSame(out, System.out);
        assertSame(err, System.err);
        assertEquals("""
            from the application
            no line separator
            x
            a carriage return""", screen());
    }

    @Test
    void aStreamAnotherToolSetIsLeftToIt() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        PrintStream other = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        LiveRegion region = region(40);
        try {
            region.start((frame, columns) -> List.of("Running tests"), true);
            System.setOut(other);
            region.stop();
            assertSame(other, System.out);
            assertSame(err, System.err);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
    }

    @Test
    void theTickerMovesTheSpinnerAndEndsWhenTheRowsStop() throws InterruptedException {
        PrintStream out = System.out;
        LiveRegion region = region(40);
        Set<Thread> before = tickers();
        try {
            region.start((frame, columns) -> List.of("frame " + frame), true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!raw().contains("frame 2") && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(raw().contains("frame 2"), raw());
        } finally {
            Set<Thread> started = tickers();
            started.removeAll(before);
            assertEquals(1, started.size(), started::toString);
            // interrupted asleep or between frames, the ticker ends
            region.stop();
            Thread ticker = started.iterator().next();
            ticker.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(ticker.isAlive());
        }
        assertSame(out, System.out);
        assertEquals("", screen());
    }

    @Test
    void theRowsAreRepaintedInPlaceAndClampedToTheWidth() {
        AtomicReference<List<String>> rows = new AtomicReference<>(List.of());
        LiveRegion region = region(12);
        // nothing shown yet
        region.refresh();
        region.printAbove("first");
        assertEquals("first", screen());

        region.start((frame, columns) -> rows.get(), false);
        assertEquals("first", screen());
        rows.set(List.of(LiveRegion.paint(LiveRegion.BOLD, "a row wider than the terminal"), "second"));
        region.refresh();
        assertEquals("first\na row wider\nsecond", screen());
        assertTrue(raw().contains("\033[1ma row wider\033[0m"), raw());

        rows.set(List.of("one row"));
        region.printAbove("above");
        assertEquals("first\nabove\none row", screen());

        // rows that empty are cleared
        rows.set(List.of());
        region.refresh();
        assertEquals("first\nabove", screen());
        region.stop();
        assertEquals("first\nabove", screen());
    }

    @Test
    void linesAreFittedAndFormatted() {
        assertEquals("ab", LiveRegion.fit("ab\033[3", 10));
        assertEquals("\033[31mab\033[0m", LiveRegion.fit("\033[31mabcd\033[0m", 2));
        assertEquals("text", LiveRegion.paint(null, "text"));
        assertEquals("", LiveRegion.paint(LiveRegion.RED, ""));
        assertEquals("1m 05s", LiveRegion.duration(TimeUnit.SECONDS.toNanos(65)));
        assertEquals("0.0s", LiveRegion.duration(-1));
        assertEquals("\033[2m[0.0s]\033[0m message", LiveRegion.stamp(0, null, "", "message", null));
        assertEquals("\033[2m[0.0s]\033[0m message", LiveRegion.stamp(0, null, "", "message", ""));
        String header = new LiveRegion(new PrintStream(new ByteArrayOutputStream()), false, 80, () -> 0L, lock).headerRow(0, "Running", null, 0, 4);
        assertEquals("\033[36m-\033[0m \033[1mRunning\033[0m \033[2m0.0s\033[0m", header);
    }

    @Test
    void textIsShortenedInTheMiddle() {
        LiveRegion.Glyphs ascii = LiveRegion.Glyphs.ASCII;
        assertEquals("", ascii.truncate("CalculatorTest", 0));
        assertEquals("CalculatorTest", ascii.truncate("CalculatorTest", 20));
        assertEquals("Ca", ascii.truncate("CalculatorTest", 2));
        assertEquals("Calc...Test", ascii.truncate("CalculatorTest", 11));
        assertEquals("Calc…rTest", LiveRegion.Glyphs.UNICODE.truncate("CalculatorTest", 10));
    }

    @Test
    void rowsAreFittedToTheColumnsTheirCharactersTake() {
        // wide East Asian characters take two columns, combining marks none, a surrogate pair is one character
        assertEquals(2, LiveRegion.columns('加'));
        assertEquals(1, LiveRegion.columns('a'));
        assertEquals(0, LiveRegion.columns('\u0301'));
        assertEquals(2, LiveRegion.columns("😀".codePointAt(0)));
        // emoji outside the pictograph blocks: a newer one and one in the technical symbols
        assertEquals(2, LiveRegion.columns(0x1FA77));
        assertEquals(2, LiveRegion.columns(0x231A));
        // a symbol shown as text by default stays narrow
        assertEquals(1, LiveRegion.columns(0x2764));
        assertEquals(10, LiveRegion.width("加算テスト"));
        assertEquals(4, LiveRegion.width("cafe\u0301"));
        assertEquals(3, LiveRegion.width("a😀"));

        assertEquals("加算テ", LiveRegion.fit("加算テスト", 7));
        // a wide character that does not fit ends the line, and the escapes after it are kept
        assertEquals("\033[1m加算テ\033[0m", LiveRegion.fit("\033[1m加算テスト x\033[0m", 7));
        assertEquals("cafe\u0301", LiveRegion.fit("cafe\u0301s", 4));
        assertEquals("a😀", LiveRegion.fit("a😀b", 3));
        assertEquals("a", LiveRegion.fit("a😀b", 2));

        assertEquals("加算…スト", LiveRegion.Glyphs.UNICODE.truncate("加算のテスト", 9));
        assertEquals("加…ト", LiveRegion.Glyphs.UNICODE.truncate("加算のテスト", 6));
        // three columns each side of the ellipsis: one emoji, never half of one
        assertEquals("😀…😀", LiveRegion.Glyphs.UNICODE.truncate("😀😀😀😀😀", 7));
        assertEquals("e\u0301…e\u0301", LiveRegion.Glyphs.UNICODE.truncate("e\u0301e\u0301e\u0301e\u0301", 3));

        AtomicReference<List<String>> rows = new AtomicReference<>(List.of("加算のテストです", "second"));
        LiveRegion region = region(12);
        region.start((frame, columns) -> rows.get(), false);
        region.refresh();
        // 11 columns drawn: five wide characters, the sixth would overflow
        assertEquals("加算のテス\nsecond", screen());
        // repainted in place, over the two rows drawn
        rows.set(List.of("引き算のテストです"));
        region.refresh();
        assertEquals("引き算のテ", screen());
        region.stop();
    }

    private LiveRegion region(int width) {
        return new LiveRegion(new PrintStream(buffer, true, StandardCharsets.UTF_8), true, width, System::nanoTime, lock);
    }

    private static Set<Thread> tickers() {
        return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().equals(TICKER)).collect(Collectors.toSet());
    }

    private String raw() {
        synchronized (lock) {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private String screen() {
        return TerminalScreen.render(raw());
    }
}
