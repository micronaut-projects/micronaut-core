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
import io.micronaut.python.processing.PythonProcessingSession;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.typecheck.TypeCheckMode;
import org.jspecify.annotations.Nullable;
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
import java.util.concurrent.TimeUnit;
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
 * compilations and the copies of the larger application (none with {@code 0}).</p>
 *
 * <p>Those warm rounds give each compilation a GraalPy context of its own in a JVM that compiled
 * before. Two more ways to compile are measured on request, and asserted alike: a build compiles an
 * application once in a JVM, which starts cold, and {@value #COLD_PROPERTY} sets a number of cold
 * rounds, which compile every workload in both modes, each compilation in a JVM of its own;
 * development mode compiles an application again and again in one processing session, which keeps
 * its context, and {@value #SESSION_PROPERTY} sets a number of session rounds, after the warm-up,
 * with a session per workload and mode. {@value #JVM_ARGUMENTS_PROPERTY} gives the arguments of
 * every JVM compiling, this one and those of the cold rounds (for instance those of the build's
 * Python compile tasks); {@code -Pcorpus.budget.rounds=0} skips the warm rounds:</p>
 *
 * <pre>./gradlew :test-suite-python-corpus:test -Ppython-ci --tests '*StaticCompilationBudgetTest' \
 *     -Pcorpus.budget.assert -Pcorpus.budget.rounds=5 -Pcorpus.budget.copies=4 \
 *     -Pcorpus.budget.cold=5 -Pcorpus.budget.session=5</pre>
 */
class StaticCompilationBudgetTest {

    /**
     * The system property switching the assertion of the budget on.
     */
    static final String ASSERT_PROPERTY = "corpus.budget.assert";

    /**
     * The system property giving the number of cold rounds: each compiles every workload in both
     * modes, each compilation in a JVM of its own, as a build compiles. None by default.
     */
    static final String COLD_PROPERTY = "corpus.budget.cold";

    /**
     * The system property giving the arguments, separated by spaces, of the JVMs of the cold rounds;
     * the build gives them to the JVM of the test too.
     */
    static final String JVM_ARGUMENTS_PROPERTY = "corpus.budget.jvm-args";

    /**
     * The system property giving the number of session rounds: each compiles every workload in both
     * modes, each mode with a processing session of its own kept across the compilations, as
     * development mode compiles. None by default.
     */
    static final String SESSION_PROPERTY = "corpus.budget.session";

    private static final double BUDGET = 1.15;
    private static final int SCALED_COPIES_DEFAULT = 1;
    private static final List<String> CORPORA = List.of("fullstack", "petclinic");
    // the stages of PipelineTimings, then the phases of the compilation profile, in pipeline order
    private static final List<String> STAGES = List.of(
        PipelineTimings.CONTEXT, PipelineTimings.TRANSFORM, PipelineTimings.PARSE, PipelineTimings.TYPE_CHECK,
        PipelineTimings.PLAN, PipelineTimings.BYTECODE, PipelineTimings.STUBS, PipelineTimings.PROCESSOR
    );
    private static final List<String> PHASES = CorpusCompilation.PHASES;

    @Test
    void modeAllAddsAtMostFifteenPercentToTheCompilation() throws IOException, InterruptedException {
        int rounds = Integer.getInteger("corpus.budget.rounds", 3);
        int warmup = Integer.getInteger("corpus.budget.warmup", 1);
        int copies = Integer.getInteger("corpus.budget.copies", SCALED_COPIES_DEFAULT);
        int cold = Integer.getInteger(COLD_PROPERTY, 0);
        int sessionRounds = Integer.getInteger(SESSION_PROPERTY, 0);
        List<String> jvmArguments = Arrays.stream(System.getProperty(JVM_ARGUMENTS_PROPERTY, "").split("\\s+"))
            .filter(argument -> !argument.isBlank()).toList();
        boolean asserting = Boolean.getBoolean(ASSERT_PROPERTY);
        double limit = Double.parseDouble(System.getProperty("corpus.budget.limit", Double.toString(BUDGET)));
        File root = new File("build/corpus-budget");
        deleteRecursively(root.toPath());

        List<Workload> workloads = new ArrayList<>();
        for (String corpus : CORPORA) {
            workloads.add(new Workload(corpus, corpus, new File("src/corpus/" + corpus), classpath(corpus, "classpath"), classpath(corpus, "processorPath")));
        }
        if (copies > 0) {
            workloads.add(scaled(new File(root, "scaled-src"), copies));
        }

        // the warm-up compiles every workload in both modes, so neither mode pays for loading the classes of the other
        for (int i = 0; i < warmup && rounds > 0; i++) {
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
            for (Workload workload : workloads) {
                for (StaticCompilationMode mode : order(round)) {
                    workload.compile(root, mode);
                }
            }
        }
        // development mode compiles an application again and again in one session, which keeps its GraalPy context
        if (sessionRounds > 0) {
            for (Workload workload : workloads) {
                Map<StaticCompilationMode, PythonProcessingSession> sessions = new java.util.EnumMap<>(StaticCompilationMode.class);
                try {
                    for (StaticCompilationMode mode : order(0)) {
                        sessions.put(mode, new PythonProcessingSession());
                    }
                    List<Sample> discarded = new ArrayList<>();
                    for (int i = 0; i < warmup; i++) {
                        for (StaticCompilationMode mode : order(0)) {
                            workload.compile(root, mode, sessions.get(mode), discarded);
                        }
                    }
                    for (int round = 0; round < sessionRounds; round++) {
                        for (StaticCompilationMode mode : order(round)) {
                            workload.compile(root, mode, sessions.get(mode), workload.sessionSamples);
                        }
                    }
                } finally {
                    sessions.values().forEach(PythonProcessingSession::close);
                }
            }
        }
        // a build compiles once per JVM: the cold rounds start a JVM for every compilation
        for (int round = 0; round < cold; round++) {
            for (Workload workload : workloads) {
                for (StaticCompilationMode mode : order(round)) {
                    workload.compileCold(root, mode, jvmArguments);
                }
            }
        }

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "Static compilation compile-time budget: mode all over mode off, type checker at error, "
                + "%s%s, %d processors, load %.2f%n",
            System.getProperty("java.vm.name") + " " + System.getProperty("java.runtime.version"),
            jvmArguments.isEmpty() ? "" : " " + String.join(" ", jvmArguments),
            Runtime.getRuntime().availableProcessors(), loadAverage()));
        List<String> over = new ArrayList<>();
        if (rounds > 0) {
            List<String> arguments = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-XX") || argument.startsWith("-Xmx") || argument.startsWith("-Dtruffle") || argument.startsWith("-Dpolyglot"))
                .toList();
            report.append(String.format(Locale.ROOT, "%nWarm: this JVM %s, %d rounds after %d warm-up%n", arguments, rounds, warmup));
            for (Workload workload : workloads) {
                over.addAll(workload.report(report, workload.samples, limit, "warm"));
            }
        }
        if (sessionRounds > 0) {
            report.append(String.format(Locale.ROOT, "%nSession: one processing session per mode, as development mode compiles, %d rounds after %d warm-up%n",
                sessionRounds, warmup));
            for (Workload workload : workloads) {
                over.addAll(workload.report(report, workload.sessionSamples, limit, "session"));
            }
        }
        if (cold > 0) {
            report.append(String.format(Locale.ROOT, "%nCold: a JVM per compilation, %d rounds%n", cold));
            for (Workload workload : workloads) {
                over.addAll(workload.report(report, workload.coldSamples, limit, "cold"));
            }
        }
        report.append(String.format(Locale.ROOT, "%nload at the end %.2f%n", loadAverage()));
        System.out.println(report);
        Files.writeString(new File(root, "budget.txt").toPath(), report);

        // every way of compiling records what mode all compiles: the last compilation measured in that mode tells
        if (rounds > 0 || sessionRounds > 0 || cold > 0) {
            for (Workload workload : workloads) {
                assertTrue(workload.compiled > 0, "mode all compiles bodies of " + workload.name);
            }
            if (copies > 0) {
                Workload scaled = workloads.get(workloads.size() - 1);
                int compiledPerCopy = workloads.stream().filter(workload -> workload != scaled).mapToInt(workload -> workload.compiled).sum();
                assertEquals(compiledPerCopy * copies, scaled.compiled, "every copy of the larger application compiles as its original");
            }
        }
        if (asserting && !over.isEmpty()) {
            fail(String.format(Locale.ROOT, "mode all exceeds the compile-time budget of %+.0f%% over mode off on %s%n%s",
                (limit - 1) * 100, over, report));
        }
    }

    private static List<StaticCompilationMode> order(int round) {
        return round % 2 == 0
            ? List.of(StaticCompilationMode.OFF, StaticCompilationMode.ALL)
            : List.of(StaticCompilationMode.ALL, StaticCompilationMode.OFF);
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
        return new Workload("scaled x" + copies, "scaled", source, List.copyOf(classpath), List.copyOf(processorPath));
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
        private final String key;
        private final File source;
        private final List<File> classpath;
        private final List<File> processorPath;
        private final List<Sample> samples = new ArrayList<>();
        private final List<Sample> coldSamples = new ArrayList<>();
        private final List<Sample> sessionSamples = new ArrayList<>();
        private int decisions;
        private int compiled;

        private Workload(String name, String key, File source, List<File> classpath, List<File> processorPath) {
            this.name = name;
            this.key = key;
            this.source = source;
            this.classpath = classpath;
            this.processorPath = processorPath;
        }

        void compile(File root, StaticCompilationMode mode) throws IOException {
            compile(root, mode, null, samples);
        }

        void compile(File root, StaticCompilationMode mode, @Nullable PythonProcessingSession session, List<Sample> into) throws IOException {
            File out = new File(root, name.replace(' ', '-') + "-" + mode.optionValue());
            deleteRecursively(out.toPath());
            File targetDir = new File(out, "classes");
            targetDir.mkdirs();
            List<String> diagnostics = new ArrayList<>();
            List<StaticCompilationDecision> made = new ArrayList<>();
            AtomicReference<CompilationProfile> profile = new AtomicReference<>();
            PyronautCompiler.Builder builder = PyronautCompiler.builder();
            if (session != null) {
                builder.pythonProcessingSession(session);
            }
            PyronautCompiler compiler = builder
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
            into.add(new Sample(mode, millis, stages));
            if (mode == StaticCompilationMode.ALL) {
                decisions = made.size();
                compiled = (int) made.stream().filter(decision -> decision.outcome() == StaticCompilationDecision.Outcome.COMPILED).count();
            }
        }

        void compileCold(File root, StaticCompilationMode mode, List<String> jvmArguments) throws IOException, InterruptedException {
            File out = new File(root, key + "-cold-" + mode.optionValue());
            deleteRecursively(out.toPath());
            Files.createDirectories(out.toPath());
            String launcher = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            List<String> command = new ArrayList<>(List.of(launcher, "-Xmx4g"));
            command.addAll(jvmArguments);
            command.addAll(List.of(
                "-D" + PipelineTimings.PROPERTY + "=true",
                "-Dcorpus." + key + ".classpath=" + joined(classpath),
                "-Dcorpus." + key + ".processorPath=" + joined(processorPath),
                "-cp", System.getProperty("java.class.path"),
                CorpusCompilation.class.getName(), "--mode=" + mode.optionValue(), key, source.getAbsolutePath(), out.getAbsolutePath()));
            File log = new File(root, key + "-cold-" + mode.optionValue() + ".log");
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start();
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                fail("the cold compilation of " + name + " did not finish, see " + log);
            }
            List<String> output = Files.readAllLines(log.toPath());
            assertEquals(0, process.exitValue(), () -> "the cold compilation of " + name + " failed:\n" + String.join("\n", output));
            String result = output.stream().filter(line -> line.startsWith(CorpusCompilation.RESULT + "\t")).findFirst()
                .orElseThrow(() -> new AssertionError("the cold compilation of " + name + " reported nothing:\n" + String.join("\n", output)));
            String[] fields = result.split("\t");
            Map<String, Double> stages = new LinkedHashMap<>();
            for (String stage : STAGES) {
                stages.put(stage, 0.0);
            }
            if (mode == StaticCompilationMode.ALL) {
                decisions = Integer.parseInt(fields[4]);
                compiled = Integer.parseInt(fields[5]);
            }
            for (int i = 6; i < fields.length; i++) {
                int separator = fields[i].indexOf('=');
                stages.put(fields[i].substring(0, separator), Double.parseDouble(fields[i].substring(separator + 1)));
            }
            coldSamples.add(new Sample(mode, Double.parseDouble(fields[3]), stages));
        }

        /**
         * Appends the comparison of the modes over the samples to the report.
         *
         * @return The workload when mode all exceeds the limit, else nothing
         */
        List<String> report(StringBuilder report, List<Sample> measured, double limit, String kind) {
            Summary off = summary(measured, StaticCompilationMode.OFF);
            Summary all = summary(measured, StaticCompilationMode.ALL);
            double ratio = all.median / off.median;
            report.append(String.format(Locale.ROOT, "%n== %s (%d functions, %d compiled in mode all)%n", name, decisions, compiled));
            report.append(String.format(Locale.ROOT, "  total      off %8.1f ms [%.1f..%.1f]   all %8.1f ms [%.1f..%.1f]   %+7.1f ms  %+6.1f%%%n",
                off.median, off.min, off.max, all.median, all.min, all.max, all.median - off.median, (ratio - 1) * 100));
            List<String> stageNames = measured.isEmpty() ? List.of() : List.copyOf(measured.get(0).stages.keySet());
            for (String stage : stageNames) {
                double offStage = stageMedian(measured, StaticCompilationMode.OFF, stage);
                double allStage = stageMedian(measured, StaticCompilationMode.ALL, stage);
                report.append(String.format(Locale.ROOT, "  %-26s off %8.1f ms   all %8.1f ms   %+7.1f ms  %+6.1f%% of off%n",
                    stage, offStage, allStage, allStage - offStage, (allStage - offStage) / off.median * 100));
            }
            return ratio > limit ? List.of(String.format(Locale.ROOT, "%s (%s): %+.1f%%", name, kind, (ratio - 1) * 100)) : List.of();
        }

        private static String joined(List<File> files) {
            return String.join(File.pathSeparator, files.stream().map(File::getAbsolutePath).toList());
        }

        private static Summary summary(List<Sample> measured, StaticCompilationMode mode) {
            List<Double> times = measured.stream().filter(sample -> sample.mode == mode).map(Sample::millis).toList();
            return new Summary(median(times), times.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                times.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        }

        private static double stageMedian(List<Sample> measured, StaticCompilationMode mode, String stage) {
            return median(measured.stream().filter(sample -> sample.mode == mode).map(sample -> sample.stages.getOrDefault(stage, 0.0)).toList());
        }
    }
}
