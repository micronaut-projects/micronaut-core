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
package io.micronaut.dev.manifest;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How test mode runs the tests.
 *
 * @param runner The identifier of the test runner, {@code junit-platform} by default
 * @param affectedOnly Whether a change runs only the tests it can affect, rather than every test
 * @param initialRun Whether every test runs once when the runtime starts
 * @param once Whether the runtime runs the tests once and stops, for a build that wants the result
 * @param reports Where the JUnit XML reports go
 * @param htmlReport Where a report that renders the runs as a page, such as micronaut-dev-test-report, writes it
 * @param htmlReportPath The path the LiveReload server serves that page at, {@code /tests/} by default
 * @param patterns The {@code --tests} patterns every run is filtered by
 * @param parameters The runner's configuration parameters
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestSettings(String runner, boolean affectedOnly, boolean initialRun, boolean once, Path reports, Path htmlReport, String htmlReportPath, List<String> patterns, Map<String, String> parameters) {

    /**
     * Validating constructor.
     *
     * @param runner The runner
     * @param affectedOnly Whether only affected tests run
     * @param initialRun Whether every test runs at start
     * @param once Whether the runtime stops after one run
     * @param reports The reports directory
     * @param htmlReport The page report directory
     * @param htmlReportPath The page report path
     * @param patterns The patterns
     * @param parameters The parameters
     */
    public TestSettings {
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(reports, "reports");
        Objects.requireNonNull(htmlReport, "htmlReport");
        Objects.requireNonNull(htmlReportPath, "htmlReportPath");
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    /**
     * Settings whose page report is served at {@code /tests/}.
     *
     * @param runner The runner
     * @param affectedOnly Whether only affected tests run
     * @param initialRun Whether every test runs at start
     * @param once Whether the runtime stops after one run
     * @param reports The reports directory
     * @param htmlReport The page report directory
     * @param patterns The patterns
     * @param parameters The parameters
     */
    public TestSettings(String runner, boolean affectedOnly, boolean initialRun, boolean once, Path reports, Path htmlReport, List<String> patterns, Map<String, String> parameters) {
        this(runner, affectedOnly, initialRun, once, reports, htmlReport, "/tests/", patterns, parameters);
    }
}
