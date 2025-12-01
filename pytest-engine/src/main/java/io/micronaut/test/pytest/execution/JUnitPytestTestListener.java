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
package io.micronaut.test.pytest.execution;

import io.micronaut.test.pytest.listener.PytestTestListener;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Adapter that implements PytestTestListener and forwards events to JUnit EngineExecutionListener.
 */
public class JUnitPytestTestListener implements PytestTestListener {

    private static final Logger LOG = LoggerFactory.getLogger(JUnitPytestTestListener.class);
    private final EngineExecutionListener junitListener;

    public JUnitPytestTestListener(EngineExecutionListener junitListener) {
        this.junitListener = junitListener;
    }

    @Override
    public void beforeFile(Path file) {
        LOG.debug("Pytest starting file: {}", file);
    }

    @Override
    public void afterFile(Path file, TestExecutionResult result) {
        LOG.debug("Pytest finished file: {} with result: {}", file, result.getStatus());
    }

    @Override
    public void beforeTest(String testId) {
        LOG.debug("Pytest starting test: {}", testId);
    }

    @Override
    public void afterTest(String testId, TestExecutionResult result) {
        LOG.debug("Pytest finished test: {} with result: {}", testId, result.getStatus());
    }

    @Override
    public void onResult(TestExecutionResult result) {
        LOG.debug("Pytest session completed with result: {}", result.getStatus());
    }
}
