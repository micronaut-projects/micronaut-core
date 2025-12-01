/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.test.pytest.listener;

import org.junit.platform.engine.TestExecutionResult;

import java.nio.file.Path;

/**
 * Listener interface for pytest test execution events.
 * This interface allows Java code to receive notifications about pytest test execution.
 */
public interface PytestTestListener {

    /**
     * Called before pytest starts processing a test file.
     *
     * @param file the path to the Python test file
     */
    void beforeFile(Path file);

    /**
     * Called after pytest finishes processing a test file.
     *
     * @param file the path to the Python test file
     * @param result the overall result for the file
     */
    void afterFile(Path file, TestExecutionResult result);

    /**
     * Called before an individual test starts execution.
     *
     * @param testId the unique identifier of the test
     */
    void beforeTest(String testId);

    /**
     * Called after an individual test finishes execution.
     *
     * @param testId the unique identifier of the test
     * @param result the result of the test execution
     */
    void afterTest(String testId, TestExecutionResult result);

    /**
     * Called when test execution is complete with overall results.
     *
     * @param result the overall test execution result
     */
    void onResult(TestExecutionResult result);
}
