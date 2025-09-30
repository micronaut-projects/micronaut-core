/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.tck.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Utility for testing for netty buffer leaks.
 *
 * @since 4.8.19
 * @author Jonas Konrad
 */
public final class TestLeakDetector {
    private static final Logger LOG = LoggerFactory.getLogger(TestLeakDetector.class);
    private static final String BASE_CANARY_STRING = "canary-" + UUID.randomUUID() + "-";

    private static final List<ResourceLeakDetector<?>> ALL_DETECTORS = new CopyOnWriteArrayList<>();

    private static volatile boolean leakDetected;
    private static volatile boolean canaryDetected;
    private static volatile String canaryString;

    private static volatile long sink;

    static {
        System.setProperty("io.netty.leakDetection.level", "paranoid"); // this prevents vertx from resetting it
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(new ResourceLeakDetectorFactory() {
            @Override
            public <T> ResourceLeakDetector<T> newResourceLeakDetector(Class<T> resource, int samplingInterval, long maxActive) {
                return new TestResourceLeakDetector<>(resource, samplingInterval);
            }

            @Override
            public <T> ResourceLeakDetector<T> newResourceLeakDetector(Class<T> resource, int samplingInterval) {
                return new TestResourceLeakDetector<>(resource, samplingInterval);
            }
        });
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    private TestLeakDetector() {
    }

    /**
     * Initialize the leak detector.
     */
    public static void init() {
        // run static initializer
    }

    /**
     * Start tracking leaks.
     *
     * @param testName The current test name
     */
    public static void startTracking(String testName) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

        triggerGc();

        leakDetected = false;

        LOG.debug("Starting resource leak tracking");
    }

    /**
     * Stop tracking leaks.
     *
     * @throws RuntimeException If there was a leak since the last {@link #startTracking(String)}
     */
    public static void stopTrackingAndReportLeaks() {
        triggerGc();

        if (leakDetected) {
            throw new RuntimeException("Detected a resource leak. Please check logs");
        } else {
            LOG.debug("No resource leak detected");
        }
    }

    private static void leakCanary() {
        ByteBuf resource = ByteBufAllocator.DEFAULT.directBuffer();
        resource.touch(canaryString);
    }

    private static void triggerGc() {
        // timeout of last resort for the loop below. use nanoTime because it's monotonic
        long startTime = System.nanoTime();

        // need to randomize this every time, since ResourceLeakDetector will deduplicate leaks
        canaryString = BASE_CANARY_STRING + UUID.randomUUID();
        canaryDetected = false;

        leakCanary();

        do {
            if (System.nanoTime() - startTime > 30_000_000_000L) {
                LOG.warn("Canary leak detection failed.");
                break;
            }

            // Trigger GC.
            System.gc();

            // trigger detectors – ref queue collection is only done on track()
            //noinspection rawtypes
            for (ResourceLeakDetector detector : ALL_DETECTORS) {
                Object obj = new Object();
                //noinspection unchecked
                ResourceLeakTracker track = detector.track(obj);
                if (track == null) {
                    throw new RuntimeException("getLevel: " + ResourceLeakDetector.getLevel() + " detector: " + detector);
                }
                track.close(obj);
            }

            // Give the GC something to work on.
            for (int i = 0; i < 1000; i++) {
                sink = System.identityHashCode(new byte[10000]);
            }
        } while (!canaryDetected && !Thread.interrupted());
    }

    private static final class TestResourceLeakDetector<T> extends ResourceLeakDetector<T> {
        public TestResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
            super(resourceType, samplingInterval);
            ALL_DETECTORS.add(this);
        }

        @Override
        protected boolean needReport() {
            return true;
        }

        @Override
        protected void reportTracedLeak(String resourceType, String records) {
            String canary = canaryString;
            if (canary != null && records.contains(canary)) {
                canaryDetected = true;
                return;
            }
            if (records.contains(BASE_CANARY_STRING)) {
                // probably a canary from another run that ran into a timeout, drop
                return;
            }

            leakDetected = true;
            super.reportTracedLeak(resourceType, records);
        }

        @Override
        protected void reportUntracedLeak(String resourceType) {
            leakDetected = true;
            super.reportUntracedLeak(resourceType);
        }
    }
}
