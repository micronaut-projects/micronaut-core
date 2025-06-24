package io.micronaut.http.tck.netty;

import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class LeakDetectionExtension implements BeforeEachCallback, AfterEachCallback {
    private static final boolean NETTY_AVAILABLE;

    static {
        boolean available;
        try {
            //noinspection ResultOfMethodCallIgnored
            ResourceLeakDetector.getLevel();
            available = true;
        } catch (NoClassDefFoundError e) {
            available = false;
        }
        NETTY_AVAILABLE = available;
        if (NETTY_AVAILABLE) {
            TestLeakDetector.init();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (NETTY_AVAILABLE) {
            TestLeakDetector.startTracking(context.getDisplayName());
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (NETTY_AVAILABLE) {
            TestLeakDetector.stopTrackingAndReportLeaks();
        }
    }
}
