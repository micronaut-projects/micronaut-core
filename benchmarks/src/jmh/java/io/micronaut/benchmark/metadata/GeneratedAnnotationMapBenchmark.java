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
package io.micronaut.benchmark.metadata;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Two-member reads including annotation lookup from real introspection metadata. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class GeneratedAnnotationMapBenchmark {
    private static final String ANNOTATION = LookupRule.class.getName();
    private AnnotationMetadata[] generated;
    private AnnotationMetadata[] ordinary;
    private int cursor;

    @Setup
    public void setup() {
        generated = BeanIntrospection.getIntrospection(LookupFixture.class).getBeanProperties().stream()
            .map(property -> property.getAnnotationMetadata()).toArray(AnnotationMetadata[]::new);
        if (generated.length != 16) {
            throw new IllegalStateException("Expected 16 distinct property metadata instances");
        }
        ordinary = new AnnotationMetadata[generated.length];
        for (int i = 0; i < generated.length; i++) {
            Map<CharSequence, Object> members = generated[i].getValues(ANNOTATION);
            if (!(members instanceof LookupRule$AnnotationMap)) {
                throw new IllegalStateException("The compiler did not install typed annotation storage");
            }
            AnnotationMetadata metadata = generated[i];
            Map<String, List<String>> byStereotype = new LinkedHashMap<>();
            for (String stereotype : metadata.getStereotypeAnnotationNames()) {
                byStereotype.put(stereotype, metadata.getAnnotationNamesByStereotype(stereotype));
            }
            // Preserve all annotations (including nullability) and the same immutable map shapes.
            // Dropping unrelated annotations changes the outer lookup cost and biases the baseline.
            ordinary[i] = new DefaultAnnotationMetadata(
                copyValues(metadata.getDeclaredMetadata(), metadata.getDeclaredAnnotationNames()),
                copyValues(metadata.getDeclaredMetadata(), metadata.getDeclaredStereotypeAnnotationNames()),
                copyValues(metadata, metadata.getStereotypeAnnotationNames()),
                copyValues(metadata, metadata.getAnnotationNames()),
                Map.copyOf(byStereotype), false, false);
        }
    }

    private static Map<String, Map<CharSequence, Object>> copyValues(AnnotationMetadata metadata, Set<String> names) {
        Map<String, Map<CharSequence, Object>> result = new LinkedHashMap<>();
        for (String name : names.stream().sorted().toList()) {
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            metadata.getValues(name).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
            result.put(name, Map.copyOf(values));
        }
        return Map.copyOf(result);
    }

    @Benchmark
    public int ordinaryMetadata() {
        AnnotationMetadata metadata = ordinary[cursor++ & 15];
        return metadata.stringValue(ANNOTATION, "name").orElseThrow().length()
            + metadata.intValue(ANNOTATION, "count").orElseThrow();
    }

    @Benchmark
    public int generatedMetadataLegacyApi() {
        AnnotationMetadata metadata = generated[cursor++ & 15];
        return metadata.stringValue(ANNOTATION, "name").orElseThrow().length()
            + metadata.intValue(ANNOTATION, "count").orElseThrow();
    }

    @Benchmark
    public int generatedMetadataTypedApi() {
        LookupRule$AnnotationMap members = LookupRule$AnnotationMap.of(generated[cursor++ & 15].getValues(ANNOTATION));
        return members.name().length() + members.count();
    }

    @Benchmark
    public int ordinaryMetadataAdapter() {
        LookupRule$AnnotationMap members = LookupRule$AnnotationMap.of(ordinary[cursor++ & 15].getValues(ANNOTATION));
        return members.name().length() + members.count();
    }
}
