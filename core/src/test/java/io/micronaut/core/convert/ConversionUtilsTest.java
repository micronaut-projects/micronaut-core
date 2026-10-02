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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.builder.TestFlag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionUtilsTest {

    private final ConversionService cs = ConversionService.SHARED;

    @Test
    void basicTypesAreReturnedAsTheyAreConvertedFromNumbersOrDelegated() {
        assertEquals("a", ConversionUtils.toString("a", cs));
        assertEquals("a.B", ConversionUtils.toString(new AnnotationClassValue<>("a.B"), cs));
        assertEquals("sb", ConversionUtils.toString(new StringBuilder("sb"), cs));
        assertEquals("5", ConversionUtils.toString(5, cs));
        assertNull(ConversionUtils.toString(null, cs));

        assertEquals(3, ConversionUtils.toInt(3, cs));
        assertEquals(3, ConversionUtils.toInt(3L, cs));
        assertEquals(3, ConversionUtils.toInt("3", cs));
        assertEquals(0, ConversionUtils.toInt(null, cs));
        assertNull(ConversionUtils.toInteger(null, cs));
        assertEquals(Integer.valueOf(4), ConversionUtils.toInteger(4, cs));

        assertEquals(3L, ConversionUtils.toLong(3, cs));
        assertEquals(3L, ConversionUtils.toLong("3", cs));
        assertEquals(0L, ConversionUtils.toLong(null, cs));
        assertEquals(Long.valueOf(3L), ConversionUtils.toLongObject(3L, cs));
        assertNull(ConversionUtils.toLongObject(null, cs));

        assertEquals((short) 3, ConversionUtils.toShort(3, cs));
        assertEquals((short) 3, ConversionUtils.toShort("3", cs));
        assertEquals((short) 0, ConversionUtils.toShort(null, cs));
        assertEquals(Short.valueOf((short) 3), ConversionUtils.toShortObject((short) 3, cs));

        assertEquals((byte) 3, ConversionUtils.toByte(3, cs));
        assertEquals((byte) 3, ConversionUtils.toByte("3", cs));
        assertEquals((byte) 0, ConversionUtils.toByte(null, cs));
        assertEquals(Byte.valueOf((byte) 3), ConversionUtils.toByteObject((byte) 3, cs));

        assertEquals(1.5f, ConversionUtils.toFloat(1.5, cs));
        assertEquals(1.5f, ConversionUtils.toFloat("1.5", cs));
        assertEquals(0f, ConversionUtils.toFloat(null, cs));
        assertEquals(Float.valueOf(1.5f), ConversionUtils.toFloatObject(1.5f, cs));

        assertEquals(1.5, ConversionUtils.toDouble(new BigDecimal("1.5"), cs));
        assertEquals(1.5, ConversionUtils.toDouble("1.5", cs));
        assertEquals(0d, ConversionUtils.toDouble(null, cs));
        assertEquals(Double.valueOf(1.5), ConversionUtils.toDoubleObject(1.5, cs));

        assertTrue(ConversionUtils.toBoolean(true, cs));
        assertTrue(ConversionUtils.toBoolean("true", cs));
        assertFalse(ConversionUtils.toBoolean(null, cs));
        assertEquals(Boolean.TRUE, ConversionUtils.toBooleanObject(true, cs));
        assertNull(ConversionUtils.toBooleanObject(null, cs));

        assertEquals('x', ConversionUtils.toChar('x', cs));
        assertEquals('x', ConversionUtils.toChar("x", cs));
        assertEquals((char) 0, ConversionUtils.toChar(null, cs));
        assertEquals(Character.valueOf('x'), ConversionUtils.toCharacter('x', cs));
        assertNull(ConversionUtils.toCharacter(null, cs));
    }

    @Test
    void aValueThatCannotBeConvertedFails() {
        assertThrows(IllegalArgumentException.class, () -> ConversionUtils.toInt("not a number", cs));
        assertThrows(IllegalArgumentException.class, () -> ConversionUtils.toBoolean(new Object(), cs));
    }

    @Test
    void arraysAreReturnedAsTheyAreOrConvertedElementByElement() {
        String[] strings = {"a"};
        assertSame(strings, ConversionUtils.toStringArray(strings, cs));
        assertArrayEquals(new String[] {"a", "b"}, ConversionUtils.toStringArray(List.of("a", "b"), cs));
        assertArrayEquals(new String[] {"a", "b"}, ConversionUtils.toStringArray("a,b", cs));
        assertArrayEquals(new String[0], ConversionUtils.toStringArray("", cs));
        assertArrayEquals(new String[] {"a"}, ConversionUtils.toStringArray(new Object[] {"a"}, cs));
        assertArrayEquals(new String[] {"5"}, ConversionUtils.toStringArray(5, cs));
        assertNull(ConversionUtils.toStringArray(null, cs));

        int[] ints = {1};
        assertSame(ints, ConversionUtils.toIntArray(ints, cs));
        assertArrayEquals(new int[] {1, 2}, ConversionUtils.toIntArray(List.of("1", 2), cs));
        assertArrayEquals(new int[] {1}, ConversionUtils.toIntArray(1, cs));
        assertNull(ConversionUtils.toIntArray(null, cs));

        assertArrayEquals(new long[] {1, 2}, ConversionUtils.toLongArray(List.of("1", 2), cs));
        assertArrayEquals(new short[] {1, 2}, ConversionUtils.toShortArray(List.of("1", 2), cs));
        assertArrayEquals(new byte[] {1, 2}, ConversionUtils.toByteArray(List.of("1", 2), cs));
        assertArrayEquals(new float[] {1, 2}, ConversionUtils.toFloatArray(List.of("1", 2), cs));
        assertArrayEquals(new double[] {1, 2}, ConversionUtils.toDoubleArray(List.of("1", 2), cs));
        assertArrayEquals(new char[] {'a', 'b'}, ConversionUtils.toCharArray(List.of("a", 'b'), cs));
        boolean[] booleans = ConversionUtils.toBooleanArray(List.of("true", false), cs);
        assertTrue(booleans[0]);
        assertFalse(booleans[1]);
        assertNull(ConversionUtils.toLongArray(null, cs));
        assertNull(ConversionUtils.toShortArray(null, cs));
        assertNull(ConversionUtils.toByteArray(null, cs));
        assertNull(ConversionUtils.toFloatArray(null, cs));
        assertNull(ConversionUtils.toDoubleArray(null, cs));
        assertNull(ConversionUtils.toBooleanArray(null, cs));
        assertNull(ConversionUtils.toCharArray(null, cs));
    }

    @Test
    void enumsAreConvertedByNameAmongTheConstants() {
        RetentionPolicy[] constants = RetentionPolicy.values();
        assertSame(RetentionPolicy.CLASS, ConversionUtils.toEnum("CLASS", constants));
        assertSame(RetentionPolicy.CLASS, ConversionUtils.toEnum(RetentionPolicy.CLASS, constants));
        assertNull(ConversionUtils.toEnum(null, constants));
        assertThrows(IllegalArgumentException.class, () -> ConversionUtils.toEnum("NONE", constants));

        assertArrayEquals(new RetentionPolicy[] {RetentionPolicy.SOURCE, RetentionPolicy.RUNTIME},
            ConversionUtils.toEnumArray(List.of("SOURCE", RetentionPolicy.RUNTIME), constants));
        assertArrayEquals(new RetentionPolicy[] {RetentionPolicy.CLASS}, ConversionUtils.toEnumArray("CLASS", constants));
        assertNull(ConversionUtils.toEnumArray(null, constants));
    }

    @Test
    void classesAreReadFromAClassAClassValueOrAName() {
        assertSame(String.class, ConversionUtils.toClass(String.class, cs));
        assertSame(String.class, ConversionUtils.toClass(new AnnotationClassValue<>(String.class), cs));
        assertSame(String.class, ConversionUtils.toClass(new AnnotationClassValue<>("java.lang.String"), cs));
        assertSame(String.class, ConversionUtils.toClass("java.lang.String", cs));
        assertNull(ConversionUtils.toClass(null, cs));
        assertThrows(IllegalArgumentException.class, () -> ConversionUtils.toClass(5, cs));

        Class<?>[] classes = {String.class};
        assertSame(classes, ConversionUtils.toClasses(classes, cs));
        assertArrayEquals(new Class<?>[] {String.class, Integer.class},
            ConversionUtils.toClasses(List.of("java.lang.String", Integer.class), cs));
        assertArrayEquals(new Class<?>[] {String.class}, ConversionUtils.toClasses(new AnnotationClassValue<>(String.class), cs));
        assertNull(ConversionUtils.toClasses(null, cs));
    }

    @Test
    void nestedAnnotationsAreBuiltByTheBuilderFromWhateverTheyAreGiven() {
        var builder = ConversionUtils.annotationBuilder(TestFlag.class);
        assertTrue(builder != null);
        assertNull(ConversionUtils.annotationBuilder(Deprecated.class));

        TestFlag fromMap = ConversionUtils.toAnnotation(Map.of("value", "m"), TestFlag.class, builder, cs);
        assertEquals("m", fromMap.value());
        TestFlag fromValue = ConversionUtils.toAnnotation(
            AnnotationValue.builder(TestFlag.class).member("value", "v").build(), TestFlag.class, builder, cs);
        assertEquals("v", fromValue.value());
        // a built annotation is kept, an annotation of the JVM is built again
        assertSame(fromMap, ConversionUtils.toAnnotation(fromMap, TestFlag.class, builder, cs));
        TestFlag jvm = Annotated.class.getAnnotation(TestFlag.class);
        TestFlag rebuilt = ConversionUtils.toAnnotation(jvm, TestFlag.class, builder, cs);
        assertEquals(jvm, rebuilt);
        assertFalse(jvm == rebuilt);
        assertNull(ConversionUtils.toAnnotation(null, TestFlag.class, builder, cs));
        // without a builder an instance is kept
        assertSame(jvm, ConversionUtils.toAnnotation(jvm, TestFlag.class, null, cs));

        TestFlag[] flags = ConversionUtils.toAnnotations(List.of(Map.of("value", "a"), fromValue), TestFlag.class, builder, cs)
            .toArray(new TestFlag[0]);
        assertEquals(2, flags.length);
        assertEquals("a", flags[0].value());
        assertEquals(1, ConversionUtils.toAnnotations(fromMap, TestFlag.class, builder, cs).size());
        assertNull(ConversionUtils.toAnnotations(null, TestFlag.class, builder, cs));
    }

    @Test
    void membersAreTheGivenValueOrTheDefault() {
        assertEquals("given", ConversionUtils.member(Map.of("a", "given"), Map.of("a", "default"), "a"));
        assertEquals("default", ConversionUtils.member(Map.of(), Map.of("a", "default"), "a"));
        assertNull(ConversionUtils.member(Map.of(), Map.of(), "a"));
    }

    @Test
    void membersAreRecordedInTheFormOfTheMetadata() {
        AnnotationClassValue<?>[] classValues = ConversionUtils.toClassValues(new Class<?>[] {String.class});
        assertEquals("java.lang.String", classValues[0].getName());
        assertArrayEquals(new String[] {"CLASS", "SOURCE"},
            ConversionUtils.toEnumNames(new RetentionPolicy[] {RetentionPolicy.CLASS, RetentionPolicy.SOURCE}));

        TestFlag built = ConversionUtils.toAnnotation(Map.of("value", "b"), TestFlag.class,
            ConversionUtils.annotationBuilder(TestFlag.class), cs);
        TestFlag jvm = Annotated.class.getAnnotation(TestFlag.class);
        AnnotationValue<?>[] values = ConversionUtils.toAnnotationValues(new TestFlag[] {built, jvm});
        assertEquals("b", values[0].stringValue("value").orElseThrow());
        assertEquals("jvm", values[1].stringValue("value").orElseThrow());
    }

    @TestFlag("jvm")
    static class Annotated {
    }
}
