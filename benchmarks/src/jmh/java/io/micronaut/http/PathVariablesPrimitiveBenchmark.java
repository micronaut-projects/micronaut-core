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
package io.micronaut.http;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.web.router.RoutePathVariables;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reading a path variable as a primitive with {@link PathVariables#getInt(String)} and the other
 * primitive accessors, converting by boxing ({@code boxed}) against converting with the primitive
 * converters of the conversion service ({@code primitive}). A value of 42 is a cached {@link Integer},
 * 12345 is not.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class PathVariablesPrimitiveBenchmark {

    @Param({"42", "12345"})
    public String value;

    private PathVariables boxed;
    private PathVariables primitive;

    @Setup
    public void setup() {
        Map<String, Object> values = Map.of("id", value, "bad", "abc");
        // a subclass of the conversion service converts by boxing: it does not share primitive converters
        ConversionService boxingService = new DefaultMutableConversionService() { };
        boxed = new RoutePathVariables(values, values, boxingService);
        primitive = new RoutePathVariables(values, values, new DefaultMutableConversionService());
        if (boxed.getInt("id") != primitive.getInt("id") || boxed.getLong("id") != primitive.getLong("id")) {
            throw new IllegalStateException("The path variables convert differently");
        }
    }

    @Benchmark
    public int getIntBoxed() {
        return boxed.getInt("id");
    }

    @Benchmark
    public int getIntPrimitive() {
        return primitive.getInt("id");
    }

    @Benchmark
    public Object findIntBoxed() {
        return boxed.findInt("id");
    }

    @Benchmark
    public Object findIntPrimitive() {
        return primitive.findInt("id");
    }

    @Benchmark
    public long getLongBoxed() {
        return boxed.getLong("id");
    }

    @Benchmark
    public long getLongPrimitive() {
        return primitive.getLong("id");
    }

    @Benchmark
    public Object getIntInvalidBoxed() {
        try {
            return boxed.getInt("bad");
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Benchmark
    public Object getIntInvalidPrimitive() {
        try {
            return primitive.getInt("bad");
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Benchmark
    public double getDoubleBoxed() {
        return boxed.getDouble("id");
    }

    @Benchmark
    public double getDoublePrimitive() {
        return primitive.getDouble("id");
    }
}
