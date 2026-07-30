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
package io.micronaut.python.compiler;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compiler facts collected while javac processes the selected source set.
 *
 * @param analyzedSources The sources analyzed by javac
 * @param dependencies The resolved source dependency graph
 * @param declaredTypes The types declared by each source
 * @param outputs The isolating outputs attributed to each source
 * @param aggregatingOutputs The outputs created by aggregating processors
 * @param pythonProcessorOutputs The outputs created by Python processing
 * @param contractViolatingOutputs The outputs with invalid isolating origins
 * @param processorCompatible Whether every processor supports incremental processing
 */
record IncrementalCompilationTrace(Set<String> analyzedSources,
                                   Map<String, Set<String>> dependencies,
                                   Map<String, Set<String>> declaredTypes,
                                   Map<String, Set<String>> outputs,
                                   Set<String> aggregatingOutputs,
                                   Set<String> pythonProcessorOutputs,
                                   Set<String> contractViolatingOutputs,
                                   boolean processorCompatible) {

    private static final IncrementalCompilationTrace EMPTY =
        new IncrementalCompilationTrace(Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), true);

    IncrementalCompilationTrace {
        analyzedSources = Set.copyOf(analyzedSources);
        dependencies = immutableCopy(dependencies);
        declaredTypes = immutableCopy(declaredTypes);
        outputs = immutableCopy(outputs);
        aggregatingOutputs = Set.copyOf(aggregatingOutputs);
        pythonProcessorOutputs = Set.copyOf(pythonProcessorOutputs);
        contractViolatingOutputs = Set.copyOf(contractViolatingOutputs);
    }

    static IncrementalCompilationTrace empty() {
        return EMPTY;
    }

    private static Map<String, Set<String>> immutableCopy(Map<String, Set<String>> values) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, Set.copyOf(new LinkedHashSet<>(value))));
        return Map.copyOf(copy);
    }
}
