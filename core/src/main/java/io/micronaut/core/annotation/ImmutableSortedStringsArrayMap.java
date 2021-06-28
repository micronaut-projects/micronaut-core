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
package io.micronaut.core.annotation;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The immutable map which is using the advantage of compile-time processing an ability to have
 * map's keys sorted.
 * <p>
 * The implementation is using two arrays and should be memory evitient.
 * The lookup is implemented using {@link Arrays#binarySearch(Object[], Object)}
 * which should guarantee performance of O(log N).
 *
 * @param <V> The value type
 * @author Denis Stepanov
 * @since 3.0
 */
@SuppressWarnings("ParameterNumber")
@Internal
@UsedByGeneratedCode
final class ImmutableSortedStringsArrayMap<V> implements Map<String, V> {

    private final String[] keys;
    private final Object[] values;

    ImmutableSortedStringsArrayMap(String[] keys, Object[] values) {
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

    @NonNull
    @Override
    public Set<String> keySet() {
        return new HashSet<>(Arrays.asList(keys));
    }

    @NonNull
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
                return ImmutableSortedStringsArrayMap.this.size();
            }

            public boolean isEmpty() {
                return ImmutableSortedStringsArrayMap.this.isEmpty();
            }

            public void clear() {
                ImmutableSortedStringsArrayMap.this.clear();
            }

            public boolean contains(Object v) {
                return ImmutableSortedStringsArrayMap.this.containsValue(v);
            }
        };
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super V> action) {
        for (int i = 0; i < keys.length; i += 1) {
            action.accept(keys[i], (V) values[i]);
        }
    }

    @NonNull
    @Override
    public Set<Entry<String, V>> entrySet() {
        Set<Entry<String, V>> set = new HashSet<>();
        for (int i = 0; i < keys.length; i += 1) {
            set.add(new AbstractMap.SimpleEntry<>(keys[i], (V) values[i]));
        }
        return set;
    }

}
