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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Measures the in-memory Python application compilation path used by Pyronaut.
 *
 * <p>Run a quick smoke benchmark with:
 * {@code ./gradlew :benchmarks:jmh -Pjmh.includes=io.micronaut.python.compiler.PyronautCompilerBenchmark -Pjmh.warmupIterations=1 -Pjmh.iterations=1}.
 * To profile the JMH fork with Async Profiler, add
 * {@code -Pjmh.profilers='async:libPath=/Users/graemerocher/dev/oss/async-profiler-4.5-macos/lib/libasyncProfiler.dylib;output=flamegraph;dir=build/reports/jmh'}.
 * </p>
 *
 * @since 5.2.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PyronautCompilerBenchmark {

    private static final String FIXTURE_ROOT = "io/micronaut/python/compiler/petclinic";
    private static final List<String> FIXTURE_FILES = List.of(
        "configuration.py",
        "forms.py",
        "models.py",
        "repositories.py",
        "services.py",
        "controllers.py"
    );

    private Path sourceDirectory;

    @Setup(Level.Trial)
    public void copyFixture() throws IOException {
        sourceDirectory = Files.createTempDirectory("pyronaut-petclinic-benchmark-");
        ClassLoader classLoader = PyronautCompilerBenchmark.class.getClassLoader();
        for (String fixtureFile : FIXTURE_FILES) {
            Path target = sourceDirectory.resolve(fixtureFile);
            try (InputStream input = classLoader.getResourceAsStream(FIXTURE_ROOT + "/" + fixtureFile)) {
                if (input == null) {
                    throw new IOException("Missing benchmark fixture resource: " + fixtureFile);
                }
                Files.copy(input, target);
            }
        }
    }

    @TearDown(Level.Trial)
    public void deleteFixture() throws IOException {
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
    public void compilePetClinicFixture(Blackhole blackhole) {
        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .build()
            .buildClassLoader();
        blackhole.consume(classLoader);
    }
}
