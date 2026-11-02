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
import io.micronaut.python.processing.model.SourceSpan;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.typecheck.TypeCheckMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compiles the pinned corpus of real Pyronaut applications with the type checker at {@code error}
 * and static compilation in mode {@code all}: both must build, the checker must report nothing,
 * and every decision (each function with its outcome) must be the one recorded in
 * {@code src/corpus/<name>.decisions.txt}, so a regression that loses some compiled bodies and
 * gains others fails as surely as one that only loses. The output of each compilation, cleaned
 * before it, lands under {@code build/corpus/<name>}: its report, and the decisions it made in
 * {@code decisions.txt}, which replaces the recorded file when the change is intended.
 */
class StaticCompilationCorpusTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"fullstack", "petclinic"})
    void theCorpusBuildsInModeAllWithTheCheckerSilent(String corpus) throws IOException {
        String override = System.getProperty("corpus." + corpus + ".dir");
        File pythonSrc = new File(override != null ? override : "src/corpus/" + corpus);
        File out = new File("build/corpus/" + corpus);
        // the classes and the report of an earlier run must not hide what this one fails to write
        deleteRecursively(out.toPath());
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
        List<String> made = decisions.stream().map(decision -> line(pythonSrc, decision)).sorted().toList();
        Path madeFile = new File(out, "decisions.txt").toPath();
        Files.write(madeFile, made);
        assertEquals(List.of(), diagnostics, "the type checker reports nothing on " + corpus);
        if (override != null) {
            return; // a checkout of the project: its decisions are reported, not compared
        }
        Path recordedFile = Path.of("src/corpus/" + corpus + ".decisions.txt");
        List<String> recorded = Files.readAllLines(recordedFile).stream().filter(line -> !line.isBlank()).toList();
        if (!recorded.equals(made)) {
            List<String> lost = recorded.stream().filter(line -> !made.contains(line)).toList();
            List<String> gained = made.stream().filter(line -> !recorded.contains(line)).toList();
            fail("the decisions on " + corpus + " changed (" + recorded.size() + " recorded, " + made.size() + " made)"
                + "\n  no longer made:\n    " + String.join("\n    ", lost)
                + "\n  newly made:\n    " + String.join("\n    ", gained)
                + "\nwhen the change is intended, copy " + madeFile + " to " + recordedFile);
        }
    }

    /**
     * @return The decision as a line of the recorded file: the source relative to the corpus, the function, the outcome
     */
    private static String line(File pythonSrc, StaticCompilationDecision decision) {
        SourceSpan span = decision.span();
        String source = "?";
        if (span != null) {
            Path root = pythonSrc.toPath().toAbsolutePath().normalize();
            Path path = Path.of(span.path()).toAbsolutePath().normalize();
            source = (path.startsWith(root) ? root.relativize(path) : path).toString().replace(File.separatorChar, '/');
        }
        return source + " " + decision.qualifiedName() + " " + decision.outcome().name();
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static List<File> files(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(File.pathSeparator)).map(File::new).toList();
    }
}
