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
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The compile-time budget of static compilation: compiling an application in mode {@code all}
 * should take at most 15% longer than compiling it in mode {@code off}, the type checker running
 * at {@code error} in both (its own cost is the checker's budget). The two real applications of the
 * corpus are compiled, and a larger one made of copies of each of their packages, in both modes,
 * interleaved in this JVM after a warm-up, so that the load of the machine and the warming of the
 * JVM affect both modes alike; the medians of the modes are compared.
 *
 * <p>The numbers, with the time of every stage of the pipeline in each mode, are always printed
 * and written to {@code build/corpus-budget/budget.txt}. A shared CI runner, its JDK and its
 * neighbours are not a measurement bench, so the ratio is asserted only when the system property
 * {@value #ASSERT_PROPERTY} is {@code true} (the Gradle property {@code -Pcorpus.budget.assert}),
 * against {@code corpus.budget.limit} (default {@value #BUDGET}); {@code corpus.budget.rounds},
 * {@code corpus.budget.warmup} and {@code corpus.budget.copies} set the repeats, the warm-up
 * compilations and the copies of the larger application (none with {@code 0}):</p>
 *
 * <pre>./gradlew :test-suite-python-corpus:test -Ppython-ci --tests '*StaticCompilationBudgetTest' \
 *     -Pcorpus.budget.assert -Pcorpus.budget.rounds=5 -Pcorpus.budget.copies=4</pre>
 */
class StaticCompilationBudgetTest {

    /**
     * The system property switching the assertion of the budget on.
     */
    static final String ASSERT_PROPERTY = "corpus.budget.assert";

    private static final double BUDGET = 1.15;
    private static final int SCALED_COPIES_DEFAULT = 1;
    private static final List<String> CORPORA = List.of("fullstack", "petclinic");
    // the stages of PipelineTimings, then the phases of the compilation profile, in pipeline order
    private static final List<String> STAGES = List.of(
        PipelineTimings.CONTEXT, PipelineTimings.TRANSFORM, PipelineTimings.PARSE, PipelineTimings.TYPE_CHECK,
        PipelineTimings.PLAN, PipelineTimings.BYTECODE, PipelineTimings.STUBS, PipelineTimings.PROCESSOR
    );
    private static final List<String> PHASES = List.of(
        "python.visitors.isolating", "python.bean-definitions", "javac.analyze", "javac.generate", "javac.task"
    );

    @Test
    void modeAllAddsAtMostFifteenPercentToTheCompilation() throws IOException {
        int rounds = Integer.getInteger("corpus.budget.rounds", 3);
        int warmup = Integer.getInteger("corpus.budget.warmup", 1);
        int copies = Integer.getInteger("corpus.budget.copies", SCALED_COPIES_DEFAULT);
        boolean asserting = Boolean.getBoolean(ASSERT_PROPERTY);
        double limit = Double.parseDouble(System.getProperty("corpus.budget.limit", Double.toString(BUDGET)));
        File root = new File("build/corpus-budget");
        deleteRecursively(root.toPath());

        List<Workload> workloads = new ArrayList<>();
        for (String corpus : CORPORA) {
            workloads.add(new Workload(corpus, new File("src/corpus/" + corpus), classpath(corpus, "classpath"), classpath(corpus, "processorPath")));
        }
        if (copies > 0) {
            workloads.add(scaled(new File(root, "scaled-src"), copies));
        }

        // the warm-up compiles every workload in both modes, so neither mode pays for loading the classes of the other
        for (int i = 0; i < warmup; i++) {
            for (Workload workload : workloads) {
                for (StaticCompilationMode mode : List.of(StaticCompilationMode.OFF, StaticCompilationMode.ALL)) {
                    workload.compile(root, mode);
                }
            }
        }
        for (Workload workload : workloads) {
            workload.samples.clear();
        }
        // the order alternates between the rounds, so a drift of the machine during a round favours neither mode
        for (int round = 0; round < rounds; round++) {
            List<StaticCompilationMode> order = round % 2 == 0
                ? List.of(StaticCompilationMode.OFF, StaticCompilationMode.ALL)
                : List.of(StaticCompilationMode.ALL, StaticCompilationMode.OFF);
            for (Workload workload : workloads) {
                for (StaticCompilationMode mode : order) {
                    workload.compile(root, mode);
                }
            }
        }

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "Static compilation compile-time budget: mode all over mode off, type checker at error, "
                + "%d rounds after %d warm-up, %s, %d processors, load %.2f%n",
            rounds, warmup, System.getProperty("java.vm.name") + " " + System.getProperty("java.runtime.version"),
            Runtime.getRuntime().availableProcessors(), loadAverage()));
        List<String> over = new ArrayList<>();
        for (Workload workload : workloads) {
            Summary off = workload.summary(StaticCompilationMode.OFF);
            Summary all = workload.summary(StaticCompilationMode.ALL);
            double ratio = all.median / off.median;
            report.append(String.format(Locale.ROOT, "%n== %s (%d functions, %d compiled in mode all)%n",
                workload.name, workload.decisions, workload.compiled));
            report.append(String.format(Locale.ROOT, "  total      off %8.1f ms [%.1f..%.1f]   all %8.1f ms [%.1f..%.1f]   %+7.1f ms  %+6.1f%%%n",
                off.median, off.min, off.max, all.median, all.min, all.max, all.median - off.median, (ratio - 1) * 100));
            for (String stage : workload.stageNames()) {
                double offStage = workload.stageMedian(StaticCompilationMode.OFF, stage);
                double allStage = workload.stageMedian(StaticCompilationMode.ALL, stage);
                report.append(String.format(Locale.ROOT, "  %-26s off %8.1f ms   all %8.1f ms   %+7.1f ms  %+6.1f%% of off%n",
                    stage, offStage, allStage, allStage - offStage, (allStage - offStage) / off.median * 100));
            }
            if (ratio > limit) {
                over.add(String.format(Locale.ROOT, "%s: %+.1f%%", workload.name, (ratio - 1) * 100));
            }
        }
        System.out.println(report);
        Files.writeString(new File(root, "budget.txt").toPath(), report);

        for (Workload workload : workloads) {
            assertTrue(workload.compiled > 0, "mode all compiles bodies of " + workload.name);
        }
        if (copies > 0) {
            Workload scaled = workloads.get(workloads.size() - 1);
            int compiledPerCopy = workloads.stream().filter(workload -> workload != scaled).mapToInt(workload -> workload.compiled).sum();
            assertEquals(compiledPerCopy * copies, scaled.compiled, "every copy of the larger application compiles as its original");
        }
        if (asserting && !over.isEmpty()) {
            fail(String.format(Locale.ROOT, "mode all exceeds the compile-time budget of %+.0f%% over mode off on %s%n%s",
                (limit - 1) * 100, over, report));
        }
    }

    /**
     * The larger application: the packages of every application of the corpus, each copied the
     * given number of times under a numbered name, compiled together with the union of their class
     * paths. Their imports within a package are relative, so a copy is the application again.
     */
    private static Workload scaled(File source, int copies) throws IOException {
        Files.createDirectories(source.toPath());
        Set<File> classpath = new LinkedHashSet<>();
        Set<File> processorPath = new LinkedHashSet<>();
        for (String corpus : CORPORA) {
            classpath.addAll(classpath(corpus, "classpath"));
            processorPath.addAll(classpath(corpus, "processorPath"));
            Path corpusRoot = Path.of("src/corpus", corpus);
            try (Stream<Path> packages = Files.list(corpusRoot)) {
                for (Path pkg : packages.filter(Files::isDirectory).toList()) {
                    for (int copy = 0; copy < copies; copy++) {
                        copyRecursively(pkg, source.toPath().resolve(pkg.getFileName() + "_" + copy));
                    }
                }
            }
        }
        Files.copy(Path.of("src/corpus/fullstack/main.py"), source.toPath().resolve("main.py"));
        return new Workload("scaled x" + copies, source, List.copyOf(classpath), List.copyOf(processorPath));
    }

    private static List<File> classpath(String corpus, String kind) {
        String path = System.getProperty("corpus." + corpus + "." + kind);
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(File.pathSeparator)).map(File::new).toList();
    }

    private static double loadAverage() {
        return java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        return size % 2 == 1 ? sorted.get(size / 2) : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }

    private static void copyRecursively(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : paths.toList()) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
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

    private record Summary(double median, double min, double max) {
    }

    /**
     * One compilation measured: its wall time and the time of each stage, in milliseconds.
     */
    private record Sample(StaticCompilationMode mode, double millis, Map<String, Double> stages) {
    }

    private static final class Workload {
        private final String name;
        private final File source;
        private final List<File> classpath;
        private final List<File> processorPath;
        private final List<Sample> samples = new ArrayList<>();
        private int decisions;
        private int compiled;

        private Workload(String name, File source, List<File> classpath, List<File> processorPath) {
            this.name = name;
            this.source = source;
            this.classpath = classpath;
            this.processorPath = processorPath;
        }

        void compile(File root, StaticCompilationMode mode) throws IOException {
            File out = new File(root, name.replace(' ', '-') + "-" + mode.optionValue());
            deleteRecursively(out.toPath());
            File targetDir = new File(out, "classes");
            targetDir.mkdirs();
            List<String> diagnostics = new ArrayList<>();
            List<StaticCompilationDecision> made = new ArrayList<>();
            AtomicReference<CompilationProfile> profile = new AtomicReference<>();
            PyronautCompiler compiler = PyronautCompiler.builder()
                .pythonSrc(source.getPath())
                .targetDir(targetDir)
                .classpath(classpath)
                .annotationProcessorPath(processorPath)
                .typeCheck(TypeCheckMode.ERROR)
                .staticCompilation(mode)
                .staticCompilationReport(new File(out, "report"))
                .pythonDiagnosticCallback(diagnostic -> diagnostics.add(diagnostic.toString()))
                .staticCompilationDecisionCallback(made::add)
                .profileCallback(profile::set)
                .build();
            // a collection before each compilation, so neither mode pays for the garbage of the other
            System.gc();
            Map<String, Long> before = PipelineTimings.nanos();
            long started = System.nanoTime();
            compiler.compile();
            double millis = (System.nanoTime() - started) / 1_000_000.0;
            Map<String, Long> after = PipelineTimings.nanos();
            assertEquals(List.of(), diagnostics, "the type checker reports nothing on " + name);

            Map<String, Double> stages = new LinkedHashMap<>();
            if (PipelineTimings.enabled()) {
                for (String stage : STAGES) {
                    stages.put(stage, (after.getOrDefault(stage, 0L) - before.getOrDefault(stage, 0L)) / 1_000_000.0);
                }
            }
            for (String phase : PHASES) {
                CompilationProfile.Phase recorded = profile.get().phase(phase);
                stages.put(phase, recorded == null ? 0.0 : recorded.millis());
            }
            samples.add(new Sample(mode, millis, stages));
            if (mode == StaticCompilationMode.ALL) {
                decisions = made.size();
                compiled = (int) made.stream().filter(decision -> decision.outcome() == StaticCompilationDecision.Outcome.COMPILED).count();
            }
        }

        Summary summary(StaticCompilationMode mode) {
            List<Double> times = samples.stream().filter(sample -> sample.mode == mode).map(Sample::millis).toList();
            return new Summary(median(times), times.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                times.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        }

        List<String> stageNames() {
            return samples.isEmpty() ? List.of() : List.copyOf(samples.get(0).stages.keySet());
        }

        double stageMedian(StaticCompilationMode mode, String stage) {
            return median(samples.stream().filter(sample -> sample.mode == mode).map(sample -> sample.stages.get(stage)).toList());
        }
    }
}
