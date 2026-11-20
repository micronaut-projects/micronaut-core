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

import io.micronaut.python.compiler.CompilationProfile;
import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.python.processing.PipelineTimings;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.typecheck.TypeCheckMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Compiles applications of the corpus in a JVM of its own, as a build does: the arguments are
 * triples of the name of the class paths (the system properties {@code corpus.<name>.classpath}
 * and {@code corpus.<name>.processorPath} hold them), the source directory and the output
 * directory, after an optional {@code --mode=<mode>} (default {@code all}). Prints a line per
 * compilation: {@value #RESULT}, the name, the mode, the wall time of the compilation in
 * milliseconds, the number of decisions and of compiled functions, then the time of every stage
 * and phase as {@code <stage>=<ms>}, separated by tabs.
 */
public final class CorpusCompilation {

    /**
     * The prefix of the line reporting a compilation.
     */
    static final String RESULT = "corpus-compilation";

    /**
     * The phases of the compilation profile reported with the stages of {@link PipelineTimings}.
     */
    static final List<String> PHASES = List.of(
        "python.visitors.isolating", "python.bean-definitions", "javac.analyze", "javac.generate", "javac.task"
    );

    private CorpusCompilation() {
    }

    /**
     * @param args The arguments
     */
    public static void main(String[] args) {
        int first = 0;
        StaticCompilationMode mode = StaticCompilationMode.ALL;
        if (args.length > 0 && args[0].startsWith("--mode=")) {
            mode = StaticCompilationMode.fromOption(args[0].substring("--mode=".length()));
            first = 1;
        }
        for (int i = first; i + 2 < args.length; i += 3) {
            String name = args[i];
            File out = new File(args[i + 2]);
            File classes = new File(out, "classes");
            classes.mkdirs();
            List<String> diagnostics = new ArrayList<>();
            List<StaticCompilationDecision> decisions = new ArrayList<>();
            AtomicReference<CompilationProfile> profile = new AtomicReference<>();
            PyronautCompiler compiler = PyronautCompiler.builder()
                .pythonSrc(args[i + 1])
                .targetDir(classes)
                .classpath(files(System.getProperty("corpus." + name + ".classpath")))
                .annotationProcessorPath(files(System.getProperty("corpus." + name + ".processorPath")))
                .typeCheck(TypeCheckMode.ERROR)
                .staticCompilation(mode)
                .staticCompilationReport(new File(out, "report"))
                .pythonDiagnosticCallback(diagnostic -> diagnostics.add(diagnostic.toString()))
                .staticCompilationDecisionCallback(decisions::add)
                .profileCallback(profile::set)
                .build();
            Map<String, Long> before = PipelineTimings.nanos();
            long started = System.nanoTime();
            compiler.compile();
            double millis = (System.nanoTime() - started) / 1_000_000.0;
            Map<String, Long> after = PipelineTimings.nanos();
            if (!diagnostics.isEmpty()) {
                throw new IllegalStateException("the type checker reports on " + name + ": " + diagnostics);
            }
            Map<String, Double> stages = new LinkedHashMap<>();
            after.forEach((stage, nanos) -> stages.put(stage, (nanos - before.getOrDefault(stage, 0L)) / 1_000_000.0));
            for (String phase : PHASES) {
                CompilationProfile.Phase recorded = profile.get() == null ? null : profile.get().phase(phase);
                stages.put(phase, recorded == null ? 0.0 : recorded.millis());
            }
            System.out.println(RESULT + "\t" + name + "\t" + mode.optionValue() + "\t" + String.format(Locale.ROOT, "%.1f", millis) + "\t"
                + decisions.size() + "\t" + decisions.stream().filter(decision -> decision.outcome() == StaticCompilationDecision.Outcome.COMPILED).count() + "\t"
                + stages.entrySet().stream()
                    .map(stage -> stage.getKey() + "=" + String.format(Locale.ROOT, "%.1f", stage.getValue()))
                    .collect(Collectors.joining("\t")));
        }
    }

    private static List<File> files(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(File.pathSeparator)).map(File::new).toList();
    }
}
