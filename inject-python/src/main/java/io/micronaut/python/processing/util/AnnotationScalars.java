/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Narrows a literal from the Python processor (a String, Boolean, Integer, Long or Double) to the
 * declared scalar annotation member type, the way the Java annotation processor would deliver it.
 * Python is more permissive than Java here: {@code value=True} on a {@code String} member becomes
 * {@code "true"}, and {@code 1} on a {@code long} member becomes {@code 1L}.
 *
 * @since 5.2.0
 */
@Internal
public final class AnnotationScalars {

    private AnnotationScalars() {
    }

    /**
     * @param value      The literal value
     * @param memberType The declared member type
     * @return The value narrowed to the member type, or the value itself when no narrowing applies
     */
    public static @Nullable Object coerce(@Nullable Object value, @Nullable ClassElement memberType) {
        if (value == null || memberType == null) {
            return value;
        }
        String typeName = memberType.getName();
        if (String.class.getName().equals(typeName)) {
            return value instanceof String || !(value instanceof Boolean || value instanceof Number || value instanceof Character)
                ? value
                : String.valueOf(value);
        }
        if (value instanceof Number number) {
            return switch (typeName) {
                case "byte", "java.lang.Byte" -> (byte) integral(number, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte");
                case "short", "java.lang.Short" -> (short) integral(number, Short.MIN_VALUE, Short.MAX_VALUE, "short");
                case "int", "java.lang.Integer" -> (int) integral(number, Integer.MIN_VALUE, Integer.MAX_VALUE, "int");
                case "long", "java.lang.Long" -> integral(number, Long.MIN_VALUE, Long.MAX_VALUE, "long");
                case "float", "java.lang.Float" -> floating(number);
                case "double", "java.lang.Double" -> number.doubleValue();
                default -> value;
            };
        }
        if (value instanceof String string && ("char".equals(typeName) || Character.class.getName().equals(typeName))) {
            return character(string);
        }
        return value;
    }

    /**
     * A literal for an integral member, checked the way javac checks a constant: integral and in range.
     *
     * @param number The literal
     * @param min The member type's minimum
     * @param max The member type's maximum
     * @param typeName The member type, for the message
     * @return The value
     */
    public static long integral(Number number, long min, long max, String typeName) {
        BigDecimal exact;
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isInfinite(d) || Double.isNaN(d)) {
                throw new IllegalArgumentException("Annotation member of type " + typeName + " cannot take the value " + number);
            }
            // the decimal value, so a huge double cannot saturate to the type's maximum before the range check
            exact = BigDecimal.valueOf(d);
        } else if (number instanceof BigDecimal bigDecimal) {
            exact = bigDecimal;
        } else if (number instanceof BigInteger bigInteger) {
            exact = new BigDecimal(bigInteger);
        } else {
            exact = BigDecimal.valueOf(number.longValue());
        }
        if (exact.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Annotation member of type " + typeName + " cannot take the value " + number);
        }
        if (exact.compareTo(BigDecimal.valueOf(min)) < 0 || exact.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException("Annotation member of type " + typeName + " cannot take the value " + number + ": out of range");
        }
        return exact.longValueExact();
    }

    /**
     * A literal for a {@code float} member: a finite value must not overflow to infinity, as javac
     * rejects a constant too large for the type.
     *
     * @param number The literal
     * @return The float
     */
    public static float floating(Number number) {
        float value = number.floatValue();
        if (Float.isInfinite(value) && !Double.isInfinite(number.doubleValue())) {
            throw new IllegalArgumentException("Annotation member of type float cannot take the value " + number + ": out of range");
        }
        return value;
    }

    /**
     * A literal for a numeric member: a number, not a string or a boolean that happens to parse.
     *
     * @param value The literal
     * @param typeName The member type, for the message
     * @return The number
     */
    public static Number number(Object value, String typeName) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException("Annotation member of type " + typeName + " cannot take the value " + value);
    }

    /**
     * A literal for a boolean member: a boolean, not a string.
     *
     * @param value The literal
     * @return The boolean
     */
    public static boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("Annotation member of type boolean cannot take the value " + value);
    }

    /**
     * A literal for a {@code char} member: exactly one character, as in Java source.
     *
     * @param string The literal
     * @return The character
     */
    public static char character(String string) {
        if (string.length() != 1) {
            throw new IllegalArgumentException("Annotation member of type char needs a one-character string, not \"" + string + "\"");
        }
        return string.charAt(0);
    }

    /**
     * A literal for a {@code char} member or array element: a one-character string, never a number
     * or a boolean that happens to print as one character.
     *
     * @param value The literal
     * @return The character
     */
    public static char character(Object value) {
        if (value instanceof Character c) {
            return c;
        }
        if (value instanceof String string) {
            return character(string);
        }
        throw new IllegalArgumentException("Annotation member of type char needs a one-character string, not " + value);
    }
}
