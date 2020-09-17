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

import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.ConversionService;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
     * Is the given type an iterable or map type.
     * @param type The type
     * @return True if it is iterable or map
     * @since 2.0.0
     */
    public static boolean isIterableOrMap(Class<?> type) {
        return type != null && (Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type));
    }

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
        }
        if (iterableType.equals(Queue.class)) {
            return Optional.of(new LinkedList<>(collection));
        }
        if (iterableType.equals(List.class)) {
            return Optional.of(new ArrayList<>(collection));
        }
        if (!iterableType.isInterface()) {
            try {
                Constructor<? extends Iterable<T>> constructor = iterableType.getConstructor(Collection.class);
                return Optional.of(constructor.newInstance(collection));
            } catch (Throwable e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Create a {@link LinkedHashMap} from an array of values.
     *
     * @param values The values
     * @return The created map
     */
    @UsedByGeneratedCode
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
     * Convert an {@link Enumeration} to a {@link Iterable}.
     *
     * @param enumeration The iterator
     * @param <T>         The type
     * @return The set
     */
    public static @NonNull <T> Iterable<T> enumerationToIterable(@Nullable Enumeration<T> enumeration) {
        if (enumeration == null) {
            return Collections.emptyList();
        }

        return () -> new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return enumeration.hasMoreElements();
            }

            @Override
            public T next() {
                return enumeration.nextElement();
            }
        };
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
            return new HashSet<>(0);
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
                if (CharSequence.class.isInstance(o)) {
                    builder.append(o.toString());
                } else {
                    Optional<String> converted = ConversionService.SHARED.convert(o, String.class);
                    converted.ifPresent(builder::append);
                }
            }
            if (i.hasNext()) {
                builder.append(delimiter);
            }
        }
        return builder.toString();
    }

    /**
     * Converts an {@link Iterable} to a {@link List}.
     *
     * @param iterable The iterable
     * @param <T>      The generic type
     * @return The list
     */
    public static <T> List<T> iterableToList(Iterable<T> iterable) {
        if (iterable == null) {
            return Collections.emptyList();
        }
        if (iterable instanceof List) {
            return (List<T>) iterable;
        }
        Iterator<T> i = iterable.iterator();
        if (i.hasNext()) {
            List<T> list = new ArrayList<>();
            while (i.hasNext()) {
                list.add(i.next());
            }
            return list;
        }
        return Collections.emptyList();
    }

    /**
     * Converts an {@link Iterable} to a {@link Set}.
     *
     * @param iterable The iterable
     * @param <T>      The generic type
     * @return The set
     */
    public static <T> Set<T> iterableToSet(Iterable<T> iterable) {
        if (iterable == null) {
            return Collections.emptySet();
        }
        if (iterable instanceof Set) {
            return (Set<T>) iterable;
        }
        Iterator<T> i = iterable.iterator();
        if (i.hasNext()) {
            Set<T> list = new HashSet<>();
            while (i.hasNext()) {
                list.add(i.next());
            }
            return list;
        }
        return Collections.emptySet();
    }

    /**
     * Null safe version of {@link Collections#unmodifiableList(List)}.
     *
     * @param list The list
     * @param <T> The generic type
     * @return A non-null unmodifiable list
     */
    public static @NonNull <T> List<T> unmodifiableList(@Nullable List<T> list) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the last element of a collection.
     *
     * @param collection The collection
     * @param <T> The generic type
     * @return The last element of a collection or null
     */
    public static @Nullable <T> T last(@NonNull Collection<T> collection) {
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            final int s = list.size();
            if (s > 0) {
                return list.get(s - 1);
            } else {
                return null;
            }
        } else if (collection instanceof Deque) {
            final Iterator<T> i = ((Deque<T>) collection).descendingIterator();
            if (i.hasNext()) {
                return i.next();
            }
            return null;
        } else if (collection instanceof NavigableSet) {
            final Iterator<T> i = ((NavigableSet<T>) collection).descendingIterator();
            if (i.hasNext()) {
                return i.next();
            }
            return null;
        } else {
            T result = null;
            for (T t : collection) {
                result = t;
            }
            return result;
        }

    }
}
