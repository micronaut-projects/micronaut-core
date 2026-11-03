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
    void theNativeServiceIsLookedForWithoutInitializingItsClass() {
        ClassLoader loader = DevWatchServiceTest.class.getClassLoader();
        // as JNA's Native, whose initializer loads its native library
        assertTrue(DevWatchService.isPresent(Initialized.class.getName(), loader));
        // read from another class: reading one of its own would initialize it
        assertEquals(false, Marker.initialized);
        assertEquals(false, DevWatchService.isPresent("io.micronaut.dev.Missing", loader));
    }

    @Test
    void theNativeServiceIsLookedForOnlyOnMacOsOutsideANativeImage() {
        ClassLoader loader = DevWatchServiceTest.class.getClassLoader();
        // the test classpath has JNA, through micronaut-runtime-osx
        assertTrue(DevWatchService.macServicePresent(true, false, loader));
        assertEquals(false, DevWatchService.macServicePresent(false, false, loader));
        assertEquals(false, DevWatchService.macServicePresent(true, true, loader));
        assertEquals(false, DevWatchService.macServicePresent(true, false, new java.net.URLClassLoader(new java.net.URL[0], null)));
    }

    static final class Initialized {
        static {
            Marker.initialized = true;
        }
    }

    static final class Marker {
        static boolean initialized;
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
