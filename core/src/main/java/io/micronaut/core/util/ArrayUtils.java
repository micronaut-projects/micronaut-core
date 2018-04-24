/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.core.util;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

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
        int aLen = a.length;
        int bLen = b.length;

        if (bLen == 0) {
            return a;
        }
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
        } else {
            List<Object> list = Arrays.asList(array);
            return CollectionUtils.toString(delimiter, list);
        }
    }
}
