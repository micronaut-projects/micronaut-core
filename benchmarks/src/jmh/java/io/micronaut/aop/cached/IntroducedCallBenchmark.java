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
package io.micronaut.aop.cached;

import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Calls of introduced methods whose advice derives per-method data from the annotation metadata: configuration
 * interface getters and declarative client calls answered by a client filter.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class IntroducedCallBenchmark {

    private ApplicationContext applicationContext;
    private BenchConfig config;
    private BenchClient client;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run(Map.of(
            "bench.config.count", 5,
            "micronaut.http.client.versioning.default.headers", "X-API-VERSION"
        ));
        config = applicationContext.getBean(BenchConfig.class);
        client = applicationContext.getBean(BenchClient.class);
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    @Benchmark
    public int configProperty() {
        return config.getCount();
    }

    @Benchmark
    public String configPropertyWithDefault() {
        return config.getName();
    }

    @Benchmark
    public String clientCall() {
        return client.item("42");
    }

    @Benchmark
    public String clientCallWithHeader() {
        return client.headers();
    }

    @Benchmark
    public String clientCallVersioned() {
        return client.versioned();
    }

    @Benchmark
    public String clientCallWithArguments() {
        return client.search("micronaut", 2, 20, "t");
    }
}
