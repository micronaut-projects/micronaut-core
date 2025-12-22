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
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Executes Python tests using pytest via GraalPy.
 */
public class PytestTestExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(PytestTestExecutor.class);

    private final EngineExecutionListener listener;
    private final Context context;

    public PytestTestExecutor(Context context, EngineExecutionListener listener) {
        this.listener = listener;
        this.context = context;
    }

    /**
     * Executes the test descriptor and its children.
     */
    public void execute(TestDescriptor descriptor) {
        LOG.debug("Executing test descriptor: {}", descriptor.getDisplayName());

        listener.executionStarted(descriptor);

        try {
            if (descriptor instanceof PytestFileDescriptor fileDescriptor) {
                executeFile(fileDescriptor);
            } else if (descriptor instanceof PytestTestDescriptor testDescriptor) {
                executeTest(testDescriptor);
            } else {
                // For the engine descriptor, run pytest on all discovered test files
                runPytestForAllTests(descriptor);
            }

            listener.executionFinished(descriptor, TestExecutionResult.successful());

        } catch (Exception e) {
            LOG.error("Error executing test descriptor: {}", descriptor.getDisplayName(), e);
            listener.executionFinished(descriptor, TestExecutionResult.failed(e));
        }
    }

    private void executeFile(PytestFileDescriptor fileDescriptor) {
        LOG.debug("Executing Python file: {}", fileDescriptor.getDisplayName());

        try {
            // Run pytest on this file with our custom plugin
            runPytestForFile(fileDescriptor);
        } catch (Exception e) {
            LOG.error("Error running pytest for file: {}", fileDescriptor.getDisplayName(), e);
            // Mark all child tests as failed
            for (TestDescriptor child : fileDescriptor.getChildren()) {
                if (child instanceof PytestTestDescriptor testDescriptor) {
                    listener.executionStarted(testDescriptor);
                    listener.executionFinished(testDescriptor, TestExecutionResult.failed(e));
                }
            }
        }
    }

    private void runPytestForFile(PytestFileDescriptor fileDescriptor) throws Exception {
        LOG.debug("Running pytest for file: {}", fileDescriptor.getDisplayName());

        Path filePath = Paths.get(fileDescriptor.getUniqueId().getSegments().get(1).getValue());
        try {
            JUnitPytestTestListener testListener = new JUnitPytestTestListener(listener, fileDescriptor.getChildren());
            // Call run_pytest with the file path and listener
            Value result = context.eval("python", """
from pyronaut.test import run_pytest

run_pytest
            """).execute(
                new String[]{filePath.toString()},
                testListener
            );

            LOG.debug("Pytest execution completed for file: {}", fileDescriptor.getDisplayName());

        } catch (Exception e) {
            LOG.error("Error running pytest for file: {}", fileDescriptor.getDisplayName(), e);
            throw e;
        }
    }

    private void runPytestForAllTests(TestDescriptor engineDescriptor) throws Exception {
        LOG.debug("Running pytest for all discovered tests");

        // Collect all unique test files from the test descriptors
        List<Path> testFiles = engineDescriptor.getChildren().stream()
            .filter(child -> child instanceof PytestTestDescriptor)
            .map(child -> ((PytestTestDescriptor) child).getFilePath())
            .toList();

        if (testFiles.isEmpty()) {
            LOG.debug("No test files found to execute");
            return;
        }

        LOG.debug("Running pytest on {} test files: {}", testFiles.size(), testFiles);

        try {
            JUnitPytestTestListener testListener = new JUnitPytestTestListener(listener, engineDescriptor.getChildren());
            // Convert paths to strings for pytest
            String[] fileArgs = testFiles.stream()
                .map(Path::toString)
                .toArray(String[]::new);

            // Call run_pytest with the file paths and listener
            context.eval("python", """
from pyronaut.test import run_pytest

run_pytest
            """).execute(
                fileArgs,
                testListener
            );

            LOG.debug("Pytest execution completed for all tests");

        } catch (Exception e) {
            LOG.error("Error running pytest for all tests", e);
            throw e;
        }
    }

    private void executeTest(PytestTestDescriptor testDescriptor) {
        LOG.debug("Executing Python test: {}", testDescriptor.getDisplayName());

        listener.executionStarted(testDescriptor);

        try {
            // Individual test execution is handled by pytest plugin
            // Just mark as successful since pytest will handle the actual execution
            LOG.debug("Test {} execution delegated to pytest", testDescriptor.getDisplayName());
            listener.executionFinished(testDescriptor, TestExecutionResult.successful());

        } catch (Exception e) {
            LOG.error("Test {} failed", testDescriptor.getDisplayName(), e);
            listener.executionFinished(testDescriptor, TestExecutionResult.failed(e));
        }
    }
}
