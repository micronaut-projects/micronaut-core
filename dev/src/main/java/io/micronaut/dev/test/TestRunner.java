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

import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

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

    /**
     * The reloader of what this runner keeps alive between runs on one class loader, such as an interpreter
     * built over the generation, for test mode to patch a change into instead of starting a new generation.
     *
     * <p>Test mode asks after a change that holds no class and only changes resources the generation of the last
     * run already held, as the Python modules of an edit that changed only bodies. When the runner answers with a
     * reloader that {@link InPlaceResourceReloader#canReload can} take the change, test mode writes the new
     * contents into that generation, has the reloader {@link InPlaceResourceReloader#reload apply} them, and runs
     * the tests the change owes on the same loader: the next {@link TestRunRequest#classLoader()} is the one
     * given here. Anything else, an empty answer, a refusal or a failure, starts a new generation as before, which
     * discards a half-applied change; a runner rebuilds what it keeps when it is given a loader it has not seen.</p>
     *
     * <p>The default keeps nothing: every change starts a new generation.</p>
     *
     * @param classLoader The loader of the last run
     * @return The reloader, or empty when this runner keeps nothing built over that loader
     * @since 5.3.0
     */
    default Optional<InPlaceResourceReloader> inPlaceReloader(ClassLoader classLoader) {
        return Optional.empty();
    }
}
