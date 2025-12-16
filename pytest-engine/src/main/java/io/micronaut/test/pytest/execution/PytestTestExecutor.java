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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Executes Python tests using pytest via GraalPy.
 */
public class PytestTestExecutor {
    private static final String PYTEST_RESOURCES = "GRAALPY-VFS/io.micronaut/pytest-engine";
    private static final Logger LOG = LoggerFactory.getLogger(PytestTestExecutor.class);

    private final EngineExecutionListener listener;

    public PytestTestExecutor(EngineExecutionListener listener) {
        this.listener = listener;
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
                // Execute children
                for (TestDescriptor child : descriptor.getChildren()) {
                    execute(child);
                }
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
        var pyEnv = System.getenv("PYENV_VERSION");
        var venv = System.getenv("VIRTUAL_ENV");
        Context.Builder builder = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(PYTEST_RESOURCES)
                .build())
            // TODO: constrain this in future
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> true);
        if (pyEnv != null && venv != null && pyEnv.startsWith("graalpy")) {
            builder.option("python.Executable", Path.of(venv).resolve("bin/python").toString());
        }
        try (Context context = builder

            .build()) {
            JUnitPytestTestListener testListener = new JUnitPytestTestListener(listener, fileDescriptor);
            // Call run_pytest with the file path and listener
            Value result = context.eval("python", """
from pytest_runner import run_pytest

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
