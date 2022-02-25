package io.micronaut.http.server.netty.fuzzing

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
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

    private static volatile String canaryString = null

    private static volatile boolean leakDetected = false
    private static volatile boolean canaryDetected = false

    @SuppressWarnings("unused")
    private static volatile int sink

    static void startTracking() {
        triggerGc()

        leakDetected = false

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
        ByteBuf resource = PooledByteBufAllocator.DEFAULT.directBuffer()
        resource.touch(canaryString)
    }

    // adapted from https://github.com/netty/netty/blob/86603872776e3ff5a60dea3c104358e486eed588/common/src/test/java/io/netty/util/ResourceLeakDetectorTest.java
    private static synchronized void triggerGc() {
        // need to randomize this every time, since ResourceLeakDetector will deduplicate leaks
        canaryString = "canary-" + UUID.randomUUID()
        canaryDetected = false

        leakCanary()

        do {
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

        leakDetected = true
        super.reportTracedLeak(resourceType, records)
    }

    @Override
    protected void reportUntracedLeak(String resourceType) {
        leakDetected = true
        super.reportUntracedLeak(resourceType)
    }
}
