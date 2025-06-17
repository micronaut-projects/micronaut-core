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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
     * Orders objects using {@link #getOrder(Object)}, i.e. objects that don't implement
     * {@link Ordered} and which are not {@link AnnotationMetadata} will be placed
     * non-deterministically in the final position.
     *
     * <p>
     * You probably want to use {@link #COMPARATOR_ZERO} instead, as with this comparator
     * no object can sort itself to the back of the collection.
     */
    // Keep as an anonymous class to avoid lambda overhead during the startup
    public static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(Object o1, Object o2) {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return Integer.compare(order1, order2);
        }
    };

    /**
     * Orders objects using {@link #getOrderWithDefaultPrecedence(Object, int)} using zero as the
     * default precedence.
     */
    // Keep as an anonymous class to avoid lambda overhead during the startup
    public static final Comparator<Object> COMPARATOR_ZERO = new Comparator<Object>() {
        @Override
        public int compare(Object o1, Object o2) {
            int order1 = getOrder(o1);
            int order2 = getOrder(o2);
            return Integer.compare(order1, order2);
        }
    };

    /**
     * The comparator of elements implementing {@link Ordered}.
     */
    // Keep as an anonymous class to avoid lambda overhead during the startup
    public static final Comparator<Ordered> ORDERED_COMPARATOR = new Comparator<Ordered>() {
        @Override
        public int compare(Ordered o1, Ordered o2) {
            return Integer.compare(o1.getOrder(), o2.getOrder());
        }
    };

    /**
     * The reverse comparator of elements implementing {@link Ordered}.
     */
    public static final Comparator<Ordered> REVERSE_ORDERED_COMPARATOR = Collections.reverseOrder(ORDERED_COMPARATOR);

    /**
     * Provide a comparator, in reversed order, for collections.
     *
     * <p>
     * You probably want to use {@link #REVERSE_COMPARATOR_ZERO} instead, as with this
     * comparator no object can sort itself to the front of the collection.
     */
    public static final Comparator<Object> REVERSE_COMPARATOR = Collections.reverseOrder(COMPARATOR);

    /**
     * Provide a comparator, in reversed order, for collections.
     */
    public static final Comparator<Object> REVERSE_COMPARATOR_ZERO = Collections.reverseOrder(COMPARATOR_ZERO);

    /**
     * Sort the given list using {@link #COMPARATOR}.
     *
     * <p>
     * You should probably not use this method. Prefer calling
     * {@code list.sort(OrderUtil.COMPARATOR_ZERO)} instead, which offers more intuitive behavior
     * for beans that don't expose an ordering.
     *
     * @param list The list to sort
     */
    public static void sort(List<?> list) {
        list.sort(COMPARATOR);
    }

    /**
     * Sort the given list.
     *
     * <p>
     * You should probably not use this method. Prefer calling
     * {@code stream.sorted(OrderUtil.COMPARATOR_ZERO)} instead, which offers more intuitive behavior
     * for beans that don't expose an ordering.
     *
     * @param list The list to sort
     * @param <T>  The stream generic type
     * @return The sorted stream
     */
    public static <T> Stream<T> sort(Stream<T> list) {
        return list.sorted(COMPARATOR);
    }

    /**
     * Sort the given stream.
     *
     * @param list The list to sort
     * @param <T>  The stream generic type
     * @return The sorted stream
     * @since 4.4.0
     */
    public static <T extends Ordered> Stream<T> sortOrdered(Stream<T> list) {
        return list.sorted(ORDERED_COMPARATOR);
    }

    /**
     * Sort the given list.
     *
     * @param list The list to sort
     * @param <T>  The type
     * @return The sorted collection
     * @since 4.4.0
     */
    public static <T extends Ordered> List<T> sortOrderedCollection(Collection<T> list) {
        var newList = new ArrayList<>(list);
        newList.sort(ORDERED_COMPARATOR);
        return newList;
    }

    /**
     * Sort the given list.
     *
     * @param list The list to sort
     * @param <T>  The type
     * @since 4.4.0
     */
    public static <T extends Ordered> void sortOrdered(List<T> list) {
        list.sort(ORDERED_COMPARATOR);
    }

    /**
     * Sort the given list.
     *
     * @param list The list to sort
     * @param <T>  The type
     * @since 4.4.0
     */
    public static <T extends Ordered> void reverseSortOrdered(List<T> list) {
        list.sort(REVERSE_ORDERED_COMPARATOR);
    }

    /**
     * Sort the given list.
     *
     * <p>
     * You should probably not use this method. Prefer calling
     * {@code list.sort(OrderUtil.REVERSE_COMPARATOR_ZERO)} instead, which offers more intuitive
     * behavior for beans that don't expose an ordering.
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
     * <p>
     * You should probably not use this method. Prefer calling
     * {@code Arrays.sort(objects, OrderUtil.COMPARATOR_ZERO)} instead, which offers more intuitive
     * behavior for beans that don't expose an ordering.
     *
     * @param objects The array to sort
     */
    public static void sort(Ordered... objects) {
        Arrays.sort(objects, COMPARATOR);
    }

    /**
     * Sort the given array.
     *
     * <p>
     * You should probably not use this method. Prefer calling
     * {@code Arrays.sort(objects, OrderUtil.COMPARATOR_ZERO)} instead, which offers more intuitive
     * behavior for beans that don't expose an ordering.
     *
     * @param objects The array to sort
     */
    public static void sort(Object[] objects) {
        Arrays.sort(objects, COMPARATOR);
    }

    /**
     * Get the order for the given bean instance or {@link AnnotationMetadata} object.
     *
     * <p>
     * You should probably not use this method. Prefer calling
     * {@link #getOrderWithDefaultPrecedence(Object, int)} with 0 instead, which offers more
     * intuitive behavior for beans that don't expose an ordering.
     *
     * @param o The object
     * @return {@link Ordered#getOrder} when object is instance of Ordered, or the order
     * of the {@link AnnotationMetadata}, or {@link Ordered#LOWEST_PRECEDENCE} if the
     * parameter is neither of those.
     */
    public static int getOrder(Object o) {
        return getOrderWithDefaultPrecedence(o, Ordered.LOWEST_PRECEDENCE);
    }

    /**
     * Get the order for the given bean instance if it implements {@link Ordered}, or for the
     * given {@link AnnotationMetadata} object.
     *
     * @param o The object
     * @param defaultPrecedence What to return if the object is neither an ordered bean nor an
     *                          annotation metadata object.
     * @return {@link Ordered#getOrder} when object is instance of Ordered, or the order
     * of the {@link AnnotationMetadata}, or {@code defaultPrecedence} if the parameter is
     * neither of those.
     */
    public static int getOrderWithDefaultPrecedence(Object o, int defaultPrecedence) {
        if (o instanceof Ordered ordered) {
            return ordered.getOrder();
        } else if (o instanceof AnnotationMetadata metadata) {
            return getOrder(metadata);
        }
        return defaultPrecedence;
    }

    /**
     * Get the order of the given object. Objects implementing {@link Ordered} have precedence
     * over annotation metadata with {@link Order}.
     *
     * @param annotationMetadata The annotation metadata
     * @param o The object
     * @return The order of the object. If no order is found, {@link Ordered#LOWEST_PRECEDENCE} is returned.
     */
    public static int getOrder(AnnotationMetadata annotationMetadata, Object o) {
        if (o instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        return getOrder(annotationMetadata);
    }

    /**
     * Get the order for the given annotation metadata.
     * @param annotationMetadata The metadata
     * @return The order or zero if there is no {@link Order} annotation.
     * @since 3.0.0
     */
    public static int getOrder(@NonNull AnnotationMetadata annotationMetadata) {
        return annotationMetadata.intValue(Order.class).orElse(0);
    }

    /**
     * Get the order for the given Ordered object.
     *
     * @param o The ordered object
     * @return the order
     * @deprecated Inline method
     */
    @Deprecated(since = "4.4.0", forRemoval = true)
    public static int getOrder(Ordered o) {
        return o.getOrder();
    }
}
