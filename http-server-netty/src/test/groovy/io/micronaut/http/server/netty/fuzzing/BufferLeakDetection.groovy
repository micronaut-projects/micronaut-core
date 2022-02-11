package io.micronaut.http.server.netty.fuzzing

import io.netty.util.ResourceLeakDetector
import io.netty.util.ResourceLeakDetectorFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hook into netty resource leak detection that allows test cases to check for resource leaks.
 */
class BufferLeakDetection<T> extends ResourceLeakDetector<T> {
    private static final Logger LOG = LoggerFactory.getLogger(BufferLeakDetection)

    private static final List<ResourceLeakDetector<?>> allDetectors = new CopyOnWriteArrayList<>()

    private static volatile boolean leakDetected = false
    private static volatile boolean canaryDetected = false

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

    private static void triggerGc() {
        canaryDetected = false

        Canary.CANARY_LEAK_DETECTOR.track(new Canary())

        // this seems to be reasonably reliable to trigger collection of remaining buffers
        System.gc()
        try {
            //noinspection GroovyUnusedAssignment
            byte[] unused = new byte[(int) Runtime.getRuntime().freeMemory()]
        } catch (OutOfMemoryError ignored) {}

        // trigger detectors – ref queue collection is only done on track()
        for (ResourceLeakDetector<?> detector : allDetectors) {
            def obj = new Object()
            detector.track(obj).close(obj)
        }

        if (!canaryDetected) {
            LOG.warn("Canary resource leak wasn't detected. Leak detection not functional")
        }
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
        if (resourceType.contains(Canary.class.getSimpleName())) {
            canaryDetected = true
            return
        }

        leakDetected = true
        super.reportTracedLeak(resourceType, records)
    }

    @Override
    protected void reportUntracedLeak(String resourceType) {
        if (resourceType.contains(Canary.class.getSimpleName())) {
            canaryDetected = true
            return
        }

        leakDetected = true
        super.reportUntracedLeak(resourceType)
    }

    private static class Canary {
        private static final ResourceLeakDetector<Canary> CANARY_LEAK_DETECTOR = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Canary)
    }
}
