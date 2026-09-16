/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.io.buffer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Netty is {@code compileOnly} for this module, so its tests run without it: both entry points of
 * {@link LeakTracker.Factory} must survive that, whichever runs first, and the supplier handed to
 * {@link LeakTracker.Factory#staticInitializer} must run exactly once.
 */
class LeakTrackerWithoutNettyTest {

    private static boolean nettyAbsent() {
        try {
            Class.forName("io.netty.util.ResourceLeakDetector");
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    @Test
    void staticInitializerRunsTheSupplierOnceWithoutNetty() {
        assumeTrue(nettyAbsent(), "netty is on the test class path");
        LeakTrackerFactoryHolder.nettyAvailable = true;
        AtomicInteger calls = new AtomicInteger();

        String value = LeakTracker.Factory.staticInitializer(() -> {
            calls.incrementAndGet();
            return "value";
        });

        assertEquals("value", value);
        assertEquals(1, calls.get());
        assertFalse(LeakTrackerFactoryHolder.nettyAvailable);
        assertFalse(LeakTrackerFactoryHolder.checkNettyAvailable());
    }

    @Test
    void aSupplierFailureIsNotMistakenForAMissingNetty() {
        assumeTrue(nettyAbsent(), "netty is on the test class path");
        LeakTrackerFactoryHolder.nettyAvailable = true;
        AtomicInteger calls = new AtomicInteger();

        assertThrows(NoClassDefFoundError.class, () -> LeakTracker.Factory.staticInitializer(() -> {
            calls.incrementAndGet();
            throw new NoClassDefFoundError("from the supplier");
        }));

        // the supplier's own failure propagates unchanged after a single run
        assertEquals(1, calls.get());
    }

    @Test
    void forClassFallsBackToNoTrackingWithoutNetty() {
        assumeTrue(nettyAbsent(), "netty is on the test class path");
        LeakTrackerFactoryHolder.nettyAvailable = true;

        LeakTracker.Factory<Object> factory = LeakTracker.Factory.forClass(Object.class);

        assertNull(factory.track(new Object()));
        assertFalse(LeakTrackerFactoryHolder.nettyAvailable);
        // once recorded, the static initializer takes the fallback directly
        assertEquals("again", LeakTracker.Factory.staticInitializer(() -> "again"));
    }
}
