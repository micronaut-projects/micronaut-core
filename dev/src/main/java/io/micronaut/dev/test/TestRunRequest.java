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
import io.micronaut.dev.compile.SourceRoot;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a runner runs.
 *
 * @param runId The run's identifier, for the events
 * @param classLoader The loader of the test classes and the classes under test: a generation's loader in
 *                    development mode. It is the thread context loader while the run lasts
 * @param testClassOutputs The directories holding the compiled tests
 * @param testSources The test source roots, for runners that select by file
 * @param selection Which tests run
 * @param parameters The runner's configuration, from the manifest
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestRunRequest(String runId,
                             ClassLoader classLoader,
                             List<Path> testClassOutputs,
                             List<SourceRoot> testSources,
                             TestSelection selection,
                             Map<String, String> parameters) {

    /**
     * Validating constructor.
     *
     * @param runId The identifier
     * @param classLoader The loader
     * @param testClassOutputs The class outputs
     * @param testSources The sources
     * @param selection The selection
     * @param parameters The parameters
     */
    public TestRunRequest {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(classLoader, "classLoader");
        testClassOutputs = List.copyOf(Objects.requireNonNull(testClassOutputs, "testClassOutputs"));
        testSources = List.copyOf(Objects.requireNonNull(testSources, "testSources"));
        Objects.requireNonNull(selection, "selection");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
}
