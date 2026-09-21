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
package io.micronaut.python.processing.staticcompile;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.python.processing.diagnostic.PythonDiagnostic;

import java.util.List;

/**
 * What the planner decided for a compilation: one decision per function, and the diagnostics for
 * the explicit switches it could not honour.
 *
 * @param decisions   The decisions, in source order
 * @param diagnostics The warnings (errors in strict mode) for explicit switches that are not honoured
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record StaticCompilationPlan(List<StaticCompilationDecision> decisions, List<PythonDiagnostic> diagnostics) {

    /**
     * An empty plan.
     */
    public static final StaticCompilationPlan EMPTY = new StaticCompilationPlan(List.of(), List.of());

    public StaticCompilationPlan {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
