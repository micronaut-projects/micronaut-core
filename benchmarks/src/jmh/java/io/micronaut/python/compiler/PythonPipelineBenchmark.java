/*
 * Copyright 2026 original authors
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

import io.micronaut.python.processing.PipelineTimings;
import io.micronaut.python.processing.PythonProcessingSession;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.typecheck.TypeCheckMode;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Measures what type checking and static compilation add to the compilation of a Python
 * application: the whole in-memory compilation ({@code buildClassLoader}) in every mode, cold (a
 * GraalPy context per compilation, as a build runs) and warm (a processing session reusing the
 * context, as a watch or a daemon runs), with the time of each stage of the pipeline as auxiliary
 * counters in milliseconds. JMH reports an events counter as its sum over the measured iterations
 * of a fork, so a stage's per-compilation time is its score divided by the iteration count.
 *
 * <p>Run with:
 * {@code ./gradlew :benchmarks:jmh -Pjmh.includes=io.micronaut.python.compiler.PythonPipelineBenchmark -Pjmh.warmupIterations=3 -Pjmh.iterations=10}.
 * Narrow the matrix with {@code -Pjmh.benchmarkParameters='mode=STATIC;warm=false;fixture=shop'}.
 * </p>
 *
 * @since 5.3.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = "-D" + PipelineTimings.PROPERTY + "=true")
public class PythonPipelineBenchmark {

    /**
     * The pipeline mode: nothing, the type checker, or the type checker and the static compilation.
     */
    public enum PipelineMode {
        OFF(TypeCheckMode.OFF, StaticCompilationMode.OFF),
        TYPECHECK(TypeCheckMode.ERROR, StaticCompilationMode.OFF),
        STATIC(TypeCheckMode.ERROR, StaticCompilationMode.ALL);

        final TypeCheckMode typeCheck;
        final StaticCompilationMode staticCompilation;

        PipelineMode(TypeCheckMode typeCheck, StaticCompilationMode staticCompilation) {
            this.typeCheck = typeCheck;
            this.staticCompilation = staticCompilation;
        }
    }

    private static final Map<String, List<String>> FIXTURES = Map.of(
        "petclinic", List.of("configuration.py", "forms.py", "models.py", "repositories.py", "services.py", "controllers.py"),
        "shop", List.of("models.py", "repository.py", "services.py", "controllers.py", "routes.py")
    );

    @Param({"OFF", "TYPECHECK", "STATIC"})
    public PipelineMode mode;

    @Param({"false", "true"})
    public boolean warm;

    @Param({"petclinic", "shop"})
    public String fixture;

    private Path sourceDirectory;
    private PythonProcessingSession session;

    /**
     * The stage times of the last compilation, in milliseconds.
     */
    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class Stages {
        public double contextMs;
        public double transformMs;
        public double parseMs;
        public double typecheckMs;
        public double planMs;
        public double bytecodeMs;
        public double stubsMs;
        public double processorMs;

        private Map<String, Long> before = Map.of();

        void start() {
            before = PipelineTimings.nanos();
        }

        void read() {
            Map<String, Long> nanos = PipelineTimings.nanos();
            contextMs = ms(nanos, PipelineTimings.CONTEXT);
            transformMs = ms(nanos, PipelineTimings.TRANSFORM);
            parseMs = ms(nanos, PipelineTimings.PARSE);
            typecheckMs = ms(nanos, PipelineTimings.TYPE_CHECK);
            planMs = ms(nanos, PipelineTimings.PLAN);
            bytecodeMs = ms(nanos, PipelineTimings.BYTECODE);
            stubsMs = ms(nanos, PipelineTimings.STUBS);
            processorMs = ms(nanos, PipelineTimings.PROCESSOR);
        }

        private double ms(Map<String, Long> nanos, String stage) {
            return (nanos.getOrDefault(stage, 0L) - before.getOrDefault(stage, 0L)) / 1_000_000.0;
        }
    }

    @Setup(Level.Trial)
    public void copyFixture() throws IOException {
        sourceDirectory = Files.createTempDirectory("pyronaut-pipeline-benchmark-");
        ClassLoader classLoader = PythonPipelineBenchmark.class.getClassLoader();
        for (String fixtureFile : FIXTURES.get(fixture)) {
            Path target = sourceDirectory.resolve(fixtureFile);
            try (InputStream input = classLoader.getResourceAsStream("io/micronaut/python/compiler/" + fixture + "/" + fixtureFile)) {
                if (input == null) {
                    throw new IOException("Missing benchmark fixture resource: " + fixtureFile);
                }
                Files.copy(input, target);
            }
        }
        if (warm) {
            session = new PythonProcessingSession();
        }
    }

    @TearDown(Level.Trial)
    public void deleteFixture() throws IOException {
        if (session != null) {
            session.close();
        }
        if (sourceDirectory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Benchmark
    public void compile(Blackhole blackhole, Stages stages) {
        stages.start();
        PyronautCompiler.Builder builder = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .typeCheck(mode.typeCheck)
            .staticCompilation(mode.staticCompilation);
        if (session != null) {
            builder.pythonProcessingSession(session);
        }
        ClassLoader classLoader = builder.build().buildClassLoader();
        blackhole.consume(classLoader);
        stages.read();
    }
}
