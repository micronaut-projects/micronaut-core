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
import io.micronaut.core.annotation.AnnotationConversionUtils;
import io.micronaut.core.annotation.AnnotationType;
import io.micronaut.core.annotation.AnnotationTypeMember;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.convert.ConversionService;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    void readsTheMembersOfAnAnnotationAsTyped() {
        AnnotationBuilder<TestFlag> builder = registry.find(TestFlag.class).orElseThrow();
        TestFlag flag = builder.build(Map.of("value", "v", "level", 4));
        Map<String, Object> members = builder.toMap(flag);
        assertEquals(Map.of("value", "v", "level", 4), members);
        assertEquals(List.of("value", "level"), List.copyOf(members.keySet()));
        assertEquals(Integer.class, members.get("level").getClass());
    }

    @Test
    void readsTheMembersOfAnAnnotationValueWithTheDefaults() {
        AnnotationBuilder<TestFlag> builder = registry.find(TestFlag.class).orElseThrow();
        AnnotationValue<TestFlag> value = AnnotationValue.builder(TestFlag.class).member("value", "av").build();
        assertEquals(Map.of("value", "av", "level", 1), builder.toMap(value, ConversionService.SHARED));
        AnnotationValue<Deprecated> other = AnnotationValue.builder(Deprecated.class).build();
        assertThrows(IllegalArgumentException.class, () -> builder.toMap(other, ConversionService.SHARED));
    }

    @Test
    void aBuilderOfAnEarlierVersionDoesNotReadAnnotations() {
        AnnotationBuilder<TestFlag> current = registry.find(TestFlag.class).orElseThrow();
        AnnotationBuilder<TestFlag> earlier = new AnnotationBuilder<>() {
            @Override
            public Class<TestFlag> annotationType() {
                return TestFlag.class;
            }

            @Override
            public TestFlag build(Map<? extends CharSequence, ?> values, ConversionService conversionService) {
                return current.build(values, conversionService);
            }
        };
        TestFlag flag = earlier.build(Map.of("value", "v"));
        assertThrows(UnsupportedOperationException.class, () -> earlier.toMap(flag));
        AnnotationValue<TestFlag> value = AnnotationValue.builder(TestFlag.class).member("value", "v").build();
        assertThrows(UnsupportedOperationException.class, () -> earlier.toMap(value, ConversionService.SHARED));
    }

    @Test
    void putMemberCopiesArraysAndLeavesOutWhatIsNotSet() {
        String[] names = {"a"};
        int[] numbers = {1};
        Map<String, Object> members = new java.util.LinkedHashMap<>();
        AnnotationConversionUtils.putMember(members, "names", names);
        AnnotationConversionUtils.putMember(members, "numbers", numbers);
        AnnotationConversionUtils.putMember(members, "missing", null);
        assertNotSame(names, members.get("names"));
        assertArrayEquals(names, (String[]) members.get("names"));
        assertNotSame(numbers, members.get("numbers"));
        assertArrayEquals(numbers, (int[]) members.get("numbers"));
        assertFalse(members.containsKey("missing"));
    }

    @Test
    void aBuilderOfAnEarlierProcessorDescribesNothing() {
        assertTrue(registry.find(TestFlag.class).orElseThrow().describe().isEmpty());
        ClassLoader classLoader = getClass().getClassLoader();
        assertTrue(AnnotationType.find(TestFlag.class, classLoader).isEmpty());
        assertTrue(AnnotationType.find(TestFlag.class.getName(), classLoader).isEmpty());
    }

    @Test
    void aDescribingBuilderIsTheAnnotationType() {
        ClassLoader classLoader = getClass().getClassLoader();
        AnnotationBuilder<TestMarker> builder = AnnotationBuilder.find(TestMarker.class, classLoader).orElseThrow();
        AnnotationType<TestMarker> type = builder.describe().orElseThrow();
        assertSame(builder, type);
        assertEquals(TestMarker.class, AnnotationType.find(TestMarker.class, classLoader).orElseThrow().getType());
        assertEquals(TestMarker.class, AnnotationType.find(TestMarker.class.getName(), classLoader).orElseThrow().getType());
        assertTrue(AnnotationType.find("not.An.Annotation", classLoader).isEmpty());

        assertEquals(TestMarker.class, type.getType());
        assertEquals(Optional.of(TestMarkers.class.getName()), type.getRepeatableContainer());
        assertEquals(RetentionPolicy.RUNTIME, type.getRetentionPolicy());
        assertEquals(Set.of(ElementType.TYPE, ElementType.METHOD), type.getTargets());
        assertTrue(type.isInherited());
        assertTrue(type.getAnnotationMetadata().isEmpty());

        List<AnnotationTypeMember<?>> members = type.getMembers();
        assertEquals(List.of("value", "level", "tags"), members.stream().map(AnnotationTypeMember::getName).toList());
        AnnotationTypeMember<?> value = members.get(0);
        assertEquals(String.class, value.getType());
        assertEquals(AnnotationTypeMember.DefaultState.ABSENT, value.getDefaultState());
        assertTrue(value.getDefaultValue().isEmpty());
        AnnotationTypeMember<?> level = type.findMember("level").orElseThrow();
        assertEquals(int.class, level.asArgument().getType());
        assertEquals(AnnotationTypeMember.DefaultState.PRESENT, level.getDefaultState());
        assertEquals(2, level.getDefaultValue().orElseThrow());
        assertTrue(type.findMember("missing").isEmpty());
    }

    @Test
    void anArrayDefaultIsCopied() {
        AnnotationType<TestMarker> type = AnnotationType.find(TestMarker.class, getClass().getClassLoader()).orElseThrow();
        AnnotationTypeMember<?> tags = type.findMember("tags").orElseThrow();
        String[] first = (String[]) tags.getDefaultValue().orElseThrow();
        first[0] = "changed";
        assertArrayEquals(new String[] {"a"}, (String[]) tags.getDefaultValue().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> type.getMembers().clear());
        assertThrows(UnsupportedOperationException.class, () -> type.getTargets().clear());
    }

    @Test
    void theDescriptionsOfALoaderAreItsOwn() throws Exception {
        URL testClasses = TestMarker.class.getProtectionDomain().getCodeSource().getLocation();
        try (ChildFirstLoader isolated = new ChildFirstLoader(testClasses, getClass().getClassLoader())) {
            Class<? extends Annotation> isolatedMarker = isolated.loadClass(TestMarker.class.getName()).asSubclass(Annotation.class);
            assertNotSame(TestMarker.class, isolatedMarker);
            // the type the loader sees is described, not the one of this test
            assertTrue(AnnotationType.find(TestMarker.class, isolated).isEmpty());
            AnnotationType<?> type = AnnotationType.find(isolatedMarker, isolated).orElseThrow();
            assertEquals(isolatedMarker, type.getType());
            assertEquals(isolatedMarker, AnnotationType.find(TestMarker.class.getName(), isolated).orElseThrow().getType());
            assertNotSame(type, AnnotationType.find(TestMarker.class, getClass().getClassLoader()).orElseThrow());
        }
        // a loader that sees no builder finds no description
        try (URLClassLoader empty = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            assertTrue(AnnotationType.find(TestMarker.class.getName(), empty).isEmpty());
        }
    }

    /**
     * Loads the classes of this package itself, and everything else from its parent.
     */
    private static final class ChildFirstLoader extends URLClassLoader {

        ChildFirstLoader(URL classes, ClassLoader parent) {
            super(new URL[] {classes}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(TestMarker.class.getPackageName() + ".")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : findClass(name);
            }
        }
    }

    @Test
    void theNameOfAnAnnotationIsFlattened() {
        assertEquals("io_micronaut_Outer_Inner", AnnotationBuilderRegistry.mangle("io.micronaut.Outer$Inner"));
    }
}
