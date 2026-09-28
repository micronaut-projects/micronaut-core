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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
     * The attribute of a Python object holding the delegate through which the Python side of a
     * compiled method calls its Java body.
     */
    public static final String COMPILED_MEMBER = "__micronaut_compiled__";

    private static final ConcurrentHashMap<String, LongAdder> ENTRIES = new ConcurrentHashMap<>();

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
     * Binds a Python object to the delegate of its stub: the compiled methods of the object run as
     * Java from then on, see {@code apply_delegation}.
     *
     * @param pythonObject The Python object of a stub
     * @param delegate     The delegate, a plain Java object calling the stub's compiled methods
     */
    public static void bindCompiled(org.graalvm.polyglot.Value pythonObject, Object delegate) {
        PythonContextRuntime.helper(pythonObject.getContext(), "__micronaut_set_instance_property")
            .execute(pythonObject, COMPILED_MEMBER, delegate);
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
     * @param value An element of a collection, boxed by the host boundary
     * @return The value as a Python int
     * @throws ClassCastException When the element is not a number, as Python raises TypeError
     */
    public static long toLong(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        throw new ClassCastException("not an int: " + str(value));
    }

    /**
     * @param value An element of a collection, boxed by the host boundary
     * @return The value as a Python float
     * @throws ClassCastException When the element is not a number, as Python raises TypeError
     */
    public static double toDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
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
        map.put(key, value);
        return map;
    }

    /**
     * @param elements The elements
     * @param <T> The element type the caller expects
     * @return A Python tuple: an immutable list of the elements
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> tuple(Object... elements) {
        return (List<T>) List.of(elements);
    }

    /**
     * @param elements The elements, in order
     * @param <T> The element type the caller expects
     * @return A Python set: an insertion-ordered set of the elements
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> set(Object... elements) {
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
        ((Map<Object, Object>) map).put(key, value);
    }

    /**
     * @param map A map
     * @param key A key
     * @return {@code map[key]}
     * @throws NoSuchElementException When the key is absent, as Python raises KeyError
     */
    public static Object item(Map<?, ?> map, @Nullable Object key) {
        if (!map.containsKey(key)) {
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
        return map.containsKey(key) ? map.get(key) : defaultValue;
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
            return element != null && text.toString().contains(element.toString());
        }
        if (container instanceof Collection<?> collection) {
            return collection.contains(element);
        }
        if (container instanceof Map<?, ?> map) {
            return map.containsKey(element);
        }
        throw new ClassCastException("argument of type " + typeName(container) + " is not iterable");
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
     * @return {@code text.strip()}: the string without leading and trailing whitespace
     */
    public static String strip(String text) {
        return text.strip();
    }

    /**
     * @param text A string
     * @param <T> The element type the caller expects
     * @return {@code text.split()}: the runs of non-whitespace, no empty strings
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> split(String text) {
        List<Object> parts = new ArrayList<>();
        for (String part : text.strip().split("\\s+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
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
            return bool ? 1 : 0;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("cannot convert float " + str(d) + " to integer");
            }
            return (long) d;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString().strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid literal for int() with base 10: '" + text + "'", e);
            }
        }
        throw new ClassCastException("int() argument must be a string or a number, not " + typeName(value));
    }

    /**
     * @param value A Python value
     * @return {@code float(value)}
     * @throws IllegalArgumentException When a string is not a float, as Python raises ValueError
     */
    public static double toFloat(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof CharSequence text) {
            String trimmed = text.toString().strip();
            try {
                return switch (trimmed.toLowerCase(java.util.Locale.ROOT)) {
                    case "inf", "+inf", "infinity" -> Double.POSITIVE_INFINITY;
                    case "-inf", "-infinity" -> Double.NEGATIVE_INFINITY;
                    case "nan", "+nan", "-nan" -> Double.NaN;
                    default -> Double.parseDouble(trimmed);
                };
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("could not convert string to float: '" + text + "'", e);
            }
        }
        throw new ClassCastException("float() argument must be a string or a number, not " + typeName(value));
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
