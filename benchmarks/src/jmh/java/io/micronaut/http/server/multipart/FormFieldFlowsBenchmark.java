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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

/**
 * Isolates binder plumbing for a first field and an immediately completed field. Both paths
 * produce a future and cancel the same source after one value. This does not measure form
 * decoding, disk writes, delayed completion, subscriber context or end-to-end HTTP latency.
 */
@Internal
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class FormFieldFlowsBenchmark {
    private final Flux<Integer> source = Flux.range(1, 16);

    /** @return The first mapped field */
    @Benchmark
    public int reactorFirst() {
        return source.map(value -> value + 1).next().toFuture().join();
    }

    /** @return The first mapped field */
    @Benchmark
    public int flowFirst() {
        return FormFieldFlows.first(source, value -> value + 1).join();
    }

    /** @return The first completed field */
    @Benchmark
    public int reactorFirstCompleted() {
        return source.flatMap(value -> ReactiveExecutionFlow.toPublisher(ExecutionFlow.just(value + 1)))
            .next().toFuture().join();
    }

    /** @return The first completed field */
    @Benchmark
    public int flowFirstCompleted() {
        return FormFieldFlows.firstFlatMap(source, value -> ExecutionFlow.just(value + 1)).join();
    }

    /** @return All completed fields */
    @Benchmark
    public Object reactorConcurrentCompleted() {
        return source.flatMap(value -> ReactiveExecutionFlow.toPublisher(ExecutionFlow.just(value + 1)))
            .collectList().block();
    }

    /** @return All completed fields */
    @Benchmark
    public Object nativeConcurrentCompleted() {
        return Flux.from(new ConcurrentFormPublisher<>(source, value -> ExecutionFlow.just(value + 1),
            value -> { }, value -> { }, 256)).collectList().block();
    }
}
