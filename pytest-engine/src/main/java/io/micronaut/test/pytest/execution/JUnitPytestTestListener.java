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

import io.micronaut.test.pytest.PytestFileDescriptor;
import io.micronaut.test.pytest.PytestTestDescriptor;
import io.micronaut.test.pytest.listener.PytestTestListener;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Adapter that implements PytestTestListener and forwards events to JUnit EngineExecutionListener.
 */
public class JUnitPytestTestListener implements PytestTestListener {

    private static final Logger LOG = LoggerFactory.getLogger(JUnitPytestTestListener.class);
    private final EngineExecutionListener junitListener;
    private final PytestFileDescriptor fileDescriptor;

    public JUnitPytestTestListener(
        EngineExecutionListener junitListener,
        PytestFileDescriptor fileDescriptor) {
        this.junitListener = junitListener;
        this.fileDescriptor = fileDescriptor;
    }

    @Override
    public void beforeFile(String file) {
        LOG.debug("Pytest starting file: {}", file);
        junitListener.executionStarted(
            fileDescriptor
        );
    }

    @Override
    public void afterFile(String file) {
        LOG.debug("Pytest finished file: {}", file);
        junitListener.executionFinished(
            fileDescriptor,
            // failures reported in children.
            TestExecutionResult.successful()
        );
    }

    @Override
    public void beforeTest(String testId) {
        LOG.debug("Pytest starting test: {}", testId);
        fileDescriptor.getChildren()
            .stream()
            .filter(child -> child instanceof PytestTestDescriptor ptd &&
                testId.endsWith("::" + ptd.getUniqueId().getSegments().getLast().getValue()))
            .findAny().ifPresent(junitListener::executionStarted);
    }

    @Override
    public void afterTest(String testId, TestExecutionResult result) {
        LOG.debug("Pytest finished test: {} with result: {}", testId, result);
        fileDescriptor.getChildren()
            .stream()
            .filter(child -> child instanceof PytestTestDescriptor ptd &&
                testId.endsWith("::" + ptd.getUniqueId().getSegments().getLast().getValue()))
            .findAny().ifPresent(td -> {
                junitListener.executionFinished(td, result);
            });
    }

    @Override
    public void onResult(TestExecutionResult result) {
        LOG.debug("Pytest session completed");
    }
}
