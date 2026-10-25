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
import java.util.AbstractList;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

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
    // the literals Python's int() and float() parse in base 10, after the digits are made ASCII
    // the repetitions are possessive: a digit run never needs to give a digit back, and possessive
    // repetition keeps a very long literal from overflowing the stack of the backtracking matcher
    private static final Pattern PYTHON_INT = Pattern.compile("[+-]?\\d(?:_?\\d)*+");
    // the digits of a float literal on either side of its point, which the float parse checks apart
    private static final Pattern PYTHON_DIGITS = Pattern.compile("\\d(?:_?\\d)*+");
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
     * @param key The compiled body, as {@code package.Class#method}: the qualified name of the class and the method
     */
    public static void entered(String key) {
        ENTRIES.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /**
     * @param key The compiled body, as {@code package.Class#method}: the qualified name of the class and the method
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
        if (value instanceof Collection<?> || value instanceof Map<?, ?> || value instanceof Object[]) {
            return repr(value);
        }
        return String.valueOf(value);
    }

    /**
     * @param value A value
     * @return {@code repr(value)} for the values a compiled body renders: a string quoted as Python
     * quotes it, a list, tuple, set or dict with its elements rendered the same way, and {@code str(value)} otherwise
     */
    public static String repr(@Nullable Object value) {
        return repr(value, null);
    }

    /**
     * @param value  A value
     * @param active The containers being rendered, or {@code null} before the first one: a container reached
     *               again within itself renders as Python's recursion marker, {@code [...]} or {@code {...}}
     */
    private static String repr(@Nullable Object value, @Nullable Set<Object> active) {
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Map<?, ?> map) {
            if (active != null && active.contains(map)) {
                return "{...}";
            }
            Set<Object> entered = enter(active, map);
            try {
                StringBuilder out = new StringBuilder("{");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (out.length() > 1) {
                        out.append(", ");
                    }
                    out.append(repr(entry.getKey(), entered)).append(": ").append(repr(entry.getValue(), entered));
                }
                return out.append('}').toString();
            } finally {
                entered.remove(map);
            }
        }
        if (value instanceof java.util.Set<?> set) {
            if (set.isEmpty()) {
                return "set()";
            }
            return join(set, "{", "}", active);
        }
        if (value instanceof Tuple tuple) {
            if (active != null && active.contains(tuple)) {
                return "(...)";
            }
            if (tuple.size() == 1) {
                Set<Object> entered = enter(active, tuple);
                try {
                    return "(" + repr(tuple.get(0), entered) + ",)";
                } finally {
                    entered.remove(tuple);
                }
            }
            return join(tuple, "(", ")", active);
        }
        if (value instanceof Collection<?> collection) {
            return join(collection, "[", "]", active);
        }
        if (value instanceof Object[] array) {
            return join(java.util.Arrays.asList(array), "[", "]", active);
        }
        return str(value);
    }

    private static Set<Object> enter(@Nullable Set<Object> active, Object container) {
        Set<Object> entered = active != null ? active : Collections.newSetFromMap(new IdentityHashMap<>());
        entered.add(container);
        return entered;
    }

    private static String join(Iterable<?> elements, String open, String close, @Nullable Set<Object> active) {
        if (active != null && active.contains(elements)) {
            return open + "..." + close;
        }
        Set<Object> entered = enter(active, elements);
        try {
            StringBuilder out = new StringBuilder(open);
            boolean first = true;
            for (Object element : elements) {
                if (!first) {
                    out.append(", ");
                }
                out.append(repr(element, entered));
                first = false;
            }
            return out.append(close).toString();
        } finally {
            entered.remove(elements);
        }
    }

    /**
     * A string as Python's repr spells it: single quotes unless the string contains a single quote and
     * no double quote, backslash escapes for the quote, the backslash, {@code \\n}, {@code \\r} and {@code \\t},
     * and {@code \\xhh}, {@code \\uhhhh} or {@code \\Uhhhhhhhh} for every other character
     * {@code str.isprintable()} rejects.
     */
    private static String quote(String string) {
        char quote = string.indexOf('\'') >= 0 && string.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder().append(quote);
        int i = 0;
        while (i < string.length()) {
            int c = string.codePointAt(i);
            i += Character.charCount(c);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c == quote) {
                        out.append('\\').append((char) c);
                    } else if (isPrintable(c)) {
                        out.appendCodePoint(c);
                    } else if (c <= 0xff) {
                        out.append(String.format("\\x%02x", c));
                    } else if (c <= 0xffff) {
                        out.append(String.format("\\u%04x", c));
                    } else {
                        out.append(String.format("\\U%08x", c));
                    }
                }
            }
        }
        return out.append(quote).toString();
    }

    /**
     * Python's {@code str.isprintable} for one character: the ASCII space, and no other separator, control,
     * format, surrogate, private-use or unassigned character.
     */
    private static boolean isPrintable(int codePoint) {
        if (codePoint < 0x7f) {
            return codePoint >= 0x20;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
                 Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SPACE_SEPARATOR -> false;
            default -> true;
        };
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
     * Python's {@code max(left, right)} on floats: the first argument unless the second is strictly
     * greater, so a NaN or a zero of the other sign does not replace it as with {@link Math#max}.
     *
     * @param left  The first argument
     * @param right The second argument
     * @return {@code max(left, right)}
     */
    public static double max(double left, double right) {
        return right > left ? right : left;
    }

    /**
     * Python's {@code min(left, right)} on floats: the first argument unless the second is strictly
     * smaller, so a NaN or a zero of the other sign does not replace it as with {@link Math#min}.
     *
     * @param left  The first argument
     * @param right The second argument
     * @return {@code min(left, right)}
     */
    public static double min(double left, double right) {
        return right < left ? right : left;
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
     * Invokes a method of the Python object of a compiled body and converts its result: the call a
     * compiled body makes to a method of its class that the stub does not declare, or that runs
     * its interceptors on the Python object.
     *
     * @param self      The Python object
     * @param name      The method
     * @param arguments The arguments
     * @param converter The conversion of the result to the Java type the body uses
     * @return The converted result
     */
    public static @Nullable Object invoke(org.graalvm.polyglot.Value self, String name, Object[] arguments, PolyglotValueConverter<?> converter) {
        return converter.convert(PythonInvocation.invokePythonMethod(self, name, arguments));
    }

    /**
     * The result of a Python method a compiled body calls whose return hint is a primitive
     * ({@code int}, {@code float} or {@code bool}). Return hints are trusted, and checked for
     * None: a None the primitive cannot hold fails as a TypeError naming the method and its hint,
     * not as the NullPointerException of the conversion.
     *
     * @param result The result of the call
     * @param method The method, as {@code Class.method}
     * @param hint   The Python name of the hinted type
     * @return The result, which is not None
     * @throws IllegalStateException When the result is None, as a TypeError
     */
    public static org.graalvm.polyglot.Value returned(org.graalvm.polyglot.Value result, String method, String hint) {
        if (result.isNull()) {
            throw new PythonError(TYPE_ERROR, method + "() returned None but is hinted to return " + hint);
        }
        return result;
    }

    /**
     * An attribute of a Python object a compiled body reads whose hint is a primitive
     * ({@code int}, {@code float} or {@code bool}): a property's getter, a hinted class attribute or
     * an attribute {@code __init__} assigns. The hint is trusted, and checked for None as
     * {@link #returned(org.graalvm.polyglot.Value, String, String)} checks a result.
     *
     * @param value     The value of the attribute
     * @param attribute The attribute, as {@code Class.attribute}
     * @param hint      The Python name of the hinted type
     * @return The value, which is not None
     * @throws IllegalStateException When the value is None, as a TypeError
     */
    public static org.graalvm.polyglot.Value hinted(org.graalvm.polyglot.Value value, String attribute, String hint) {
        if (value.isNull()) {
            throw new PythonError(TYPE_ERROR, attribute + " is None but is hinted as " + hint);
        }
        return value;
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

    // ---------------------------------------------------------------- collections

    /**
     * @param elements The elements
     * @param <T> The element type the caller expects
     * @return A Python list: a mutable list of the elements
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> list(Object... elements) {
        return (List<T>) new ArrayList<>(Arrays.asList(elements));
    }

    /**
     * @param values The values, a Java collection or any other iterable
     * @param <T> The element type
     * @return A Python list of the values: {@code list(values)}
     */
    public static <T> List<T> copyOfList(Iterable<? extends T> values) {
        List<T> list = values instanceof Collection<?> collection ? new ArrayList<>(collection.size()) : new ArrayList<>();
        for (T value : values) {
            list.add(value);
        }
        return list;
    }

    /**
     * @param values The values, a Java collection or any other iterable
     * @param <T> The element type
     * @return A Python set of the values, in iteration order: {@code set(values)}
     */
    public static <T> Set<T> copyOfSet(Iterable<? extends T> values) {
        Set<T> set = new LinkedHashSet<>();
        for (T value : values) {
            requireHashable(value);
            set.add(value);
        }
        return set;
    }

    /**
     * @param entries The entries
     * @param <K> The key type
     * @param <V> The value type
     * @return A Python dict of the entries, in iteration order: {@code {**entries}}
     */
    public static <K, V> Map<K, V> copyOfMap(Map<? extends K, ? extends V> entries) {
        return new LinkedHashMap<>(entries);
    }

    /**
     * @param map     A dict under construction
     * @param entries The entries to add, later ones winning as in a dict literal
     * @param <K> The key type
     * @param <V> The value type
     * @return The dict
     */
    public static <K, V> Map<K, V> putAll(Map<K, V> map, Map<? extends K, ? extends V> entries) {
        map.putAll(entries);
        return map;
    }

    /**
     * @param map   A dict under construction
     * @param key   The key
     * @param value The value
     * @param <K> The key type
     * @param <V> The value type
     * @return The dict
     */
    public static <K, V> Map<K, V> put(Map<K, V> map, K key, V value) {
        requireHashable(key);
        map.put(key, value);
        return map;
    }

    /**
     * @param elements The elements
     * @param <T> The element type the caller expects
     * @return A Python tuple: an immutable list of the elements, None among them, that {@link #repr(Object)} renders as a tuple
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> tuple(Object... elements) {
        return (List<T>) new Tuple(elements.clone());
    }

    /**
     * @param values The values, a Java collection or any other iterable
     * @param <T> The element type
     * @return A Python tuple of the values: {@code tuple(values)}
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> copyOfTuple(Iterable<? extends T> values) {
        return (List<T>) new Tuple(copyOfList(values).toArray());
    }

    /**
     * @param elements The elements, in order
     * @param <T> The element type the caller expects
     * @return A Python set: an insertion-ordered set of the elements
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> set(Object... elements) {
        for (Object element : elements) {
            requireHashable(element);
        }
        return (Set<T>) new LinkedHashSet<>(Arrays.asList(elements));
    }

    /**
     * @param keysAndValues The keys and values, alternating
     * @param <K> The key type the caller expects
     * @param <V> The value type the caller expects
     * @return A Python dict: an insertion-ordered map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> map(Object... keysAndValues) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            requireHashable(keysAndValues[i]);
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return (Map<K, V>) map;
    }

    /**
     * @param list  A list
     * @param index A Python index, negative from the end
     * @return {@code list[index]}
     * @throws IndexOutOfBoundsException When the index is out of range, as Python raises IndexError
     */
    public static Object at(List<?> list, long index) {
        int size = list.size();
        long position = index < 0 ? index + size : index;
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("list index out of range");
        }
        return list.get((int) position);
    }

    /**
     * @param text  A string
     * @param index A Python index, negative from the end
     * @return {@code text[index]}, a one-character string
     * @throws IndexOutOfBoundsException When the index is out of range, as Python raises IndexError
     */
    public static String at(String text, long index) {
        // Python indexes code points, Java's charAt UTF-16 units
        int length = text.codePointCount(0, text.length());
        long position = index < 0 ? index + length : index;
        if (position < 0 || position >= length) {
            throw new IndexOutOfBoundsException("string index out of range");
        }
        int offset = text.offsetByCodePoints(0, (int) position);
        return text.substring(offset, text.offsetByCodePoints(offset, 1));
    }

    /**
     * @param list  A list
     * @param value The value
     * @return {@code list.append(value)}: None, where the Java method answers a boolean
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Object append(List<?> list, @Nullable Object value) {
        ((List<Object>) list).add(value);
        return null;
    }

    /**
     * @param set   A set
     * @param value The value
     * @return {@code set.add(value)}: None, where the Java method answers a boolean
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Object add(Set<?> set, @Nullable Object value) {
        requireHashable(value);
        ((Set<Object>) set).add(value);
        return null;
    }

    /**
     * @param list  A list
     * @param index A Python index, negative from the end
     * @param value The value
     * @throws IndexOutOfBoundsException When the index is out of range, as Python raises IndexError
     */
    @SuppressWarnings("unchecked")
    public static void setAt(List<?> list, long index, @Nullable Object value) {
        int size = list.size();
        long position = index < 0 ? index + size : index;
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("list assignment index out of range");
        }
        ((List<Object>) list).set((int) position, value);
    }

    /**
     * @param value A Java collection given to a parameter hinted as a Python list, set or dict
     * @param <T>   The type of the collection
     * @return A copy, as the bridge hands Python one: the caller's collection stays untouched, and
     * an unmodifiable one can be added to
     */
    @SuppressWarnings("unchecked")
    public static <T> T copy(T value) {
        if (value instanceof Tuple) {
            return value;
        }
        if (value instanceof List<?> list) {
            return (T) new ArrayList<>(list);
        }
        if (value instanceof Set<?> set) {
            return (T) new LinkedHashSet<>(set);
        }
        if (value instanceof Map<?, ?> map) {
            return (T) new LinkedHashMap<>(map);
        }
        return value;
    }

    /**
     * @param map   A map
     * @param key   A key
     * @param value The value
     */
    @SuppressWarnings("unchecked")
    public static void setItem(Map<?, ?> map, @Nullable Object key, @Nullable Object value) {
        requireHashable(key);
        ((Map<Object, Object>) map).put(key, value);
    }

    /**
     * @param map A map
     * @param key A key
     * @return {@code map[key]}
     * @throws NoSuchElementException When the key is absent, as Python raises KeyError
     */
    public static @Nullable Object item(Map<?, ?> map, @Nullable Object key) {
        requireHashable(key);
        if (!containsKey(map, key)) {
            throw new NoSuchElementException("KeyError: " + str(key));
        }
        return map.get(key);
    }

    /**
     * @param map          A map
     * @param key          A key
     * @param defaultValue The value when the key is absent
     * @return {@code map.get(key, default)}
     */
    public static @Nullable Object get(Map<?, ?> map, @Nullable Object key, @Nullable Object defaultValue) {
        requireHashable(key);
        return containsKey(map, key) ? map.get(key) : defaultValue;
    }

    /**
     * @param value A string, list, set, map or tuple
     * @return {@code len(value)}
     */
    public static long len(@Nullable Object value) {
        if (value instanceof CharSequence text) {
            String string = text.toString();
            return string.codePointCount(0, string.length());
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        int arrayLength = arrayLength(value);
        if (arrayLength >= 0) {
            return arrayLength;
        }
        throw new ClassCastException("object of type " + typeName(value) + " has no len()");
    }

    /**
     * @param container A string, collection or map
     * @param element   The element, a substring for a string, a key for a map
     * @return {@code element in container}
     */
    public static boolean contains(@Nullable Object container, @Nullable Object element) {
        if (container instanceof CharSequence text) {
            if (element == null) {
                throw new PythonError(TYPE_ERROR, "'in <string>' requires string as left operand, not NoneType");
            }
            return text.toString().contains(element.toString());
        }
        if (container instanceof Set<?> set) {
            requireHashable(element);
            try {
                return set.contains(element);
            } catch (NullPointerException e) {
                // an immutable Java set refuses to look for null: it holds none, so None is not in it
                if (element == null) {
                    return false;
                }
                throw e;
            }
        }
        if (container instanceof Collection<?> collection) {
            for (Object candidate : collection) {
                if (same(candidate, element)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof Map<?, ?> map) {
            requireHashable(element);
            return containsKey(map, element);
        }
        throw new ClassCastException("argument of type " + typeName(container) + " is not iterable");
    }

    private static boolean containsKey(Map<?, ?> map, @Nullable Object key) {
        try {
            return map.containsKey(key);
        } catch (NullPointerException e) {
            // an immutable Java map refuses to look for a null key: it holds none
            if (key == null) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Python's {@code ==} as far as Java's {@code equals} differs from it on the values of a compiled body:
     * a tuple equals no list and a list no tuple, also as elements of lists, tuples and dict values.
     */
    private static boolean same(@Nullable Object left, @Nullable Object right) {
        if (left == right) {
            return true;
        }
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if ((left instanceof Tuple) != (right instanceof Tuple) || leftList.size() != rightList.size()) {
                return false;
            }
            java.util.Iterator<?> rightElements = rightList.iterator();
            for (Object leftElement : leftList) {
                if (!same(leftElement, rightElements.next())) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            if (leftMap.size() != rightMap.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : leftMap.entrySet()) {
                if (!containsKey(rightMap, entry.getKey()) || !same(entry.getValue(), rightMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(left, right);
    }

    /**
     * Requires a dict key or a set element Python can hash: a list, set or dict cannot be one (nor a tuple
     * holding one), which keeps a list from finding the key of an equal tuple.
     *
     * @param key A dict key or a set element
     * @throws ClassCastException For a list, set or dict, as Python raises TypeError
     */
    private static void requireHashable(@Nullable Object key) {
        if (key instanceof Tuple tuple) {
            for (Object element : tuple) {
                requireHashable(element);
            }
        } else if (key instanceof Collection<?> || key instanceof Map<?, ?>) {
            throw new ClassCastException("unhashable type: '" + typeName(key) + "'");
        }
    }

    /**
     * The length of an array of any component type, or -1 for a value that is not an array.
     */
    private static int arrayLength(@Nullable Object value) {
        if (value instanceof Object[] objects) {
            return objects.length;
        }
        if (value instanceof byte[] bytes) {
            return bytes.length;
        }
        if (value instanceof int[] ints) {
            return ints.length;
        }
        if (value instanceof long[] longs) {
            return longs.length;
        }
        if (value instanceof double[] doubles) {
            return doubles.length;
        }
        if (value instanceof boolean[] booleans) {
            return booleans.length;
        }
        if (value instanceof char[] chars) {
            return chars.length;
        }
        if (value instanceof short[] shorts) {
            return shorts.length;
        }
        if (value instanceof float[] floats) {
            return floats.length;
        }
        return -1;
    }

    /**
     * The Python spelling of a value's type for a message: the builtin kind of a converted value,
     * {@code NoneType} for null, {@code object} for anything else.
     */
    private static String typeName(@Nullable Object value) {
        if (value == null) {
            return "NoneType";
        }
        if (value instanceof CharSequence) {
            return "str";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof Double || value instanceof Float) {
            return "float";
        }
        if (value instanceof Number) {
            return "int";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        if (value instanceof Set<?>) {
            return "set";
        }
        if (value instanceof Map<?, ?>) {
            return "dict";
        }
        return "object";
    }

    // ---------------------------------------------------------------- strings

    /**
     * @param text A string
     * @return {@code text.strip()}: the string without leading and trailing whitespace, as Python classifies it
     */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end) {
            int codePoint = text.codePointAt(start);
            if (!isSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = text.codePointBefore(end);
            if (!isSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return start == 0 && end == text.length() ? text : text.substring(start, end);
    }

    /**
     * Python's {@code str.isspace} for one character: the Unicode separators (including the no-break spaces Java's
     * {@link Character#isWhitespace(int)} leaves out), the ASCII controls Python counts, and the next line.
     */
    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
    }

    /**
     * @param text A string
     * @param <T> The element type the caller expects
     * @return {@code text.split()}: the runs of non-whitespace, no empty strings
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> split(String text) {
        List<Object> parts = new ArrayList<>();
        int start = -1;
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            if (isSpace(codePoint)) {
                if (start >= 0) {
                    parts.add(text.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
            i += Character.charCount(codePoint);
        }
        if (start >= 0) {
            parts.add(text.substring(start));
        }
        return (List<T>) parts;
    }

    /**
     * @param text      A string
     * @param separator The separator, as a plain string
     * @param <T> The element type the caller expects
     * @return {@code text.split(separator)}: every occurrence separates, empty strings kept
     * @throws IllegalArgumentException On an empty separator, as Python raises ValueError
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> split(String text, String separator) {
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("empty separator");
        }
        List<Object> parts = new ArrayList<>();
        int start = 0;
        while (true) {
            int next = text.indexOf(separator, start);
            if (next < 0) {
                parts.add(text.substring(start));
                return (List<T>) parts;
            }
            parts.add(text.substring(start, next));
            start = next + separator.length();
        }
    }

    /**
     * @param separator The separator
     * @param parts     The strings
     * @return {@code separator.join(parts)}
     * @throws ClassCastException When a part is not a string, as Python raises TypeError
     */
    public static String join(String separator, Iterable<?> parts) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Object part : parts) {
            if (!(part instanceof String)) {
                throw new ClassCastException("sequence item is not a str: " + str(part));
            }
            if (!first) {
                out.append(separator);
            }
            out.append((String) part);
            first = false;
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- numbers

    /**
     * @param value A Python value
     * @return {@code int(value)}: a float truncates towards zero, a string is parsed
     * @throws IllegalArgumentException When a string is not an integer, as Python raises ValueError
     */
    public static long toInt(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool.booleanValue() ? 1 : 0;
        }
        if (value instanceof Double || value instanceof Float) {
            return toInt(((Number) value).doubleValue());
        }
        if (value instanceof BigInteger big) {
            return big.longValueExact();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            String digits = asciiDigits(strip(text.toString()));
            if (!PYTHON_INT.matcher(digits).matches()) {
                throw new IllegalArgumentException("invalid literal for int() with base 10: '" + text + "'");
            }
            try {
                return Long.parseLong(digits.replace("_", ""));
            } catch (NumberFormatException e) {
                // the literal is an int, one no long holds: an int a compiled body cannot represent
                throw new ArithmeticException("int('" + text + "') is outside the range of a long");
            }
        }
        throw new ClassCastException("int() argument must be a string or a number, not " + typeName(value));
    }

    /**
     * @param value A float
     * @return {@code int(value)}: the float truncated towards zero
     * @throws IllegalArgumentException On a NaN, as Python raises ValueError
     * @throws ArithmeticException On an infinity or a value outside the range of a long, where Python would
     * raise OverflowError or answer an integer no long holds
     */
    public static long toInt(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("cannot convert float NaN to integer");
        }
        if (Double.isInfinite(value)) {
            throw new ArithmeticException("cannot convert float infinity to integer");
        }
        if (value >= 0x1p63 || value < -0x1p63) {
            throw new ArithmeticException("int(" + str(value) + ") is outside the range of a long");
        }
        return (long) value;
    }

    /**
     * @param value A Python value
     * @return {@code float(value)}
     * @throws IllegalArgumentException When a string is not a float, as Python raises ValueError
     */
    public static double toFloat(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool.booleanValue() ? 1.0 : 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof CharSequence text) {
            String trimmed = strip(text.toString());
            try {
                return switch (trimmed.toLowerCase(java.util.Locale.ROOT)) {
                    case "inf", "+inf", "infinity", "+infinity" -> Double.POSITIVE_INFINITY;
                    case "-inf", "-infinity" -> Double.NEGATIVE_INFINITY;
                    case "nan", "+nan", "-nan" -> Double.NaN;
                    default -> parseFloat(trimmed);
                };
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("could not convert string to float: '" + text + "'", e);
            }
        }
        throw new ClassCastException("float() argument must be a string or a number, not " + typeName(value));
    }

    /**
     * A float literal as Python's float() reads it: digits with single underscores between them, no
     * Java suffix ({@code 1d}, {@code 2f}) and no hexadecimal form, which Double.parseDouble accepts.
     */
    private static double parseFloat(String text) {
        String digits = asciiDigits(text);
        int exponent = Math.max(digits.indexOf('e'), digits.indexOf('E'));
        String mantissa = exponent < 0 ? digits : digits.substring(0, exponent);
        if (!isPythonMantissa(mantissa)
            || exponent >= 0 && !PYTHON_INT.matcher(digits.substring(exponent + 1)).matches()) {
            throw new NumberFormatException(text);
        }
        return Double.parseDouble(digits.replace("_", ""));
    }

    /**
     * Whether the text is the mantissa of a Python float literal: an optional sign, then digits, a
     * point or both, with digits on at least one side of the point.
     */
    private static boolean isPythonMantissa(String text) {
        String unsigned = text.startsWith("+") || text.startsWith("-") ? text.substring(1) : text;
        int point = unsigned.indexOf('.');
        String whole = point < 0 ? unsigned : unsigned.substring(0, point);
        String fraction = point < 0 ? "" : unsigned.substring(point + 1);
        return !(whole.isEmpty() && fraction.isEmpty())
            && (whole.isEmpty() || PYTHON_DIGITS.matcher(whole).matches())
            && (fraction.isEmpty() || PYTHON_DIGITS.matcher(fraction).matches());
    }

    /**
     * The text with every Unicode decimal digit, which Python's int() and float() accept, as its ASCII digit.
     */
    private static String asciiDigits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (codePoint > 0x7f && Character.isDigit(codePoint)) {
                out.append((char) ('0' + Character.digit(codePoint, 10)));
            } else {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString();
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
     * A Python tuple: an immutable list whose rendering keeps the parentheses. Java's {@code equals} holds it
     * equal to any list of the same elements, as the List contract requires; the helpers of a compiled body
     * compare as Python does instead, where a tuple equals no list ({@link #same}), and take no list as a
     * dict key or a set element ({@link #requireHashable}).
     */
    private static final class Tuple extends AbstractList<Object> implements RandomAccess {
        private final Object[] elements;

        private Tuple(Object[] elements) {
            this.elements = elements;
        }

        // a tuple equals any list of equal elements, as a Java list does; the helpers that compare as
        // Python does (contains, the keys of a dict) tell a tuple from a list themselves, see same()
        @Override
        public boolean equals(@Nullable Object o) {
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public Object get(int index) {
            return elements[index];
        }

        @Override
        public int size() {
            return elements.length;
        }
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
