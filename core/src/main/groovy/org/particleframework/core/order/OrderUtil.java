/*
 * Copyright 2017 original authors
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
package org.particleframework.core.order;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Apply the {@link Ordered} interface to lists or arrays
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class OrderUtil {

    /**
     * Sort the given list
     *
     * @param list The list to sort
     */
    public static void sort(List<?> list) {
        list.sort((o1, o2) -> {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return (order1 < order2) ? -1 : (order1 > order2) ? 1 : 0;
        });
    }

    /**
     * Sort the given list
     *
     * @param list The list to sort
     */
    public static Stream sort(Stream<?> list) {
        return list.sorted((o1, o2) -> {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return (order1 < order2) ? -1 : (order1 > order2) ? 1 : 0;
        });
    }

    /**
     * Sort the given list
     *
     * @param list The list to sort
     */
    public static void reverseSort(List<?> list) {
        list.sort(Collections.reverseOrder((o1, o2) -> {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return (order1 < order2) ? -1 : (order1 > order2) ? 1 : 0;
        }));
    }
    /**
     * Sort the given array
     *
     * @param objects The array to sort
     */
    public static void sort(Ordered...objects) {
        Arrays.sort(objects,(o1, o2) -> {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return (order1 < order2) ? -1 : (order1 > order2) ? 1 : 0;
        });
    }

    /**
     * Sort the given array
     *
     * @param objects The array to sort
     */
    public static void sort(Object...objects) {
        Arrays.sort(objects,(o1, o2) -> {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return (order1 < order2) ? -1 : (order1 > order2) ? 1 : 0;
        });
    }

    private static int getOrder(Object o) {
        if(o instanceof Ordered) {
            return getOrder((Ordered)o);
        }
        return Ordered.LOWEST_PRECEDENCE;
    }
    private static int getOrder(Ordered o) {
        return o.getOrder();
    }
}
