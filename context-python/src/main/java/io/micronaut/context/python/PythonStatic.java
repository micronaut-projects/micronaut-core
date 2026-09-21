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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * The Python semantics a statically compiled function body needs where Java's differ: the string
 * form of values, division and modulo, and truthiness. Called by the generated stubs.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
@Internal
@UsedByGeneratedCode
public final class PythonStatic {

    private PythonStatic() {
    }

    /**
     * @param value A Python int
     * @return {@code str(value)}
     */
    public static String str(long value) {
        return Long.toString(value);
    }

    /**
     * @param value A Python float
     * @return {@code str(value)}: the shortest repr, in fixed notation for exponents from -4 to 15
     * and in scientific notation otherwise, as CPython renders it
     */
    public static String str(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0.0" : "0.0";
        }
        BigDecimal decimal = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = decimal.unscaledValue().toString();
        int exponent = digits.length() - 1 - decimal.scale();  // the power of ten of the first digit
        StringBuilder out = new StringBuilder();
        if (value < 0) {
            out.append('-');
        }
        if (exponent >= -4 && exponent < 16) {
            if (exponent < 0) {
                out.append("0.").append("0".repeat(-exponent - 1)).append(digits);
            } else if (exponent + 1 >= digits.length()) {
                out.append(digits).append("0".repeat(exponent + 1 - digits.length())).append(".0");
            } else {
                out.append(digits, 0, exponent + 1).append('.').append(digits, exponent + 1, digits.length());
            }
        } else {
            out.append(digits.charAt(0));
            if (digits.length() > 1) {
                out.append('.').append(digits, 1, digits.length());
            }
            out.append('e').append(exponent < 0 ? '-' : '+');
            int magnitude = Math.abs(exponent);
            if (magnitude < 10) {
                out.append('0');
            }
            out.append(magnitude);
        }
        return out.toString();
    }

    /**
     * @param value A Python bool
     * @return {@code str(value)}: {@code True} or {@code False}
     */
    public static String str(boolean value) {
        return value ? "True" : "False";
    }

    /**
     * @param value Any value
     * @return {@code str(value)}: {@code None} for null, the Python spellings for booleans and
     * floats, the value itself for a string, {@code toString()} otherwise
     */
    public static String str(@Nullable Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Boolean bool) {
            return str(bool.booleanValue());
        }
        if (value instanceof Double || value instanceof Float) {
            return str(((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left / right}: always a float
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static double divide(long left, long right) {
        if (right == 0) {
            throw new ArithmeticException("division by zero");
        }
        return (double) left / right;
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left / right}
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static double divide(double left, double right) {
        if (right == 0) {
            throw new ArithmeticException("float division by zero");
        }
        return left / right;
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left // right}: the floor of the quotient
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static long floorDiv(long left, long right) {
        if (right == 0) {
            throw new ArithmeticException("integer division or modulo by zero");
        }
        return Math.floorDiv(left, right);
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left % right}: the remainder with the sign of the divisor
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static long floorMod(long left, long right) {
        if (right == 0) {
            throw new ArithmeticException("integer division or modulo by zero");
        }
        return Math.floorMod(left, right);
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left // right} on floats
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static double floorDiv(double left, double right) {
        if (right == 0) {
            throw new ArithmeticException("float floor division by zero");
        }
        // The parity target is the runtime the Python body would run on: GraalPy floors the
        // quotient (1.0 // 0.1 is 10.0 there), where CPython derives it from the fmod remainder
        // (9.0). The harness pins this against GraalPy.
        return Math.floor(left / right);
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left % right} on floats: the remainder with the sign of the divisor
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static double floorMod(double left, double right) {
        if (right == 0) {
            throw new ArithmeticException("float modulo by zero");
        }
        double remainder = left % right;
        if (remainder == 0) {
            return Math.copySign(0.0, right);  // a zero remainder takes the divisor's sign
        }
        if ((remainder < 0) != (right < 0)) {
            remainder += right;
        }
        return remainder;
    }

    /**
     * @param value A Python int
     * @return The value as a short
     * @throws ArithmeticException When the value does not fit, as the boundary conversion rejects it
     */
    public static short toShortExact(long value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new ArithmeticException("short overflow");
        }
        return (short) value;
    }

    /**
     * @param value A Python int
     * @return The value as a byte
     * @throws ArithmeticException When the value does not fit, as the boundary conversion rejects it
     */
    public static byte toByteExact(long value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new ArithmeticException("byte overflow");
        }
        return (byte) value;
    }

    /**
     * An {@code assert} statement: assertions are enabled in the runtime, so a false condition
     * raises. A Python exception reaches a Java caller as a runtime exception, so the failure is
     * one too rather than a {@link java.lang.AssertionError}.
     *
     * @param condition The condition
     * @param message   The message, or {@code null}
     * @throws IllegalStateException When the condition is false, as Python raises AssertionError
     */
    public static void assertion(boolean condition, @Nullable String message) {
        if (!condition) {
            throw new IllegalStateException(message == null ? "AssertionError" : "AssertionError: " + message);
        }
    }

    /**
     * @param base     The base
     * @param exponent The exponent, zero or more
     * @return {@code base ** exponent}, exactly
     * @throws ArithmeticException When the result does not fit a long, or the exponent is negative
     */
    public static long power(long base, long exponent) {
        if (exponent < 0) {
            throw new ArithmeticException("negative exponent");
        }
        long result = 1;
        long factor = base;
        long remaining = exponent;
        while (remaining > 0) {
            if ((remaining & 1) == 1) {
                result = Math.multiplyExact(result, factor);
            }
            remaining >>= 1;
            if (remaining > 0) {
                factor = Math.multiplyExact(factor, factor);
            }
        }
        return result;
    }

    /**
     * @param exception A checked exception a Java call of a compiled body threw
     * @return The exception to throw instead: the exception itself when it is unchecked, else
     * wrapped, as a Python exception reaches a Java caller as a runtime exception
     */
    public static RuntimeException unchecked(Exception exception) {
        return exception instanceof RuntimeException runtime ? runtime : new IllegalStateException(exception.toString(), exception);
    }

    /**
     * @param value A Python int given to a parameter of a reference type
     * @return The value boxed as the host boundary boxes it: an Integer when it fits one, else a Long
     */
    public static Number box(long value) {
        if (value == (int) value) {
            return Integer.valueOf((int) value);
        }
        return Long.valueOf(value);
    }

    /**
     * @param value A string, or None
     * @return Its truthiness: non-null and non-empty
     */
    public static boolean truthy(@Nullable String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * @param value A value
     * @return Its truthiness: None is false, an empty string, collection or map is false, a
     * number is true when it is not zero, anything else is true
     */
    public static boolean truthy(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        return true;
    }
}
