package io.micronaut.dev;

import org.junit.jupiter.api.Test;

import java.nio.file.FileSystems;
import java.nio.file.WatchService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevWatchServiceTest {

    @Test
    void onMacOsWithoutTheNativeServiceTheLauncherPollsEveryQuarterSecondNotTheJdksTwo() throws Exception {
        assertPolling(DevWatchService.create(true, false, false));
        // a native image cannot load the native service, even when it is on the classpath
        assertPolling(DevWatchService.create(true, true, true));
        assertEquals(250, DevWatchService.POLL_MILLIS);
    }

    @Test
    void elsewhereTheJdksServiceIsUsed() throws Exception {
        DevWatchService watch = DevWatchService.create(false, false, false);
        try (WatchService jdk = FileSystems.getDefault().newWatchService()) {
            assertEquals(jdk.getClass(), watch.service().getClass());
            assertEquals(java.time.Duration.ZERO, watch.detectionDelay());
        } finally {
            watch.service().close();
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    void onMacOsTheNativeServiceReportsTheEventsAfterAFirstOneWithin50Ms() throws Exception {
        DevWatchService watch = DevWatchService.create(true, false, true);
        try {
            assertInstanceOf(FsEventsWatchService.class, watch.service());
            assertEquals(java.time.Duration.ofMillis(50), watch.detectionDelay());
            // closing it is safe: its streams are released on their run loop's thread, outside the run loop
            assertTrue(watch.closeOnClose());
        } finally {
            watch.service().close();
        }
    }

    private static void assertPolling(DevWatchService watch) throws Exception {
        try {
            assertInstanceOf(PollingWatchService.class, watch.service());
            assertEquals(java.time.Duration.ofMillis(DevWatchService.POLL_MILLIS), watch.detectionDelay());
        } finally {
            watch.service().close();
        }
    }
}
