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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

/**
 * Runs tests, streaming what happens.
 *
 * <p>Implementations are discovered through {@code META-INF/services} and chosen by {@link #id()}; the
 * {@link JUnitPlatformTestRunner JUnit Platform runner} is built in. A runner owns discovery and execution;
 * development mode owns which tests run, the loaders, and the reports. A runner that is asked to cancel
 * stops as soon as it can and reports the run as cancelled.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface TestRunner {

    /**
     * @return The runner's identifier, such as {@code junit-platform}
     */
    String id();

    /**
     * Whether the runner can run in this JVM.
     *
     * @return True if its implementation is present
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Runs tests. Every event goes to the listener, {@link TestEventListener#runStarted} first and
     * {@link TestEventListener#runFinished} last, whatever happens.
     *
     * @param request What to run
     * @param listener Where the events go
     * @param cancellation Asks the run to stop early
     * @return What the run did, as given to {@link TestEventListener#runFinished}
     */
    TestRunSummary run(TestRunRequest request, TestEventListener listener, Cancellation cancellation);
}
