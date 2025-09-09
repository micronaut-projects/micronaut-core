/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.server.binding.TestController;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
public class StartupBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(StartupBenchmark.class.getName() + ".*")
            .warmupIterations(0)
            .measurementIterations(1)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
//            .addProfiler(AsyncProfiler.class, "libPath=/Users/denisstepanov/Downloads/async-profiler-4.1-macos/lib/libasyncProfiler.dylib;output=flamegraph")
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void startup(Blackhole blackhole) {
        ApplicationContext context = ApplicationContext.run();
        final TestController controller = context.getBean(TestController.class);
        blackhole.consume(controller);
    }

    @Benchmark
    public void limited(Blackhole blackhole) {
        ApplicationContext context = ApplicationContext.builder()
            .enableDefaultPropertySources(false)
            .bootstrapEnvironment(false)
            .deduceEnvironment(false)
            .deducePackage(false)
            .start();
        blackhole.consume(context.getBean(TestController.class));
    }
}
