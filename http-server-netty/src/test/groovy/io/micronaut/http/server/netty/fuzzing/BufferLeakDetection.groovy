package io.micronaut.http.server.netty.fuzzing

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.util.ResourceLeakDetector
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hook into netty resource leak detection that allows test cases to check for resource leaks.
 */
class BufferLeakDetection<T> extends ResourceLeakDetector<T> {
    private static final Logger LOG = LoggerFactory.getLogger(BufferLeakDetection)

    private static final List<ResourceLeakDetector<?>> allDetectors = new CopyOnWriteArrayList<>()

    private static final String BASE_CANARY_STRING = "canary-" + UUID.randomUUID() + "-"
    private static volatile String canaryString = null

    private static volatile String currentTest = null
    private static volatile boolean leakDetected = false
    private static volatile boolean canaryDetected = false

    @SuppressWarnings("unused")
    private static volatile int sink

    static void startTracking(String testName) {
        triggerGc()

        leakDetected = false
        currentTest = testName

        LOG.debug("Starting resource leak tracking")
        if (level != Level.PARANOID) {
            LOG.warn("Resource leaks can't be detected properly below leak detection level 'paranoid'")
        }
    }

    static void stopTrackingAndReportLeaks() {
        triggerGc()

        if (leakDetected) {
            throw new RuntimeException("Detected a resource leak. Please check logs")
        } else {
            LOG.debug("No resource leak detected")
        }
    }

    private static void leakCanary() {
        ByteBuf resource = ByteBufAllocator.DEFAULT.directBuffer()
        resource.touch(canaryString)
    }

    // adapted from https://github.com/netty/netty/blob/86603872776e3ff5a60dea3c104358e486eed588/common/src/test/java/io/netty/util/ResourceLeakDetectorTest.java
    private static synchronized void triggerGc() {
        // timeout of last resort for the loop below. use nanoTime because it's monotonic
        long startTime = System.nanoTime()

        // need to randomize this every time, since ResourceLeakDetector will deduplicate leaks
        canaryString = BASE_CANARY_STRING + UUID.randomUUID()
        canaryDetected = false

        leakCanary()

        do {
            if (System.nanoTime() - startTime > 30_000_000_000L) {
                LOG.warn("Canary leak detection failed.")
                break
            }

            // Trigger GC.
            System.gc();

            // trigger detectors – ref queue collection is only done on track()
            for (ResourceLeakDetector<?> detector : allDetectors) {
                def obj = new Object()
                detector.track(obj).close(obj)
            }

            // Give the GC something to work on.
            for (int i = 0; i < 1000; i++) {
                sink = System.identityHashCode(new byte[10000]);
            }
        } while (!canaryDetected && !Thread.interrupted());
    }

    @SuppressWarnings('unused')
    BufferLeakDetection(Class<?> resourceType, int samplingInterval) {
        super(resourceType, samplingInterval)
        allDetectors.add(this)
    }

    @SuppressWarnings('unused')
    BufferLeakDetection(Class<?> resourceType, int samplingInterval, long maxActive) {
        // maxActive ignored by superclass
        this(resourceType, samplingInterval)
    }

    @Override
    protected boolean needReport() {
        return true
    }

    @Override
    protected void reportTracedLeak(String resourceType, String records) {
        def canary = canaryString
        if (canary != null && records.contains(canary)) {
            canaryDetected = true
            return
        }
        if (records.contains(BASE_CANARY_STRING)) {
            // probably a canary from another run that ran into a timeout, drop
            return
        }

        leakDetected = true
        super.reportTracedLeak(resourceType, records)
    }

    @Override
    protected void reportUntracedLeak(String resourceType) {
        leakDetected = true
        super.reportUntracedLeak(resourceType)
    }

    //@Override only available for override in netty 4.1.75+
    protected Object getInitialHint(String resourceType) {
        return currentTest
    }
}
