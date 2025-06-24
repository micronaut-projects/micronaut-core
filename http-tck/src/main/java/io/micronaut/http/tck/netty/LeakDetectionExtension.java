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
