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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * The time the stages of the Python pipeline take, accumulated per stage when the system property
 * {@value #PROPERTY} is set: the benchmarks read them to attribute the cost of a compilation to
 * the GraalPy context, the transform, the model, the type checker, the planner, the bytecode and
 * the generated classes. Off by default, when the stages cost one static boolean read each.
 *
 * @since 5.3.0
 */
@Internal
public final class PipelineTimings {
    /**
     * The system property enabling the timings.
     */
    public static final String PROPERTY = "micronaut.python.timings";

    /**
     * Creating and initializing the GraalPy context of a parser.
     */
    public static final String CONTEXT = "context";
    /**
     * Transforming the sources: the Python transform of each module.
     */
    public static final String TRANSFORM = "transform";
    /**
     * Building the model of the transformed sources: the Python visitor of each module.
     */
    public static final String PARSE = "parse";
    /**
     * The type checker.
     */
    public static final String TYPE_CHECK = "typecheck";
    /**
     * The static compilation planner.
     */
    public static final String PLAN = "plan";
    /**
     * Rewriting the runtime trees for delegation and compiling the Python bytecode.
     */
    public static final String BYTECODE = "bytecode";
    /**
     * Generating the Java classes of the Python classes and scripts.
     */
    public static final String STUBS = "stubs";
    /**
     * The whole Python annotation processor, per application.
     */
    public static final String PROCESSOR = "processor";

    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final Map<String, LongAdder> NANOS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();

    private PipelineTimings() {
    }

    /**
     * @return Whether the timings are recorded
     */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * @return The start of a stage, to give to {@link #record(String, long)}; zero when off
     */
    public static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    /**
     * Records a stage run since its start.
     *
     * @param stage The stage
     * @param start The start, from {@link #start()}
     */
    public static void record(String stage, long start) {
        if (ENABLED) {
            NANOS.computeIfAbsent(stage, ignored -> new LongAdder()).add(System.nanoTime() - start);
            COUNTS.computeIfAbsent(stage, ignored -> new LongAdder()).increment();
        }
    }

    /**
     * @return The nanoseconds accumulated per stage since the last {@link #reset()}
     */
    public static Map<String, Long> nanos() {
        Map<String, Long> snapshot = new TreeMap<>();
        NANOS.forEach((stage, adder) -> snapshot.put(stage, adder.sum()));
        return snapshot;
    }

    /**
     * @return The number of runs recorded per stage since the last {@link #reset()}
     */
    public static Map<String, Long> counts() {
        Map<String, Long> snapshot = new TreeMap<>();
        COUNTS.forEach((stage, adder) -> snapshot.put(stage, adder.sum()));
        return snapshot;
    }

    /**
     * Forgets the accumulated timings.
     */
    public static void reset() {
        NANOS.clear();
        COUNTS.clear();
    }
}
