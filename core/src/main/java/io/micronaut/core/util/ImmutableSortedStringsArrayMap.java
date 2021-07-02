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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * The immutable map which is using the advantage of compile-time processing an ability to have
 * map's keys sorted.
 * <p>
 * The implementation is using two arrays and should be memory evitient.
 * The lookup is implemented using {@link Arrays#binarySearch(Object[], Object)}
 * which should guarantee performance of O(log N).
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@SuppressWarnings("ParameterNumber")
@Internal
@UsedByGeneratedCode
public final class ImmutableSortedStringsArrayMap {

    /**
     * Create a new immutable {@link Map} from an array of values.
     * String values must be sorted!
     *
     * @param array The key,value array
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(Object... array) {
        int len = array.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }
        if (array.length == 0) {
            return Collections.EMPTY_MAP;
        }
        int size = len / 2;
        String[] keys = new String[size];
        Object[] values = new Object[size];
        int k = 0;
        for (int i = 0, arrayLength = array.length; i < arrayLength; i += 2) {
            keys[k] = (String) array[i];
            values[k] = array[i + 1];
            k++;
        }
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key 1
     * @param value1 The value 1
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1) {
        return Collections.singletonMap(key1, value1);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1, String key2, Object value2) {
        String[] keys = {
                key1, key2
        };
        Object[] values = {
                value1, value2
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3) {
        String[] keys = {
                key1, key2, key3
        };
        Object[] values = {
                value1, value2, value3
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @param key4   The key4
     * @param value4 The value4
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4) {
        String[] keys = {
                key1, key2, key3, key4
        };
        Object[] values = {
                value1, value2, value3, value4
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @param key4   The key4
     * @param value4 The value4
     * @param key5   The key5
     * @param value5 The value5
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4,
                                         String key5, Object value5) {
        String[] keys = {
                key1, key2, key3, key4, key5
        };
        Object[] values = {
                value1, value2, value3, value4, value5
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @param key4   The key4
     * @param value4 The value4
     * @param key5   The key5
     * @param value5 The value5
     * @param key6   The key6
     * @param value6 The value6
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4,
                                         String key5, Object value5,
                                         String key6, Object value6) {
        String[] keys = {
                key1, key2, key3, key4, key5, key6
        };
        Object[] values = {
                value1, value2, value3, value4, value5, value6
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @param key4   The key4
     * @param value4 The value4
     * @param key5   The key5
     * @param value5 The value5
     * @param key6   The key6
     * @param value6 The value6
     * @param key7   The key7
     * @param value7 The value7
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4,
                                         String key5, Object value5,
                                         String key6, Object value6,
                                         String key7, Object value7) {
        String[] keys = {
                key1, key2, key3, key4, key5, key6, key7
        };
        Object[] values = {
                value1, value2, value3, value4, value5, value6, value7
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @param key4   The key4
     * @param value4 The value4
     * @param key5   The key5
     * @param value5 The value5
     * @param key6   The key6
     * @param value6 The value6
     * @param key7   The key7
     * @param value7 The value7
     * @param key8   The key8
     * @param value8 The value8
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4,
                                         String key5, Object value5,
                                         String key6, Object value6,
                                         String key7, Object value7,
                                         String key8, Object value8) {
        String[] keys = {
                key1, key2, key3, key4, key5, key6, key7, key8
        };
        Object[] values = {
                value1, value2, value3, value4, value5, value6, value7, value8
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1   The key1
     * @param value1 The value1
     * @param key2   The key2
     * @param value2 The value2
     * @param key3   The key3
     * @param value3 The value3
     * @param key4   The key4
     * @param value4 The value4
     * @param key5   The key5
     * @param value5 The value5
     * @param key6   The key6
     * @param value6 The value6
     * @param key7   The key7
     * @param value7 The value7
     * @param key8   The key8
     * @param value8 The value8
     * @param key9   The key9
     * @param value9 The value9
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4,
                                         String key5, Object value5,
                                         String key6, Object value6,
                                         String key7, Object value7,
                                         String key8, Object value8,
                                         String key9, Object value9) {
        String[] keys = {
                key1, key2, key3, key4, key5, key6, key7, key8, key9
        };
        Object[] values = {
                value1, value2, value3, value4, value5, value6, value7, value8, value9
        };
        return new Impl(keys, values);
    }

    /**
     * Create a new immutable {@link Map}.
     * String values must be sorted!
     *
     * @param key1    The key1
     * @param value1  The value1
     * @param key2    The key2
     * @param value2  The value2
     * @param key3    The key3
     * @param value3  The value3
     * @param key4    The key4
     * @param value4  The value4
     * @param key5    The key5
     * @param value5  The value5
     * @param key6    The key6
     * @param value6  The value6
     * @param key7    The key7
     * @param value7  The value7
     * @param key8    The key8
     * @param value8  The value8
     * @param key9    The key9
     * @param value9  The value9
     * @param key10   The key10
     * @param value10 The value10
     * @return The created map
     */
    @UsedByGeneratedCode
    public static Map<String, Object> of(String key1, Object value1,
                                         String key2, Object value2,
                                         String key3, Object value3,
                                         String key4, Object value4,
                                         String key5, Object value5,
                                         String key6, Object value6,
                                         String key7, Object value7,
                                         String key8, Object value8,
                                         String key9, Object value9,
                                         String key10, Object value10) {
        String[] keys = {
                key1, key2, key3, key4, key5, key6, key7, key8, key9, key10
        };
        Object[] values = {
                value1, value2, value3, value4, value5, value6, value7, value8, value9, value10
        };
        return new Impl(keys, values);
    }

    /**
     * The imputable map based on arrays of sorted keys and values.
     * The lookup is using {@link Arrays#binarySearch(Object[], Object)}.
     *
     * @param <V> The value type
     * @author Denis Stepanov
     * @since 3.0
     */
    private static final class Impl<V> implements Map<String, V> {

        private final String[] keys;
        private final Object[] values;

        private Impl(String[] keys, Object[] values) {
            this.keys = keys;
            this.values = values;
        }

        private int findKeyIndex(Object key) {
            if (!(key instanceof Comparable)) {
                return -1;
            }
            return Arrays.binarySearch(keys, key);
        }

        @Override
        public int size() {
            return keys.length;
        }

        @Override
        public boolean isEmpty() {
            return keys.length == 0;
        }

        @Override
        public boolean containsKey(Object key) {
            return findKeyIndex(key) > -1;
        }

        @Override
        public boolean containsValue(Object value) {
            Objects.requireNonNull(value);
            for (int i = 0; i < values.length; i += 1) {
                Object tableValue = values[i];
                if (tableValue.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public V get(Object key) {
            Objects.requireNonNull(key);
            int keyIndex = findKeyIndex(key);
            if (keyIndex < 0) {
                return null;
            }
            return (V) values[keyIndex];
        }

        @Nullable
        @Override
        public V put(String key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends String, ? extends V> m) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public Set<String> keySet() {
            return new HashSet<>(Arrays.asList(keys));
        }

        @NotNull
        @Override
        public Collection<V> values() {
            return new AbstractCollection<V>() {
                public Iterator<V> iterator() {
                    return new Iterator<V>() {
                        private int index = 0;

                        public boolean hasNext() {
                            return index < values.length;
                        }

                        public V next() {
                            if (hasNext()) {
                                V v = (V) values[index];
                                index += 1;
                                return v;
                            }
                            throw new NoSuchElementException();
                        }

                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                public int size() {
                    return Impl.this.size();
                }

                public boolean isEmpty() {
                    return Impl.this.isEmpty();
                }

                public void clear() {
                    Impl.this.clear();
                }

                public boolean contains(Object v) {
                    return Impl.this.containsValue(v);
                }
            };
        }

        @Override
        public void forEach(BiConsumer<? super String, ? super V> action) {
            for (int i = 0; i < keys.length; i += 1) {
                action.accept(keys[i], (V) values[i]);
            }
        }

        @NotNull
        @Override
        public Set<Entry<String, V>> entrySet() {
            Set<Entry<String, V>> set = new HashSet<>();
            for (int i = 0; i < keys.length; i += 1) {
                set.add(new AbstractMap.SimpleEntry<>(keys[i], (V) values[i]));
            }
            return set;
        }
    }

}
