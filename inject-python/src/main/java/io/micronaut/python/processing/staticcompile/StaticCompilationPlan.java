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

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the planner decided for a compilation: one decision per function, the bodies it lowered,
 * and the diagnostics for the explicit switches it could not honour.
 *
 * @param decisions   The decisions, in source order
 * @param bodies      The compiled bodies by {@link Ir.CompiledBody#key()}
 * @param diagnostics The warnings (errors in strict mode) for explicit switches that are not honoured
 * @param trace       Whether the compiled bodies count their entries
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record StaticCompilationPlan(List<StaticCompilationDecision> decisions,
                                    Map<String, Ir.CompiledBody> bodies,
                                    List<PythonDiagnostic> diagnostics,
                                    boolean trace) {

    /**
     * An empty plan.
     */
    public static final StaticCompilationPlan EMPTY = new StaticCompilationPlan(List.of(), Map.of(), List.of(), false);

    public StaticCompilationPlan {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        bodies = bodies == null ? Map.of() : Map.copyOf(bodies);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /**
     * A plan without tracing.
     *
     * @param decisions   The decisions
     * @param bodies      The compiled bodies
     * @param diagnostics The diagnostics
     */
    public StaticCompilationPlan(List<StaticCompilationDecision> decisions, Map<String, Ir.CompiledBody> bodies, List<PythonDiagnostic> diagnostics) {
        this(decisions, bodies, diagnostics, false);
    }

    /**
     * A plan without compiled bodies.
     *
     * @param decisions   The decisions
     * @param diagnostics The diagnostics
     */
    public StaticCompilationPlan(List<StaticCompilationDecision> decisions, List<PythonDiagnostic> diagnostics) {
        this(decisions, Map.of(), diagnostics);
    }

    /**
     * @param trace Whether the compiled bodies count their entries
     * @return The plan with tracing set
     */
    public StaticCompilationPlan withTrace(boolean trace) {
        return new StaticCompilationPlan(decisions, bodies, diagnostics, trace);
    }

    /**
     * @param className The generated class
     * @return Whether any method of the class is compiled
     */
    public boolean compilesAnyMethodOf(String className) {
        String prefix = className + "#";
        for (String key : bodies.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param className The generated class
     * @return The compiled bodies of the class
     */
    public List<Ir.CompiledBody> bodiesOf(String className) {
        List<Ir.CompiledBody> result = new java.util.ArrayList<>();
        for (Ir.CompiledBody body : bodies.values()) {
            if (body.className().equals(className)) {
                result.add(body);
            }
        }
        return result;
    }

    /**
     * @param bodies The compiled bodies
     * @return The bodies by key
     */
    public static Map<String, Ir.CompiledBody> byKey(List<Ir.CompiledBody> bodies) {
        Map<String, Ir.CompiledBody> map = new LinkedHashMap<>();
        for (Ir.CompiledBody body : bodies) {
            map.put(body.key(), body);
        }
        return map;
    }

    /**
     * @param className  The generated class
     * @param methodName The method
     * @return The compiled body of the method, or {@code null} when it is not compiled
     */
    public Ir.@Nullable CompiledBody body(String className, String methodName) {
        return bodies.get(Ir.CompiledBody.key(className, methodName));
    }
}
