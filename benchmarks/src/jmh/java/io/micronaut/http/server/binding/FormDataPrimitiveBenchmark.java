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
package io.micronaut.http.server.binding;

import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reading a form field as a primitive with {@link FormData#getInt(String)} and the other primitive
 * accessors, converting by boxing ({@code boxed}, with a subclass of the conversion service, which does
 * not share primitive converters) against converting with the primitive converters of the default
 * conversion service ({@code primitive}). A value of 42 is a cached {@link Integer}, 12345 is not.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class FormDataPrimitiveBenchmark {

    @Param({"42", "12345"})
    public String value;

    private FormData boxed;
    private FormData primitive;

    @Setup
    public void setup() {
        Map<String, List<String>> fields = Map.of("age", List.of(value));
        Map<String, List<FileUpload>> files = Map.of();
        boxed = new DefaultFormData(fields, files, new DefaultMutableConversionService() { });
        primitive = new DefaultFormData(fields, files, new DefaultMutableConversionService());
        if (boxed.getInt("age") != primitive.getInt("age") || boxed.getLong("age") != primitive.getLong("age")) {
            throw new IllegalStateException("The forms convert differently");
        }
    }

    @Benchmark
    public int getIntBoxed() {
        return boxed.getInt("age");
    }

    @Benchmark
    public int getIntPrimitive() {
        return primitive.getInt("age");
    }

    @Benchmark
    public Object findIntBoxed() {
        return boxed.findInt("age");
    }

    @Benchmark
    public Object findIntPrimitive() {
        return primitive.findInt("age");
    }

    @Benchmark
    public long getLongBoxed() {
        return boxed.getLong("age");
    }

    @Benchmark
    public long getLongPrimitive() {
        return primitive.getLong("age");
    }
}
