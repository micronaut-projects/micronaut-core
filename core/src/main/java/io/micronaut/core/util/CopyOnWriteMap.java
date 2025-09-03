/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Thread-safe map that is optimized for reads. Uses a normal {@link HashMap} that is copied on
 * update operations.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
@Experimental
public final class CopyOnWriteMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    /**
     * How many items to evict at a time, to make eviction a bit more efficient.
     */
    static final int EVICTION_BATCH = 16;
    /**
     * Empty {@link HashMap} to avoid polymorphism.
     */
    @SuppressWarnings("rawtypes")
    private static final Map EMPTY = new HashMap();
    private static final VarHandle ACTUAL_HANDLE;

    private final int maxSizeWithEvictionMargin;

    @SuppressWarnings("unused")
    private Map<? extends K, ? extends V> actualField;

    static {
        try {
            ACTUAL_HANDLE = MethodHandles.lookup().findVarHandle(CopyOnWriteMap.class, "actualField", Map.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("Field should be present", e);
        }
    }

    /**
     * @param maxSize The maximum size of this map
     * @deprecated Use {@link #create(int)}
     */
    @Deprecated(forRemoval = true)
    @NextMajorVersion("Used by validation, remove when ready")
    public CopyOnWriteMap(int maxSize) {
        int maxSizeWithEvictionMargin = maxSize + EVICTION_BATCH;
        if (maxSizeWithEvictionMargin < 0) {
            maxSizeWithEvictionMargin = Integer.MAX_VALUE;
        }
        this.maxSizeWithEvictionMargin = maxSizeWithEvictionMargin;
        actual(EMPTY);
    }

    /**
     * Create a new COW map.
     *
     * @param maxSize The maximum size of the map, when arbitrary items should be evicted
     * @return The map
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> ConcurrentMap<K, V> create(int maxSize) {
        return new CopyOnWriteMap<>(maxSize);
    }

    @SuppressWarnings("unchecked")
    private Map<? extends K, ? extends V> actual() {
        return (Map<? extends K, ? extends V>) ACTUAL_HANDLE.getAcquire(this);
    }

    private void actual(Map<? extends K, ? extends V> map) {
        ACTUAL_HANDLE.setRelease(this, map);
    }

    @NonNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    @Override
    public V get(Object key) {
        return actual().get(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return ((Map<K, V>) actual()).getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(Object key) {
        return actual().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return actual().containsValue(value);
    }

    @Override
    public int size() {
        return actual().size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void clear() {
        actual(EMPTY);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        update(map -> {
            map.putAll(m);
            return null;
        });
    }

    @Override
    public V remove(Object key) {
        return update(m -> m.remove(key));
    }

    @Override
    public int hashCode() {
        return actual().hashCode();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return actual().equals(o);
    }

    @Override
    public String toString() {
        return actual().toString();
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        actual().forEach(action);
    }

    private synchronized <R> R update(Function<Map<K, V>, R> updater) {
        Map<K, V> next = new HashMap<>(actual());
        R ret = updater.apply(next);
        int newSize = next.size();
        if (newSize >= maxSizeWithEvictionMargin) {
            evict(next, EVICTION_BATCH);
        }
        actual(next);
        return ret;
    }

    /**
     * Evict {@code numToEvict} items from the given {@code map} at random. This is not an atomic
     * operation.
     *
     * @param map        The map to modify
     * @param numToEvict The number of items to remove
     */
    public static void evict(Map<?, ?> map, int numToEvict) {
        int size = map.size();
        if (size < numToEvict) {
            // can occasionally happen with concurrent evict with CHM. we're best-effort in that case
            numToEvict = size;
        }
        // select some indices in the map to remove at random
        BitSet toRemove = new BitSet(size);
        for (int i = 0; i < numToEvict; i++) {
            setUnset(toRemove, ThreadLocalRandom.current().nextInt(size - i));
        }
        // iterate over the map and remove those indices
        Iterator<?> iterator = map.entrySet().iterator();
        for (int i = 0; i < size; i++) {
            try {
                iterator.next();
            } catch (NoSuchElementException ignored) {
                // if called on a ConcurrentHashMap, another thread may have modified the map in
                // the meantime
                break;
            }
            if (toRemove.get(i)) {
                iterator.remove();
            }
        }
    }

    /**
     * Set the bit at {@code index}, with the index only counting unset bits. e.g. setting index 0
     * when the first bit of the {@link BitSet} is already set would set the second bit (the first
     * unset bit).
     *
     * @param set   The bit set to modify
     * @param index The index of the bit to set
     */
    static void setUnset(BitSet set, int index) {
        int i = 0;
        while (true) {
            int nextI = set.nextSetBit(i);
            if (nextI == -1 || nextI > index) {
                break;
            }
            i = nextI + 1;
            index++;
        }
        set.set(index);
    }

    @Override
    public V put(K key, V value) {
        return update(m -> m.put(key, value));
    }

    @Override
    public boolean remove(@NonNull Object key, Object value) {
        return update(m -> m.remove(key, value));
    }

    @Override
    public boolean replace(@NonNull K key, @NonNull V oldValue, @NonNull V newValue) {
        return update(m -> m.replace(key, oldValue, newValue));
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        update(m -> {
            m.replaceAll(function);
            return null;
        });
    }

    @Override
    public V computeIfAbsent(K key, @NonNull Function<? super K, ? extends V> mappingFunction) {
        V present = get(key);
        if (present != null) {
            // fast path without sync
            return present;
        } else {
            return update(m -> m.computeIfAbsent(key, mappingFunction));
        }
    }

    @Override
    public V computeIfPresent(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return update(m -> m.computeIfPresent(key, remappingFunction));
    }

    @Override
    public V compute(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return update(m -> m.compute(key, remappingFunction));
    }

    @Override
    public V merge(K key, @NonNull V value, @NonNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return update(m -> m.merge(key, value, remappingFunction));
    }

    @Override
    public V putIfAbsent(@NonNull K key, V value) {
        return update(m -> m.putIfAbsent(key, value));
    }

    @Override
    public V replace(@NonNull K key, @NonNull V value) {
        return update(m -> m.replace(key, value));
    }

    private final class EntrySetIterator implements Iterator<Entry<K, V>> {
        final Iterator<? extends Entry<? extends K, ? extends V>> itr = actual().entrySet().iterator();
        K lastKey;

        @Override
        public boolean hasNext() {
            return itr.hasNext();
        }

        @Override
        public Entry<K, V> next() {
            Entry<? extends K, ? extends V> e = itr.next();
            lastKey = e.getKey();
            return new EntryImpl(e);
        }

        @Override
        public void remove() {
            CopyOnWriteMap.this.remove(lastKey);
        }
    }

    private class EntryImpl implements Entry<K, V> {
        private final Entry<? extends K, ? extends V> entry;

        public EntryImpl(Entry<? extends K, ? extends V> entry) {
            this.entry = entry;
        }

        @Override
        public K getKey() {
            return entry.getKey();
        }

        @Override
        public V getValue() {
            return entry.getValue();
        }

        @Override
        public V setValue(V value) {
            return put(entry.getKey(), value);
        }
    }

    private final class EntrySet extends AbstractSet<Entry<K, V>> {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new EntrySetIterator();
        }

        @Override
        public int size() {
            return actual().size();
        }
    }
}
