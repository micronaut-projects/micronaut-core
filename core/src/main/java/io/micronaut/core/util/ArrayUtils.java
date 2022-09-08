/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ReflectionUtils;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Utility methods for working with arrays.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ArrayUtils {

    /**
     * An empty object array.
     */
    @UsedByGeneratedCode
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /**
     * An empty boolean array.
     */
    public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];

    /**
     * An empty byte array.
     */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * An empty char array.
     */
    public static final char[] EMPTY_CHAR_ARRAY = new char[0];

    /**
     * An empty int array.
     */
    public static final int[] EMPTY_INT_ARRAY = new int[0];

    /**
     * An empty double array.
     */
    public static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    /**
     * An empty long array.
     */
    public static final long[] EMPTY_LONG_ARRAY = new long[0];

    /**
     * An empty float array.
     */
    public static final float[] EMPTY_FLOAT_ARRAY = new float[0];

    /**
     * An empty short array.
     */
    public static final short[] EMPTY_SHORT_ARRAY = new short[0];

    /**
     * Concatenate two arrays.
     *
     * @param a   The first array
     * @param b   The second array
     * @param <T> The array type
     * @return The concatenated array
     */
    public static <T> T[] concat(T[] a, T... b) {
        int bLen = b.length;

        if (bLen == 0) {
            return a;
        }
        int aLen = a.length;
        if (aLen == 0) {
            return b;
        }

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    /**
     * Concatenate two byte arrays.
     *
     * @param a   The first array
     * @param b   The second array
     * @return The concatenated array
     */
    public static byte[] concat(byte[] a, byte... b) {
        int bLen = b.length;

        if (bLen == 0) {
            return a;
        }
        int aLen = a.length;
        if (aLen == 0) {
            return b;
        }

        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    /**
     * Whether the given array is empty.
     *
     * @param array The array
     * @return True if it is
     */
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Whether the given array is not empty.
     *
     * @param array The array
     * @return True if it is
     */
    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }

    /**
     * Produce a string representation of the given array.
     *
     * @param array The array
     * @return The string representation
     */
    public static String toString(@Nullable Object[] array) {
        String delimiter = ",";
        return toString(delimiter, array);
    }

    /**
     * Produce a string representation of the given array.
     *
     * @param delimiter The delimiter
     * @param array     The array
     * @return The string representation
     */
    public static String toString(String delimiter, @Nullable Object[] array) {
        if (isEmpty(array)) {
            return "";
        }
        List<Object> list = Arrays.asList(array);
        return CollectionUtils.toString(delimiter, list);
    }

    /**
     * Produce an iterator for the given array.
     * @param array The array
     * @param <T> The array type
     * @return The iterator
     */
    public static <T> Iterator<T> iterator(T...array) {
        if (isNotEmpty(array)) {
            return new ArrayIterator<>(array);
        } else {
            return Collections.emptyIterator();
        }
    }

    /**
     * Produce an iterator for the given array.
     * @param array The array
     * @param <T> The array type
     * @return The iterator
     */
    public static <T> Iterator<T> reverseIterator(T...array) {
        if (isNotEmpty(array)) {
            return new ReverseArrayIterator<>(array);
        } else {
            return Collections.emptyIterator();
        }
    }

    /**
     * Returns an array containing all of the elements in this collection, using the provided generator function to allocate the returned array.
     *
     * @param collection The collection
     * @param createArrayFn The function to create the array
     * @param <T> The type of the array
     * @return The array
     */
    public static <T> T[] toArray(Collection<T> collection, IntFunction<T[]> createArrayFn) {
        T[] array = createArrayFn.apply(collection.size());
        return collection.toArray(array);
    }

    /**
     * Returns an array containing all of the elements in this collection, using the item class.
     *
     * @param collection The collection
     * @param arrayItemClass The array item class
     * @param <T> The type of the array
     * @return The array
     * @since 3.0
     */
    public static <T> T[] toArray(Collection<T> collection, Class<T> arrayItemClass) {
        return (T[]) collection.toArray((Object[]) Array.newInstance(arrayItemClass, collection.size()));
    }

    /**
     * Converts a primitive array to the equivalent wrapper such as int[] to Integer[].
     * @param primitiveArray The primitive array
     * @return The primitive array wrapper.
     * @since 3.0.0
     */
    public static Object[] toWrapperArray(final Object primitiveArray) {
        Objects.requireNonNull(primitiveArray, "Primitive array cannot be null");
        final Class<?> cls = primitiveArray.getClass();
        Class<?> componentType = cls.getComponentType();
        if (!cls.isArray() || !componentType.isPrimitive()) {
            throw new IllegalArgumentException(
                    "Only primitive arrays are supported");
        }
        final int length = Array.getLength(primitiveArray);
        Object[] arr = (Object[]) Array.newInstance(ReflectionUtils.getWrapperType(componentType), length);
        for (int i = 0; i < length; i++) {
            arr[i] = Array.get(primitiveArray, i);
        }
        return arr;
    }

    /**
     * Converts a primitive wrapper array to the equivalent primitive array such as Integer[] to int[].
     * @param wrapperArray The wrapper array
     * @return The primitive array.
     * @since 3.0.0
     */
    public static Object toPrimitiveArray(final Object[] wrapperArray) {
        Objects.requireNonNull(wrapperArray, "Wrapper array cannot be null");
        final Class<?> cls = wrapperArray.getClass();
        Class<?> ct = cls.getComponentType();
        Class<?> componentType = ReflectionUtils.getPrimitiveType(ct);
        if (componentType == ct) {
            return wrapperArray;
        } else {

            if (!cls.isArray() || !componentType.isPrimitive()) {
                throw new IllegalArgumentException(
                        "Only primitive arrays are supported");
            }
            final int length = wrapperArray.length;
            Object arr = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Array.set(arr, i, wrapperArray[i]);
            }
            return arr;
        }
    }

    /**
     * Reverse the order of items in array.
     *
     * @param input The array
     * @param <T>   The array type
     * @since 4.0.0
     */
    public static <T> void reverse(T[] input) {
        final int len = input.length;
        if (len > 1) {
            for (int i = 0; i < len / 2; i++) {
                T temp = input[i];
                final int pos = len - i - 1;
                input[i] = input[pos];
                input[pos] = temp;
            }
        }
    }

    /**
     * Iterator implementation used to efficiently expose contents of an
     * Array as read-only iterator.
     *
     * @param <T> the type
     */
    private static final class ArrayIterator<T> implements Iterator<T>, Iterable<T> {

        private final T[] _a;
        private int _index;

        private ArrayIterator(T[] a) {
            _a = a;
            _index = 0;
        }

        @Override
        public boolean hasNext() {
            return _index < _a.length;
        }

        @Override
        public T next() {
            if (_index >= _a.length) {
                throw new NoSuchElementException();
            }
            return _a[_index++];
        }

        @Override public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override public Iterator<T> iterator() {
            return this;
        }
    }

    /**
     * Iterator implementation used to efficiently expose contents of an
     * Array as read-only iterator.
     *
     * @param <T> the type
     */
    private static final class ReverseArrayIterator<T> implements Iterator<T>, Iterable<T> {

        private final T[] _a;
        private int _index;

        private ReverseArrayIterator(T[] a) {
            _a = a;
            _index = a.length > 0 ? a.length - 1 : -1;
        }

        @Override
        public boolean hasNext() {
            return _index > -1;
        }

        @Override
        public T next() {
            if (_index <= -1) {
                throw new NoSuchElementException();
            }
            return _a[_index--];
        }

        @Override public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override public Iterator<T> iterator() {
            return this;
        }
    }
}
