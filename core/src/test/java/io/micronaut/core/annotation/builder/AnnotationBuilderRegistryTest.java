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
package io.micronaut.core.annotation.builder;

import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.annotation.AnnotationBuilderRegistry;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.convert.ConversionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationBuilderRegistryTest {

    private final AnnotationBuilderRegistry registry = AnnotationBuilderRegistry.shared();

    @Test
    void findsTheBuilderOnceByTypeAndByName() {
        AnnotationBuilder<TestFlag> builder = registry.find(TestFlag.class).orElseThrow();
        assertSame(builder, registry.find(TestFlag.class).orElseThrow());
        assertSame(builder, registry.find(TestFlag.class.getName()).orElseThrow());
        assertEquals(TestFlag.class, builder.annotationType());
        assertTrue(registry.contains(TestFlag.class));
        assertFalse(registry.contains(Deprecated.class));
        assertTrue(registry.find("not.An.Annotation").isEmpty());
    }

    @Test
    void theStaticMethodsOfTheBuilderFindTheBuilders() {
        AnnotationBuilder<TestFlag> builder = AnnotationBuilder.find(TestFlag.class).orElseThrow();
        assertSame(builder, AnnotationBuilder.find(TestFlag.class.getName()).orElseThrow());
        ClassLoader classLoader = getClass().getClassLoader();
        assertEquals(TestFlag.class, AnnotationBuilder.find(TestFlag.class, classLoader).orElseThrow().annotationType());
        assertEquals(TestFlag.class, AnnotationBuilder.find(TestFlag.class.getName(), classLoader).orElseThrow().annotationType());
        assertTrue(AnnotationBuilder.find(Deprecated.class).isEmpty());
        assertTrue(AnnotationBuilder.find("not.An.Annotation").isEmpty());
        assertEquals("v", builder.build(Map.of("value", "v")).value());
    }

    @Test
    void aRegistryOfALoaderFindsTheBuilders() {
        AnnotationBuilderRegistry own = AnnotationBuilderRegistry.of(getClass().getClassLoader());
        assertTrue(own.find(TestFlag.class).isPresent());
    }

    @Test
    void buildsFromMembersWithTheDefaults() {
        TestFlag flag = registry.build(TestFlag.class, Map.of("value", "v"));
        assertEquals("v", flag.value());
        assertEquals(1, flag.level());

        TestFlag converted = registry.build(TestFlag.class, Map.of("value", "v", "level", "7"), ConversionService.SHARED);
        assertEquals(7, converted.level());
        assertEquals(flag, registry.build(TestFlag.class, Map.of("value", "v", "level", 1)));
        assertEquals(flag.hashCode(), registry.build(TestFlag.class, Map.of("value", "v", "level", 1)).hashCode());
    }

    @Test
    void buildsFromAnAnnotationValue() {
        AnnotationValue<TestFlag> value = AnnotationValue.builder(TestFlag.class).member("value", "av").member("level", 3).build();
        TestFlag flag = registry.build(TestFlag.class, value);
        assertEquals("av", flag.value());
        assertEquals(3, flag.level());
        assertEquals(flag, registry.build(value, ConversionService.SHARED));
        assertEquals(flag, registry.build(TestFlag.class.getName(), Map.of("value", "av", "level", 3), ConversionService.SHARED));
        AnnotationValue<TestFlag> provided = assertInstanceOf(AnnotationValueProvider.class, flag).annotationValue();
        assertEquals("av", provided.stringValue("value").orElseThrow());
    }

    @Test
    void failsWhenThereIsNoBuilderOrTheValueIsWrong() {
        assertThrows(IllegalArgumentException.class, () -> registry.build(Deprecated.class, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.build("not.An.Annotation", Map.of(), ConversionService.SHARED));
        Map<String, Object> wrong = Map.of("value", "v", "level", "not a number");
        assertThrows(IllegalArgumentException.class, () -> registry.build(TestFlag.class, wrong));
    }

    @Test
    void theNameOfAnAnnotationIsFlattened() {
        assertEquals("io_micronaut_Outer_Inner", AnnotationBuilderRegistry.mangle("io.micronaut.Outer$Inner"));
    }
}
