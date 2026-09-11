/*
 * Copyright 2017-2026 original authors
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

import org.jspecify.annotations.Nullable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * An enum-keyed map that behaves like {@link java.util.EnumMap} but is given the constants of the
 * enum instead of looking them up reflectively from its class.
 *
 * <p>The values are kept in an array indexed by {@link Enum#ordinal()}. An empty slot is
 * {@code null}, so a {@code null} value is stored as the {@link #NULL} sentinel. Iteration is in
 * ordinal order and, like {@code EnumMap}, its iterators are weakly consistent: they never throw
 * {@link java.util.ConcurrentModificationException}.</p>
 *
 * <p>The constants array is held, not copied. It must be shaped as the {@code values()} method of
 * the enum returns it, which {@link CollectionUtils#newEnumMap(Enum[])} validates, and it must not
 * be mutated afterwards.</p>
 *
 * @param <K> The enum type
 * @param <V> The value type
 * @author Denis Stepanov
 */
@SuppressWarnings("EnumOrdinal") // indexing by ordinal is the purpose of this map
final class EnumConstantsMap<K extends Enum<K>, V extends @Nullable Object> extends AbstractMap<K, V> {

    /**
     * Stored in place of a {@code null} value, because {@code null} marks an empty slot.
     */
    private static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "EnumConstantsMap.NULL";
        }
    };

    private final K[] universe;
    private final @Nullable Object[] vals;
    private int size;

    private @Nullable Set<Entry<K, V>> entrySet;
    private @Nullable Set<K> keySet;
    private @Nullable Collection<V> values;

    /**
     * @param universe The constants of the enum in ordinal order, already validated
     */
    EnumConstantsMap(K[] universe) {
        this.universe = universe;
        this.vals = new Object[universe.length];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        int index = indexOfKey(key);
        return index >= 0 && vals[index] != null;
    }

    @Override
    public boolean containsValue(@Nullable Object value) {
        Object masked = mask(value);
        for (Object val : vals) {
            if (val != null && masked.equals(val)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable V get(@Nullable Object key) {
        int index = indexOfKey(key);
        return index >= 0 ? unmask(vals[index]) : null;
    }

    @Override
    public @Nullable V getOrDefault(@Nullable Object key, @Nullable V defaultValue) {
        int index = indexOfKey(key);
        if (index >= 0) {
            Object val = vals[index];
            if (val != null) {
                return unmask(val);
            }
        }
        return defaultValue;
    }

    @Override
    public @Nullable V put(K key, V value) {
        int index = indexOfNewKey(key);
        Object old = vals[index];
        vals[index] = mask(value);
        if (old == null) {
            size++;
        }
        return unmask(old);
    }

    @Override
    public @Nullable V remove(@Nullable Object key) {
        int index = indexOfKey(key);
        if (index < 0) {
            return null;
        }
        Object old = vals[index];
        if (old != null) {
            vals[index] = null;
            size--;
        }
        return unmask(old);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        if (m instanceof EnumConstantsMap<?, ?> other && hasSameUniverse(other)) {
            Object[] otherVals = other.vals;
            for (int i = 0; i < otherVals.length; i++) {
                Object val = otherVals[i];
                if (val != null) {
                    if (vals[i] == null) {
                        size++;
                    }
                    vals[i] = val;
                }
            }
        } else {
            super.putAll(m);
        }
    }

    @Override
    public void clear() {
        Arrays.fill(vals, null);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        for (int i = 0; i < vals.length; i++) {
            Object val = vals[i];
            if (val != null) {
                action.accept(universe[i], unmask(val));
            }
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> es = entrySet;
        if (es == null) {
            es = new EntrySet();
            entrySet = es;
        }
        return es;
    }

    @Override
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    @Override
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EnumConstantsMap<?, ?> other && hasSameUniverse(other)) {
            // Both hold masked values, so the sentinel compares by identity
            return size == other.size && Arrays.equals(vals, other.vals);
        }
        // Covers any other Map, including an EnumMap or a HashMap with the same mappings
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (int i = 0; i < vals.length; i++) {
            Object val = vals[i];
            if (val != null) {
                h += universe[i].hashCode() ^ Objects.hashCode(unmask(val));
            }
        }
        return h;
    }

    /**
     * The slot of a key being read. The identity check rejects a constant of another enum with the
     * same ordinal without calling {@link Enum#getDeclaringClass()}.
     *
     * @param key The key
     * @return The index of its value, or -1 if the key is not a constant of this map's enum
     */
    private int indexOfKey(@Nullable Object key) {
        if (key instanceof Enum<?> e) {
            int ordinal = e.ordinal();
            if (ordinal < universe.length && universe[ordinal] == e) {
                return ordinal;
            }
        }
        return -1;
    }

    /**
     * The slot of a key being stored, rejecting keys that {@code EnumMap} rejects.
     *
     * @param key The key
     * @return The index of its value
     * @throws NullPointerException if the key is null
     * @throws ClassCastException if the key is a constant of another enum
     */
    private int indexOfNewKey(K key) {
        Objects.requireNonNull(key, "key");
        int ordinal = key.ordinal();
        if (ordinal >= universe.length || universe[ordinal] != key) {
            throw new ClassCastException(key.getDeclaringClass() + " is not the key type of the map, whose constants are " + Arrays.toString(universe));
        }
        return ordinal;
    }

    /**
     * Whether the other map was created from the constants of the same enum. Both arrays passed the
     * factory's validation, so arrays of the same length starting with the same constant hold the
     * same constants. This lets two maps built from separate {@code values()} calls share fast paths.
     *
     * @param other The other map
     * @return true if both maps are keyed by the same enum
     */
    private boolean hasSameUniverse(EnumConstantsMap<?, ?> other) {
        Enum<?>[] otherUniverse = other.universe;
        return otherUniverse == universe
            || (otherUniverse.length == universe.length && universe.length > 0 && otherUniverse[0] == universe[0]);
    }

    /**
     * Whether the key is mapped to the value. The candidate value is compared with
     * {@code candidate.equals(stored)}, as {@link Set#contains} specifies and {@code EnumMap} does.
     *
     * @param key   The candidate key
     * @param value The candidate value
     * @return true if the map holds the mapping
     */
    private boolean containsMapping(@Nullable Object key, @Nullable Object value) {
        int index = indexOfKey(key);
        if (index < 0) {
            return false;
        }
        Object val = vals[index];
        return val != null && mask(value).equals(val);
    }

    private static Object mask(@Nullable Object value) {
        return value == null ? NULL : value;
    }

    @SuppressWarnings("unchecked")
    private @Nullable V unmask(@Nullable Object value) {
        return (V) (value == NULL ? null : value);
    }

    /**
     * Iterates the occupied slots in ordinal order.
     *
     * @param <T> The element type
     */
    private abstract class SlotIterator<T> implements Iterator<T> {
        private int next;
        private int lastReturned = -1;

        @Override
        public boolean hasNext() {
            while (next < vals.length && vals[next] == null) {
                next++;
            }
            return next < vals.length;
        }

        @Override
        public @Nullable T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            lastReturned = next++;
            return element(lastReturned);
        }

        @Override
        public void remove() {
            if (lastReturned < 0) {
                throw new IllegalStateException();
            }
            if (vals[lastReturned] != null) {
                vals[lastReturned] = null;
                size--;
            }
            lastReturned = -1;
        }

        /**
         * @param index An occupied slot
         * @return The element of the slot
         */
        abstract @Nullable T element(int index);
    }

    private final class EntrySet extends AbstractSet<Entry<K, V>> {

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new SlotIterator<>() {
                @Override
                Entry<K, V> element(int index) {
                    return new SlotEntry(index);
                }
            };
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return o instanceof Entry<?, ?> entry && containsMapping(entry.getKey(), entry.getValue());
        }

        @Override
        public boolean remove(@Nullable Object o) {
            if (o instanceof Entry<?, ?> entry) {
                Object key = entry.getKey();
                if (containsMapping(key, entry.getValue())) {
                    EnumConstantsMap.this.remove(key);
                    return true;
                }
            }
            return false;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            EnumConstantsMap.this.clear();
        }
    }

    private final class KeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new SlotIterator<>() {
                @Override
                K element(int index) {
                    return universe[index];
                }
            };
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(@Nullable Object o) {
            if (containsKey(o)) {
                EnumConstantsMap.this.remove(o);
                return true;
            }
            return false;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            EnumConstantsMap.this.clear();
        }
    }

    private final class Values extends AbstractCollection<V> {

        @Override
        public Iterator<V> iterator() {
            return new SlotIterator<>() {
                @Override
                @Nullable V element(int index) {
                    return unmask(vals[index]);
                }
            };
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return containsValue(o);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            EnumConstantsMap.this.clear();
        }
    }

    /**
     * An entry reading and writing its slot, so {@link #setValue} writes through to the map.
     */
    private final class SlotEntry implements Entry<K, V> {
        private final int index;

        SlotEntry(int index) {
            this.index = index;
        }

        @Override
        public K getKey() {
            return universe[index];
        }

        @Override
        public @Nullable V getValue() {
            return unmask(occupied());
        }

        @Override
        public @Nullable V setValue(V value) {
            Object old = occupied();
            vals[index] = mask(value);
            return unmask(old);
        }

        private Object occupied() {
            Object val = vals[index];
            if (val == null) {
                throw new IllegalStateException("Entry was removed");
            }
            return val;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Entry<?, ?> e
                && getKey() == e.getKey()
                && Objects.equals(getValue(), e.getValue());
        }

        @Override
        public int hashCode() {
            return getKey().hashCode() ^ Objects.hashCode(getValue());
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }
    }
}
