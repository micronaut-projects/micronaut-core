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
 * The events of a run, from which every report is built.
 *
 * <p>A runner calls these from the thread running the tests, in order: {@link #runStarted} once, then for
 * each test {@link #testStarted}, any {@link #output}, and {@link #testFinished}, then {@link #runFinished}
 * once. A test that could not start, as when its class failed to set up or was disabled, is reported with
 * {@link #testFinished} alone. An implementation must not throw; one that does is logged and skipped.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface TestEventListener {

    /**
     * A run starts.
     *
     * @param event The run
     */
    default void runStarted(TestRunStarted event) {
    }

    /**
     * A test starts.
     *
     * @param test The test
     */
    default void testStarted(TestId test) {
    }

    /**
     * A test wrote to a stream.
     *
     * @param test The test
     * @param stream The stream
     * @param text What it wrote
     */
    default void output(TestId test, TestOutput stream, String text) {
    }

    /**
     * A test finished, or was skipped without starting.
     *
     * @param test The test
     * @param outcome How it ended
     */
    default void testFinished(TestId test, TestOutcome outcome) {
    }

    /**
     * The run finished.
     *
     * @param summary What it did
     */
    default void runFinished(TestRunSummary summary) {
    }
}
