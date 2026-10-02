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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.annotation.AnnotationBuilderRegistry;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.builders.Color;
import io.micronaut.inject.annotation.builders.Sample;
import io.micronaut.inject.annotation.builders.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationBuilderTest {

    private final AnnotationBuilderRegistry registry = AnnotationBuilderRegistry.shared();

    @Test
    void theRegistryFindsTheBuilderOnceAndByName() {
        AnnotationBuilder<Sample> builder = registry.find(Sample.class).orElseThrow();
        assertSame(builder, registry.find(Sample.class).orElseThrow());
        assertSame(builder, registry.find(Sample.class.getName()).orElseThrow());
        assertEquals(Sample.class, builder.annotationType());
        assertTrue(registry.contains(Tag.class));
        assertFalse(registry.contains(Deprecated.class));
        assertThrows(IllegalArgumentException.class, () -> registry.build(Deprecated.class, Map.of()));
    }

    @Test
    void membersTakeTheirDefaults() {
        Sample sample = registry.build(Sample.class, Map.of("name", "n"));
        assertEquals("n", sample.name());
        assertEquals("label", sample.label());
        assertEquals(3, sample.count());
        assertEquals(4L, sample.big());
        assertTrue(sample.flag());
        assertEquals(0.5, sample.ratio());
        assertEquals((byte) 1, sample.small());
        assertEquals('x', sample.letter());
        assertEquals((short) 2, sample.tiny());
        assertEquals(1.5f, sample.fraction());
        assertEquals(Object.class, sample.type());
        assertArrayEquals(new Class<?>[]{String.class, Integer.class}, sample.types());
        assertEquals(Color.GREEN, sample.color());
        assertArrayEquals(new Color[]{Color.RED, Color.BLUE}, sample.colors());
        assertArrayEquals(new String[]{"a", "b"}, sample.names());
        assertArrayEquals(new int[]{1, 2, 3}, sample.numbers());
        assertEquals("default", sample.tag().value());
        assertEquals(1, sample.tag().weight());
        assertEquals(2, sample.tags().length);
        assertEquals("two", sample.tags()[1].value());
        assertEquals(2, sample.tags()[1].weight());
        assertEquals(Sample.class, sample.annotationType());
    }

    @Test
    void valuesAreConvertedToTheTypeOfTheMember() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "n");
        values.put("count", "7");
        values.put("big", 9);
        values.put("flag", "false");
        values.put("type", "java.lang.Long");
        values.put("types", new AnnotationClassValue<?>[]{new AnnotationClassValue<>(Integer.class), new AnnotationClassValue<>("java.lang.Long")});
        values.put("color", "BLUE");
        values.put("colors", new String[]{"GREEN"});
        values.put("numbers", List.of("4", 5));
        values.put("names", "x,y,z");
        // a nested annotation as a map, as an annotation value and as an instance, an array as a mix
        values.put("tag", Map.of("value", "nested", "weight", "5"));
        values.put("tags", new Object[]{
            AnnotationValue.builder(Tag.class).member("value", "av").build(),
            Map.of("value", "map"),
            registry.build(Tag.class, Map.of("value", "instance"))
        });
        Sample sample = registry.build(Sample.class, values);
        assertEquals(7, sample.count());
        assertEquals(9L, sample.big());
        assertFalse(sample.flag());
        assertEquals(Long.class, sample.type());
        assertArrayEquals(new Class<?>[]{Integer.class, Long.class}, sample.types());
        assertEquals(Color.BLUE, sample.color());
        assertArrayEquals(new Color[]{Color.GREEN}, sample.colors());
        assertArrayEquals(new String[]{"x", "y", "z"}, sample.names());
        assertArrayEquals(new int[]{4, 5}, sample.numbers());
        assertEquals("nested", sample.tag().value());
        assertEquals(5, sample.tag().weight());
        assertEquals(List.of("av", "map", "instance"), java.util.Arrays.stream(sample.tags()).map(Tag::value).toList());
        assertEquals(1, sample.tags()[1].weight());
    }

    @Test
    void anAnnotationValueIsBuilt() {
        AnnotationValue<Sample> value = AnnotationValue.builder(Sample.class)
            .member("name", "n")
            .member("count", 11)
            .member("color", Color.RED)
            .member("tag", AnnotationValue.builder(Tag.class).member("value", "t").build())
            .build();
        Sample sample = registry.build(Sample.class, value);
        assertEquals(11, sample.count());
        assertEquals(Color.RED, sample.color());
        assertEquals("t", sample.tag().value());
        Sample synthesized = AnnotationMetadataSupport.buildAnnotation(Sample.class, value);
        assertEquals(sample, synthesized);
        assertSame(sample.getClass(), synthesized.getClass());
    }

    @Test
    void arraysAreCopied() {
        Sample sample = registry.build(Sample.class, Map.of("name", "n"));
        sample.names()[0] = "changed";
        sample.numbers()[0] = 100;
        sample.tags()[0] = null;
        assertEquals("a", sample.names()[0]);
        assertEquals(1, sample.numbers()[0]);
        assertEquals("one", sample.tags()[0].value());
    }

    @Test
    void equalsAndHashCodeAgreeWithTheProxy() {
        Sample built = registry.build(Sample.class, Map.of("name", "n", "count", 5));

        // the proxy of the same members, omitting the defaults, and one that writes some of them out
        AnnotationValue<Sample> omitting = AnnotationValue.builder(Sample.class).member("name", "n").member("count", 5).build();
        AnnotationValue<Sample> writing = AnnotationValue.builder(Sample.class)
            .member("name", "n").member("count", 5).member("label", "label").member("color", Color.GREEN).build();
        Sample proxy = AnnotationMetadataSupport.buildProxyAnnotation(Sample.class, omitting);
        Sample proxyWriting = AnnotationMetadataSupport.buildProxyAnnotation(Sample.class, writing);
        assertNotSame(proxy.getClass(), built.getClass());

        assertEquals(proxy, built);
        assertEquals(built, proxy);
        assertEquals(built, proxyWriting);
        assertEquals(proxyWriting, built);
        assertEquals(proxy.hashCode(), built.hashCode());
        assertEquals(proxyWriting.hashCode(), built.hashCode());
        assertEquals(1, new HashSet<Annotation>(List.of(proxy, built, proxyWriting)).size());

        Sample other = registry.build(Sample.class, Map.of("name", "n", "count", 6));
        Sample otherProxy = AnnotationMetadataSupport.buildProxyAnnotation(Sample.class,
            AnnotationValue.builder(Sample.class).member("name", "n").member("count", 6).build());
        assertNotEquals(built, other);
        assertNotEquals(other, built);
        assertNotEquals(built, otherProxy);
        assertNotEquals(otherProxy, built);
        assertEquals(other, otherProxy);
        assertEquals(otherProxy, other);
        assertEquals(other.hashCode(), otherProxy.hashCode());
    }

    @Test
    void equalsAnAnnotationOfTheJvm() {
        Sample jvm = Annotated.class.getAnnotation(Sample.class);
        Sample built = registry.build(Sample.class, Map.of(
            "name", "jvm",
            "count", 9,
            "type", Long.class,
            "tag", Map.of("value", "jvm-tag")));
        assertEquals(jvm, built);
        assertEquals(built, jvm);
    }

    @Test
    void toStringAndAnnotationValue() {
        Sample built = registry.build(Sample.class, Map.of("name", "n"));
        assertTrue(built.toString().contains("n"));
        AnnotationValue<Sample> value = ((io.micronaut.core.annotation.AnnotationValueProvider<Sample>) built).annotationValue();
        assertEquals(Sample.class.getName(), value.getAnnotationName());
        assertEquals("n", value.stringValue("name").orElseThrow());
        assertEquals("default", value.getAnnotation("tag", Tag.class).orElseThrow().stringValue("value").orElseThrow());
    }

    @Sample(name = "jvm", count = 9, type = Long.class, tag = @Tag("jvm-tag"))
    static class Annotated {
    }
}
