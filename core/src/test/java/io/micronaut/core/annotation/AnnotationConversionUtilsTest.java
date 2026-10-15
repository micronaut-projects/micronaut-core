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
package io.micronaut.core.annotation;

import io.micronaut.core.annotation.builder.TestFlag;
import io.micronaut.core.convert.ConversionService;
import org.junit.jupiter.api.Test;

import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationConversionUtilsTest {

    private final ConversionService cs = ConversionService.SHARED;

    @Test
    void stringsAndIntegersAreReturnedAsTheyAreConvertedFromNumbersOrDelegated() {
        assertEquals("a", AnnotationConversionUtils.toString("a", cs));
        assertEquals("a.B", AnnotationConversionUtils.toString(new AnnotationClassValue<>("a.B"), cs));
        assertEquals("sb", AnnotationConversionUtils.toString(new StringBuilder("sb"), cs));
        assertEquals("5", AnnotationConversionUtils.toString(5, cs));
        assertNull(AnnotationConversionUtils.toString(null, cs));

        assertEquals(3, AnnotationConversionUtils.toInt(3, cs));
        assertEquals(3, AnnotationConversionUtils.toInt(3L, cs));
        assertEquals(3, AnnotationConversionUtils.toInt("3", cs));
        assertEquals(0, AnnotationConversionUtils.toInt(null, cs));
        assertNull(AnnotationConversionUtils.toInteger(null, cs));
        assertEquals(Integer.valueOf(4), AnnotationConversionUtils.toInteger(4, cs));

    }

    @Test
    void integralTypesAreConverted() {
        assertEquals(3L, AnnotationConversionUtils.toLong(3, cs));
        assertEquals(3L, AnnotationConversionUtils.toLong("3", cs));
        assertEquals(0L, AnnotationConversionUtils.toLong(null, cs));
        assertEquals(Long.valueOf(3L), AnnotationConversionUtils.toLongObject(3L, cs));
        assertNull(AnnotationConversionUtils.toLongObject(null, cs));

        assertEquals((short) 3, AnnotationConversionUtils.toShort(3, cs));
        assertEquals((short) 3, AnnotationConversionUtils.toShort("3", cs));
        assertEquals((short) 0, AnnotationConversionUtils.toShort(null, cs));
        assertEquals(Short.valueOf((short) 3), AnnotationConversionUtils.toShortObject((short) 3, cs));

        assertEquals((byte) 3, AnnotationConversionUtils.toByte(3, cs));
        assertEquals((byte) 3, AnnotationConversionUtils.toByte("3", cs));
        assertEquals((byte) 0, AnnotationConversionUtils.toByte(null, cs));
        assertEquals(Byte.valueOf((byte) 3), AnnotationConversionUtils.toByteObject((byte) 3, cs));

    }

    @Test
    void floatingPointBooleanAndCharacterTypesAreConverted() {
        assertEquals(1.5f, AnnotationConversionUtils.toFloat(1.5, cs));
        assertEquals(1.5f, AnnotationConversionUtils.toFloat("1.5", cs));
        assertEquals(0f, AnnotationConversionUtils.toFloat(null, cs));
        assertEquals(Float.valueOf(1.5f), AnnotationConversionUtils.toFloatObject(1.5f, cs));

        assertEquals(1.5, AnnotationConversionUtils.toDouble(new BigDecimal("1.5"), cs));
        assertEquals(1.5, AnnotationConversionUtils.toDouble("1.5", cs));
        assertEquals(0d, AnnotationConversionUtils.toDouble(null, cs));
        assertEquals(Double.valueOf(1.5), AnnotationConversionUtils.toDoubleObject(1.5, cs));

        assertTrue(AnnotationConversionUtils.toBoolean(true, cs));
        assertTrue(AnnotationConversionUtils.toBoolean("true", cs));
        assertFalse(AnnotationConversionUtils.toBoolean(null, cs));
        assertEquals(Boolean.TRUE, AnnotationConversionUtils.toBooleanObject(true, cs));
        assertNull(AnnotationConversionUtils.toBooleanObject(null, cs));

        assertEquals('x', AnnotationConversionUtils.toChar('x', cs));
        assertEquals('x', AnnotationConversionUtils.toChar("x", cs));
        assertEquals((char) 0, AnnotationConversionUtils.toChar(null, cs));
        assertEquals(Character.valueOf('x'), AnnotationConversionUtils.toCharacter('x', cs));
        assertNull(AnnotationConversionUtils.toCharacter(null, cs));
    }

    @Test
    void aValueThatCannotBeConvertedFails() {
        assertThrows(IllegalArgumentException.class, () -> AnnotationConversionUtils.toInt("not a number", cs));
        Object notABoolean = new Object();
        assertThrows(IllegalArgumentException.class, () -> AnnotationConversionUtils.toBoolean(notABoolean, cs));
    }

    @Test
    void arraysAreReturnedAsTheyAreOrConvertedElementByElement() {
        String[] strings = {"a"};
        assertSame(strings, AnnotationConversionUtils.toStringArray(strings, cs));
        assertArrayEquals(new String[] {"a", "b"}, AnnotationConversionUtils.toStringArray(List.of("a", "b"), cs));
        assertArrayEquals(new String[] {"a", "b"}, AnnotationConversionUtils.toStringArray("a,b", cs));
        assertArrayEquals(new String[0], AnnotationConversionUtils.toStringArray("", cs));
        assertArrayEquals(new String[] {"a"}, AnnotationConversionUtils.toStringArray(new Object[] {"a"}, cs));
        assertArrayEquals(new String[] {"5"}, AnnotationConversionUtils.toStringArray(5, cs));
        assertNull(AnnotationConversionUtils.toStringArray(null, cs));

        int[] ints = {1};
        assertSame(ints, AnnotationConversionUtils.toIntArray(ints, cs));
        assertArrayEquals(new int[] {1, 2}, AnnotationConversionUtils.toIntArray(List.of("1", 2), cs));
        assertArrayEquals(new int[] {1}, AnnotationConversionUtils.toIntArray(1, cs));
        assertNull(AnnotationConversionUtils.toIntArray(null, cs));

    }

    @Test
    void arraysOfTheOtherPrimitiveTypesAreConvertedElementByElement() {
        assertArrayEquals(new long[] {1, 2}, AnnotationConversionUtils.toLongArray(List.of("1", 2), cs));
        assertArrayEquals(new short[] {1, 2}, AnnotationConversionUtils.toShortArray(List.of("1", 2), cs));
        assertArrayEquals(new byte[] {1, 2}, AnnotationConversionUtils.toByteArray(List.of("1", 2), cs));
        assertArrayEquals(new float[] {1, 2}, AnnotationConversionUtils.toFloatArray(List.of("1", 2), cs));
        assertArrayEquals(new double[] {1, 2}, AnnotationConversionUtils.toDoubleArray(List.of("1", 2), cs));
        assertArrayEquals(new char[] {'a', 'b'}, AnnotationConversionUtils.toCharArray(List.of("a", 'b'), cs));
        boolean[] booleans = AnnotationConversionUtils.toBooleanArray(List.of("true", false), cs);
        assertTrue(booleans[0]);
        assertFalse(booleans[1]);
        assertNull(AnnotationConversionUtils.toLongArray(null, cs));
        assertNull(AnnotationConversionUtils.toShortArray(null, cs));
        assertNull(AnnotationConversionUtils.toByteArray(null, cs));
        assertNull(AnnotationConversionUtils.toFloatArray(null, cs));
        assertNull(AnnotationConversionUtils.toDoubleArray(null, cs));
        assertNull(AnnotationConversionUtils.toBooleanArray(null, cs));
        assertNull(AnnotationConversionUtils.toCharArray(null, cs));
    }

    @Test
    void enumsAreConvertedByNameAmongTheConstants() {
        RetentionPolicy[] constants = RetentionPolicy.values();
        assertSame(RetentionPolicy.CLASS, AnnotationConversionUtils.toEnum("CLASS", constants));
        assertSame(RetentionPolicy.CLASS, AnnotationConversionUtils.toEnum(RetentionPolicy.CLASS, constants));
        assertNull(AnnotationConversionUtils.toEnum(null, constants));
        assertThrows(IllegalArgumentException.class, () -> AnnotationConversionUtils.toEnum("NONE", constants));

        assertArrayEquals(new RetentionPolicy[] {RetentionPolicy.SOURCE, RetentionPolicy.RUNTIME},
            AnnotationConversionUtils.toEnumArray(List.of("SOURCE", RetentionPolicy.RUNTIME), constants));
        assertArrayEquals(new RetentionPolicy[] {RetentionPolicy.CLASS}, AnnotationConversionUtils.toEnumArray("CLASS", constants));
        assertNull(AnnotationConversionUtils.toEnumArray(null, constants));
    }

    @Test
    void classesAreReadFromAClassAClassValueOrAName() {
        assertSame(String.class, AnnotationConversionUtils.toClass(String.class, cs));
        assertSame(String.class, AnnotationConversionUtils.toClass(new AnnotationClassValue<>(String.class), cs));
        assertSame(String.class, AnnotationConversionUtils.toClass(new AnnotationClassValue<>("java.lang.String"), cs));
        assertSame(String.class, AnnotationConversionUtils.toClass("java.lang.String", cs));
        assertNull(AnnotationConversionUtils.toClass(null, cs));
        assertThrows(IllegalArgumentException.class, () -> AnnotationConversionUtils.toClass(5, cs));

        Class<?>[] classes = {String.class};
        assertSame(classes, AnnotationConversionUtils.toClasses(classes, cs));
        assertArrayEquals(new Class<?>[] {String.class, Integer.class},
            AnnotationConversionUtils.toClasses(List.of("java.lang.String", Integer.class), cs));
        assertArrayEquals(new Class<?>[] {String.class}, AnnotationConversionUtils.toClasses(new AnnotationClassValue<>(String.class), cs));
        assertNull(AnnotationConversionUtils.toClasses(null, cs));
    }

    @Test
    void nestedAnnotationsAreBuiltByTheBuilderFromWhateverTheyAreGiven() {
        var builder = AnnotationConversionUtils.annotationBuilder(TestFlag.class);
        assertNotNull(builder);
        assertNull(AnnotationConversionUtils.annotationBuilder(Deprecated.class));

        TestFlag fromMap = AnnotationConversionUtils.toAnnotation(Map.of("value", "m"), TestFlag.class, builder, cs);
        assertEquals("m", fromMap.value());
        TestFlag fromValue = AnnotationConversionUtils.toAnnotation(
            AnnotationValue.builder(TestFlag.class).member("value", "v").build(), TestFlag.class, builder, cs);
        assertEquals("v", fromValue.value());
        // a built annotation is kept, an annotation of the JVM is built again
        assertSame(fromMap, AnnotationConversionUtils.toAnnotation(fromMap, TestFlag.class, builder, cs));
        TestFlag jvm = Annotated.class.getAnnotation(TestFlag.class);
        TestFlag rebuilt = AnnotationConversionUtils.toAnnotation(jvm, TestFlag.class, builder, cs);
        assertEquals(jvm, rebuilt);
        assertNotSame(jvm, rebuilt);
        assertNull(AnnotationConversionUtils.toAnnotation(null, TestFlag.class, builder, cs));
        // without a builder an instance is kept
        assertSame(jvm, AnnotationConversionUtils.toAnnotation(jvm, TestFlag.class, null, cs));

        TestFlag[] flags = AnnotationConversionUtils.toAnnotations(List.of(Map.of("value", "a"), fromValue), TestFlag.class, builder, cs)
            .toArray(new TestFlag[0]);
        assertEquals(2, flags.length);
        assertEquals("a", flags[0].value());
        assertEquals(1, AnnotationConversionUtils.toAnnotations(fromMap, TestFlag.class, builder, cs).size());
        assertNull(AnnotationConversionUtils.toAnnotations(null, TestFlag.class, builder, cs));
    }

    @Test
    void membersAreTheGivenValueOrTheDefault() {
        assertEquals("given", AnnotationConversionUtils.member(Map.of("a", "given"), Map.of("a", "default"), "a"));
        assertEquals("default", AnnotationConversionUtils.member(Map.of(), Map.of("a", "default"), "a"));
        assertNull(AnnotationConversionUtils.member(Map.of(), Map.of(), "a"));
    }

    @Test
    void membersAreRecordedInTheFormOfTheMetadata() {
        AnnotationClassValue<?>[] classValues = AnnotationConversionUtils.toClassValues(new Class<?>[] {String.class});
        assertEquals("java.lang.String", classValues[0].getName());
        assertArrayEquals(new String[] {"CLASS", "SOURCE"},
            AnnotationConversionUtils.toEnumNames(new RetentionPolicy[] {RetentionPolicy.CLASS, RetentionPolicy.SOURCE}));

        TestFlag built = AnnotationConversionUtils.toAnnotation(Map.of("value", "b"), TestFlag.class,
            AnnotationConversionUtils.annotationBuilder(TestFlag.class), cs);
        TestFlag jvm = Annotated.class.getAnnotation(TestFlag.class);
        AnnotationValue<?>[] values = AnnotationConversionUtils.toAnnotationValues(new TestFlag[] {built, jvm});
        assertEquals("b", values[0].stringValue("value").orElseThrow());
        assertEquals("jvm", values[1].stringValue("value").orElseThrow());
    }

    @TestFlag("jvm")
    static class Annotated {
    }
}
