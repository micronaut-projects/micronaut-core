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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Sample implementation of PytestTestListener that logs all test events.
 */
public class LoggingPytestTestListener implements PytestTestListener {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPytestTestListener.class);

    @Override
    public void beforeFile(Path file) {
        LOG.info("Starting pytest execution for file: {}", file);
    }

    @Override
    public void afterFile(Path file, TestExecutionResult result) {
        LOG.info("Finished pytest execution for file: {} with result: {}", file, result.getStatus());
        if (result.getThrowable().isPresent()) {
            LOG.error("File execution failed: {}", file, result.getThrowable().get());
        }
    }

    @Override
    public void beforeTest(String testId) {
        LOG.debug("Starting test execution: {}", testId);
    }

    @Override
    public void afterTest(String testId, TestExecutionResult result) {
        LOG.debug("Finished test execution: {} with result: {}", testId, result.getStatus());
        if (result.getThrowable().isPresent()) {
            LOG.error("Test execution failed: {}", testId, result.getThrowable().get());
        }
    }

    @Override
    public void onResult(TestExecutionResult result) {
        LOG.info("Pytest execution completed with overall result: {}", result.getStatus());
        if (result.getThrowable().isPresent()) {
            LOG.error("Overall execution failed", result.getThrowable().get());
        }
    }
}
