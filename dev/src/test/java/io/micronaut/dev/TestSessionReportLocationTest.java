package io.micronaut.dev;

import io.micronaut.dev.test.TestReportListener;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The report the test console shows after every run.
 */
class TestSessionReportLocationTest {

    @Test
    void theFirstReportWithALocationIsShownPastOnesWithoutOrFailing() {
        TestReportListener none = new TestReportListener() {
        };
        TestReportListener failing = new TestReportListener() {
            @Override
            public String location() {
                throw new IllegalStateException("no server");
            }
        };
        TestReportListener page = new TestReportListener() {
            @Override
            public String location() {
                return "http://localhost:35729/tests/";
            }
        };

        assertEquals("http://localhost:35729/tests/", TestSession.reportLocation(List.of(none, failing, page)));
        assertNull(TestSession.reportLocation(List.of(none, failing)));
        assertNull(TestSession.reportLocation(List.of()));
    }
}
