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

import io.micronaut.core.convert.ConversionService;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * <p>Utility methods for working with {@link java.util.Collection} types</p>.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CollectionUtils {

    /**
     * Null safe empty check.
     *
     * @param map The map
     * @return True if it is empty or null
     */
    public static boolean isEmpty(@Nullable Map map) {
        return map == null || map.isEmpty();
    }

    /**
     * Null safe not empty check.
     *
     * @param map The ,ap
     * @return True if it is not null and not empty
     */
    public static boolean isNotEmpty(@Nullable Map map) {
        return map != null && !map.isEmpty();
    }

    /**
     * Null safe empty check.
     *
     * @param collection The collection
     * @return True if it is empty or null
     */
    public static boolean isEmpty(@Nullable Collection collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Null safe not empty check.
     *
     * @param collection The collection
     * @return True if it is not null and not empty
     */
    public static boolean isNotEmpty(@Nullable Collection collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * <p>Attempts to convert a collection to the given iterabable type</p>.
     *
     * @param iterableType The iterable type
     * @param collection   The collection
     * @param <T>          The collection generic type
     * @return An {@link Optional} of the converted type
     */
    public static <T> Optional<Iterable<T>> convertCollection(Class<? extends Iterable<T>> iterableType, Collection<T> collection) {
        if (iterableType.isInstance(collection)) {
            return Optional.of(collection);
        }
        if (iterableType.equals(Set.class)) {
            return Optional.of(new HashSet<>(collection));
        } else if (iterableType.equals(Queue.class)) {
            return Optional.of(new LinkedList<>(collection));
        } else if (iterableType.equals(List.class)) {
            return Optional.of(new ArrayList<>(collection));
        } else if (!iterableType.isInterface()) {
            try {
                Constructor<? extends Iterable<T>> constructor = iterableType.getConstructor(Collection.class);
                return Optional.of(constructor.newInstance(collection));
            } catch (Throwable e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Create a {@link LinkedHashMap} from an array of values.
     *
     * @param values The values
     * @return The created map
     */
    public static Map mapOf(Object... values) {
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        Map answer = new LinkedHashMap(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++], values[i++]);
        }
        return answer;
    }

    /**
     * Convert an {@link Iterator} to a {@link Set}.
     *
     * @param iterator The iterator
     * @param <T>      The type
     * @return The set
     */
    public static <T> Set<T> iteratorToSet(Iterator<T> iterator) {
        Set<T> set = new HashSet<>();
        while (iterator.hasNext()) {
            set.add(iterator.next());
        }
        return set;
    }

    /**
     * Convert an {@link Enumeration} to a {@link Set}.
     *
     * @param enumeration The iterator
     * @param <T>         The type
     * @return The set
     */
    public static <T> Set<T> enumerationToSet(Enumeration<T> enumeration) {
        Set<T> set = new HashSet<>();
        while (enumeration.hasMoreElements()) {
            set.add(enumeration.nextElement());
        }
        return set;
    }

    /**
     * Creates a set of the given objects.
     *
     * @param objects The objects
     * @param <T>     The type
     * @return The set
     */
    public static <T> Set<T> setOf(T... objects) {
        if (objects == null || objects.length == 0) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(objects));
    }

    /**
     * Produce a string representation of the given iterable.
     *
     * @param iterable The iterable
     * @return The string representation
     */
    public static String toString(Iterable<?> iterable) {
        return toString(",", iterable);
    }

    /**
     * Produce a string representation of the given iterable.
     *
     * @param delimiter The delimiter
     * @param iterable  The iterable
     * @return The string representation
     */
    public static String toString(String delimiter, Iterable<?> iterable) {
        StringBuilder builder = new StringBuilder();
        Iterator<?> i = iterable.iterator();
        while (i.hasNext()) {
            Object o = i.next();
            if (o == null) {
                continue;
            } else {
                Optional<String> converted = ConversionService.SHARED.convert(o, String.class);
                converted.ifPresent(builder::append);
            }
            if (i.hasNext()) {
                builder.append(delimiter);
            }
        }
        return builder.toString();
    }
}
