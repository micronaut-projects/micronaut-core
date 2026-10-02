package io.micronaut.dev;

import org.junit.jupiter.api.Test;

import java.nio.file.FileSystems;
import java.nio.file.WatchService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        } finally {
            watch.service().close();
        }
    }

    private static void assertPolling(DevWatchService watch) throws Exception {
        try {
            assertInstanceOf(PollingWatchService.class, watch.service());
        } finally {
            watch.service().close();
        }
    }
}
