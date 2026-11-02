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

import java.util.concurrent.TimeUnit;

/**
 * Calls of intercepted methods whose interceptors derive per-method data from the annotation metadata.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class InterceptedCallBenchmark {

    private ApplicationContext applicationContext;
    private InterceptedBean bean;
    private int value;

    @Setup
    public void prepare() {
        applicationContext = ApplicationContext.run();
        bean = applicationContext.getBean(InterceptedBean.class);
    }

    @TearDown
    public void cleanup() {
        applicationContext.close();
    }

    /**
     * A retryable call that succeeds at once: only the retry setup is measured.
     */
    @Benchmark
    public int retryable() {
        return bean.retryable(value++);
    }

    @Benchmark
    public int retryableConfigured() {
        return bean.retryableConfigured(value++);
    }
}
