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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Array;
import java.util.*;

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
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

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
            _index = a.length > 0 ? a.length : -1;
        }

        @Override
        public boolean hasNext() {
            return _index > -1;
        }

        @Override
        public T next() {
            if (_index >= -1) {
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
