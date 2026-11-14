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
package io.micronaut.context.resolution;

import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Measures the first context startup in each fresh JVM, excluding shutdown.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(10)
public class BeanContextStartupBenchmark {
    private ApplicationContext context;

    @Benchmark
    public Object startup() {
        context = ApplicationContext.run(Map.of("spec.name", Fixtures.SPEC));
        return context.getBean(Fixtures.ProviderHolder.class);
    }

    @TearDown
    public void close() {
        if (context != null) {
            context.close();
        }
    }
}
