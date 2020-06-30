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
package io.micronaut.core.order;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Apply the {@link Ordered} interface to lists or arrays.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class OrderUtil {

    /**
     * Provide a comparator for collections.
     */
    public static final Comparator<Object> COMPARATOR = (o1, o2) -> {
        int order1 = getOrder(o1);
        int order2 = getOrder(o2);
        return Integer.compare(order1, order2);
    };

    /**
     * Provide a comparator, in reversed order, for collections.
     */
    public static final Comparator<Object> REVERSE_COMPARATOR = Collections.reverseOrder(COMPARATOR);

    /**
     * Sort the given list.
     *
     * @param list The list to sort
     */
    public static void sort(List<?> list) {
        list.sort(COMPARATOR);
    }

    /**
     * Sort the given list.
     *
     * @param list The list to sort
     * @param <T>  The stream generic type
     * @return The sorted stream
     */
    public static <T> Stream<T> sort(Stream<T> list) {
        return list.sorted(COMPARATOR);
    }

    /**
     * Sort the given list.
     *
     * @param list The list to sort
     */
    public static void reverseSort(List<?> list) {
        list.sort(REVERSE_COMPARATOR);
    }

    /**
     * Sort the given array in reverse order.
     *
     * @param array The array to sort
     */
    public static void reverseSort(Object[] array) {
        Arrays.sort(array, REVERSE_COMPARATOR);
    }

    /**
     * Sort the given array.
     *
     * @param objects The array to sort
     */
    public static void sort(Ordered... objects) {
        Arrays.sort(objects, COMPARATOR);
    }

    /**
     * Sort the given array.
     *
     * @param objects The array to sort
     */
    public static void sort(Object[] objects) {
        Arrays.sort(objects, COMPARATOR);
    }

    private static int getOrder(Object o) {
        if (o instanceof Ordered) {
            return getOrder((Ordered) o);
        }
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static int getOrder(Ordered o) {
        return o.getOrder();
    }
}
