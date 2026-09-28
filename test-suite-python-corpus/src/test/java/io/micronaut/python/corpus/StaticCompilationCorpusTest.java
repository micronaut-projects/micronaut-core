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
package io.micronaut.python.corpus;

import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.typecheck.TypeCheckMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles the pinned corpus of real Pyronaut applications with the type checker at {@code error}
 * and static compilation in mode {@code all}: both must build, the checker must report nothing,
 * and the number of compiled bodies must not fall below what the compiler reaches today. The
 * report of each compilation lands under {@code build/corpus/<name>/report}.
 */
class StaticCompilationCorpusTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "fullstack, 41",
        "petclinic, 32",
    })
    void theCorpusBuildsInModeAllWithTheCheckerSilent(String corpus, int compiledAtLeast) {
        File pythonSrc = new File(System.getProperty("corpus." + corpus + ".dir", "src/corpus/" + corpus));
        File out = new File("build/corpus/" + corpus);
        File targetDir = new File(out, "classes");
        List<String> diagnostics = new ArrayList<>();
        List<StaticCompilationDecision> decisions = new ArrayList<>();
        targetDir.mkdirs();
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonSrc(pythonSrc.getPath())
            .targetDir(targetDir)
            .classpath(files(System.getProperty("corpus." + corpus + ".classpath")))
            .annotationProcessorPath(files(System.getProperty("corpus." + corpus + ".processorPath")))
            .typeCheck(TypeCheckMode.ERROR)
            .staticCompilation(StaticCompilationMode.ALL)
            .staticCompilationReport(new File(out, "report"))
            .verboseErrors(true)
            .errorDumpDirectory(new File(out, "error-dump"))
            .pythonDiagnosticCallback(diagnostic -> diagnostics.add(diagnostic.toString()))
            .staticCompilationDecisionCallback(decisions::add)
            .build();

        compiler.compile();

        Map<String, Integer> outcomes = new TreeMap<>();
        Map<String, Integer> reasons = new TreeMap<>();
        for (StaticCompilationDecision decision : decisions) {
            outcomes.merge(decision.outcome().name(), 1, Integer::sum);
            for (StaticCompilationDecision.Reason reason : decision.reasons()) {
                reasons.merge(reason.rule(), 1, Integer::sum);
            }
        }
        System.out.println("== " + corpus + ": " + decisions.size() + " decisions " + outcomes + ", reasons " + reasons);
        assertEquals(List.of(), diagnostics, "the type checker reports nothing on " + corpus);
        int compiled = outcomes.getOrDefault("COMPILED", 0);
        assertTrue(compiled >= compiledAtLeast,
            corpus + " compiled " + compiled + " bodies, fewer than the " + compiledAtLeast + " it compiled before: " + decisions);
    }

    private static List<File> files(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(File.pathSeparator)).map(File::new).toList();
    }
}
