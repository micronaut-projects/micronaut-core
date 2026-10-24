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
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

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

    /**
     * The class attribute through which the rewritten methods of a class reach the Java bodies of
     * the objects bound to them: {@code None} until an object of the class is bound, see
     * {@link #bindCompiled(org.graalvm.polyglot.Value, String, Object)}.
     */
    public static final String COMPILED_MEMBER = "__micronaut_compiled__";

    private static final ConcurrentHashMap<String, LongAdder> ENTRIES = new ConcurrentHashMap<>();
    private static final String ZERO_DIVISION_ERROR = "ZeroDivisionError";
    private static final String TYPE_ERROR = "TypeError";
    private static final String ASSERTION_ERROR = "AssertionError";
    private static final String BIND_COMPILED = "__micronaut_bind_compiled";
    private static final String RAISE_PYTHON = "__micronaut_raise";
    // the largest magnitude a double holds every long up to exactly
    private static final long EXACT_DOUBLE_LONG = 1L << 53;
    private static final int QUOTIENT_SHIFT = 128;

    private PythonStatic() {
    }

    /**
     * Counts an entry into a compiled body; emitted at the start of every compiled body when the
     * compilation traces, see {@code micronaut.python.compile.static.trace}.
     *
     * @param key The compiled body, as {@code class#method}
     */
    public static void entered(String key) {
        ENTRIES.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /**
     * @param key The compiled body, as {@code class#method}
     * @return How many times the compiled body ran since the last reset
     */
    public static long entries(String key) {
        LongAdder adder = ENTRIES.get(key);
        return adder == null ? 0 : adder.sum();
    }

    /**
     * Forgets every counted entry.
     */
    public static void resetEntries() {
        ENTRIES.clear();
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
     * @return {@code left / right}: always a float, the quotient correctly rounded as Python
     * rounds it, also for operands a double does not hold exactly
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static double divide(long left, long right) {
        if (right == 0) {
            throw new PythonArithmeticError(ZERO_DIVISION_ERROR, "division by zero");
        }
        if (Math.abs(left) <= EXACT_DOUBLE_LONG && Math.abs(right) <= EXACT_DOUBLE_LONG) {
            // both convert exactly, and the division of doubles is correctly rounded
            return (double) left / right;
        }
        // the quotient scaled by 2**128 has more than 64 bits; the sticky bit of a remainder makes
        // its rounding to 53 bits the rounding of the exact quotient, and the scaling back is exact
        BigInteger[] quotient = BigInteger.valueOf(left).abs().shiftLeft(QUOTIENT_SHIFT).divideAndRemainder(BigInteger.valueOf(right).abs());
        BigInteger scaled = quotient[1].signum() == 0 ? quotient[0] : quotient[0].setBit(0);
        double magnitude = Math.scalb(scaled.doubleValue(), -QUOTIENT_SHIFT);
        return (left < 0) != (right < 0) ? -magnitude : magnitude;
    }

    /**
     * An int compared with a float, exactly as Python compares them: a long above 2**53 is not
     * rounded to a double first.
     *
     * @param left  The int
     * @param right The float
     * @return The sign of {@code left - right}, as -1.0, 0.0 or 1.0, or NaN when {@code right} is
     * NaN, so that comparing the result with 0.0 answers every comparison as Python does
     */
    @SuppressWarnings("java:S2111") // the exact value of the double is wanted; BigDecimal.valueOf reads its shortest decimal string
    public static double compare(long left, double right) {
        if (Double.isNaN(right)) {
            return Double.NaN;
        }
        if (Math.abs(left) <= EXACT_DOUBLE_LONG || Double.isInfinite(right)) {
            double exact = left;
            // the difference of two doubles is zero only when they are equal (0 and -0.0 included)
            return Math.signum(exact - right);
        }
        return new BigDecimal(left).compareTo(new BigDecimal(right));
    }

    /**
     * {@code left + right} on two Python strings, either of which may be a null a Java call
     * returned or a Java caller passed.
     *
     * @param left  The left operand
     * @param right The right operand
     * @return The concatenation
     * @throws IllegalStateException When an operand is null, as Python raises TypeError on None
     */
    public static String concat(@Nullable String left, @Nullable String right) {
        if (left == null) {
            throw new PythonError(TYPE_ERROR, "unsupported operand type(s) for +: 'NoneType' and 'str'");
        }
        if (right == null) {
            throw new PythonError(TYPE_ERROR, "can only concatenate str (not \"NoneType\") to str");
        }
        return left.concat(right);
    }

    /**
     * @param left  The dividend
     * @param right The divisor
     * @return {@code left / right}
     * @throws ArithmeticException On a zero divisor, as Python raises ZeroDivisionError
     */
    public static double divide(double left, double right) {
        if (right == 0) {
            throw new PythonArithmeticError(ZERO_DIVISION_ERROR, "float division by zero");
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
            throw new PythonArithmeticError(ZERO_DIVISION_ERROR, "integer division or modulo by zero");
        }
        if (left == Long.MIN_VALUE && right == -1) {
            // the quotient 2**63 does not fit: raised as the other exact operations raise where Python promotes
            throw new ArithmeticException("long overflow");
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
            throw new PythonArithmeticError(ZERO_DIVISION_ERROR, "integer division or modulo by zero");
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
            throw new PythonArithmeticError(ZERO_DIVISION_ERROR, "float floor division by zero");
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
            throw new PythonArithmeticError(ZERO_DIVISION_ERROR, "float modulo by zero");
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
            throw new PythonError(ASSERTION_ERROR, message);
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
     * Binds a Python object to the compiled bodies of one class of its MRO: the rewritten methods
     * of that class run the Java bodies for the object from then on, see {@code apply_delegation}.
     * The binding is kept outside the object, weakly, so a copy of the object is not bound and
     * the object's attributes are its own.
     *
     * @param pythonObject The Python object of a stub
     * @param owner        The generated class whose compiled bodies are bound, as the rewritten methods name it
     * @param delegate     The delegate of that class: a stateless Java object whose methods take the Python object first
     */
    public static void bindCompiled(org.graalvm.polyglot.Value pythonObject, String owner, Object delegate) {
        PythonContextRuntime.helper(pythonObject.getContext(), BIND_COMPILED).execute(pythonObject, owner, delegate);
    }

    /**
     * The exception a compiled body called from Python throws instead: a failure the body raises
     * where Python raises one (a division by zero, an assertion, + on None) becomes that Python
     * exception, raised through the Python object's context, and a checked exception of a Java call
     * is thrown as itself, as a Python caller of the Java method would see it.
     *
     * @param self      The Python object the body ran on
     * @param exception The exception the body threw
     * @return The exception to throw, when it has no Python counterpart
     */
    public static RuntimeException forPython(org.graalvm.polyglot.Value self, RuntimeException exception) {
        if (exception instanceof PythonFailure failure) {
            // raises the Python exception, which reaches the Python caller as itself
            PythonContextRuntime.helper(self.getContext(), RAISE_PYTHON).execute(failure.pythonType(), failure.pythonMessage());
        }
        if (exception instanceof UncheckedFailure unchecked && unchecked.getCause() instanceof Exception cause) {
            throw PythonStatic.<RuntimeException>sneakyThrow(cause);
        }
        return exception;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    /**
     * @param value A conditional expression used as an operand
     * @return The value: the call groups the expression, which the source generator would otherwise
     * render without the parentheses Java needs around a conditional operand
     */
    public static long group(long value) {
        return value;
    }

    /**
     * @param value A conditional expression used as an operand
     * @return The value, grouped
     */
    public static double group(double value) {
        return value;
    }

    /**
     * @param value A conditional expression used as an operand
     * @return The value, grouped
     */
    public static boolean group(boolean value) {
        return value;
    }

    /**
     * @param value A conditional expression used as an operand
     * @param <T>   The type of the value
     * @return The value, grouped
     */
    public static <T> T group(T value) {
        return value;
    }

    /**
     * @param exception A checked exception a Java call of a compiled body threw
     * @return The exception to throw instead: the exception itself when it is unchecked, else
     * wrapped, as a Python exception reaches a Java caller as a runtime exception
     */
    public static RuntimeException unchecked(Exception exception) {
        return exception instanceof RuntimeException runtime ? runtime : new UncheckedFailure(exception);
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
     * @param value An element of a collection, boxed by the host boundary
     * @return The value as a Python int
     * @throws ClassCastException When the element is not an int (a float, say, which the compiled body
     * cannot keep as Python would), so that the body fails rather than truncates it
     * @throws ArithmeticException When the element is an int that does not fit a long
     */
    public static long toLong(@Nullable Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger big) {
            return big.longValueExact();
        }
        if (value instanceof Boolean bool) {
            return bool.booleanValue() ? 1 : 0;
        }
        throw new ClassCastException("not an int: " + str(value));
    }

    /**
     * @param value An element of a collection, boxed by the host boundary
     * @return The value as a Python float; a bool is 1.0 or 0.0, as Python's arithmetic takes it
     * @throws ClassCastException When the element is not a number
     */
    public static double toDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool.booleanValue() ? 1.0 : 0.0;
        }
        throw new ClassCastException("not a float: " + str(value));
    }

    /**
     * @param step The step of a range
     * @return The step
     * @throws IllegalArgumentException When the step is zero, as Python raises ValueError
     */
    public static long step(long step) {
        if (step == 0) {
            throw new IllegalArgumentException("range() arg 3 must not be zero");
        }
        return step;
    }

    /**
     * @param value The current value of a range loop
     * @param stop  The bound
     * @param step  The step
     * @return The next value of the range; the bound when the next value would not fit a long,
     * since the loop ends there as Python's would
     */
    public static long advance(long value, long stop, long step) {
        long next = value + step;
        boolean overflowed = step > 0 ? next < value : next > value;
        return overflowed ? stop : next;
    }

    /**
     * @param value The current value of a range loop
     * @param stop  The bound
     * @param step  The step
     * @return Whether the value is within the range
     */
    public static boolean inRange(long value, long stop, long step) {
        return step > 0 ? value < stop : value > stop;
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

    /**
     * A failure of a compiled body that Python raises as an exception of its own.
     */
    sealed interface PythonFailure permits PythonError, PythonArithmeticError {
        /**
         * @return The name of the Python builtin exception
         */
        String pythonType();

        /**
         * @return The message of the Python exception, or {@code null} for none
         */
        @Nullable String pythonMessage();
    }

    /**
     * An assertion that failed, or an operation Python rejects with a TypeError: a runtime exception
     * for a Java caller, as a Python exception reaches it.
     */
    static final class PythonError extends IllegalStateException implements PythonFailure {
        private final String pythonType;
        private final @Nullable String pythonMessage;

        PythonError(String pythonType, @Nullable String pythonMessage) {
            super(pythonMessage == null ? pythonType : pythonType + ": " + pythonMessage);
            this.pythonType = pythonType;
            this.pythonMessage = pythonMessage;
        }

        @Override
        public String pythonType() {
            return pythonType;
        }

        @Override
        public @Nullable String pythonMessage() {
            return pythonMessage;
        }
    }

    /**
     * A division by zero: an ArithmeticException for a Java caller, a ZeroDivisionError for a Python one.
     */
    static final class PythonArithmeticError extends ArithmeticException implements PythonFailure {
        private final String pythonType;

        PythonArithmeticError(String pythonType, String message) {
            super(message);
            this.pythonType = pythonType;
        }

        @Override
        public String pythonType() {
            return pythonType;
        }

        @Override
        public @Nullable String pythonMessage() {
            return getMessage();
        }
    }

    /**
     * A checked exception of a Java call of a compiled body, carried unchecked to a Java caller.
     */
    static final class UncheckedFailure extends IllegalStateException {
        UncheckedFailure(Exception cause) {
            super(cause.toString(), cause);
        }
    }
}
