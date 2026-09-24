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

import io.micronaut.core.annotation.Internal;

import java.util.Objects;
import java.util.function.Function;

/**
 * An immutable map from {@link String} keys to {@code int} values, built for fast lookups.
 *
 * <p>Every entry is inserted in the constructor and every field is final, so an instance is
 * safely published even through a data race (JLS 17.5): a thread that sees the reference also
 * sees the complete table. Callers can therefore cache an instance in a plain, non-volatile field.
 * Do not add mutators or non-final fields to this class, as that would break the guarantee.</p>
 *
 * <p>Up to {@link #LINEAR_SCAN_THRESHOLD} keys are stored densely and looked up by a linear scan
 * with {@link String#equals}. Larger maps use open addressing with linear probing and a load
 * factor of at most 50%.</p>
 *
 * @author Jochen Seeber
 * @since 5.3.0
 */
@Internal
public final class ImmutableStringIntMap {

    /**
     * Maximum number of keys that are looked up by a linear scan.
     */
    static final int LINEAR_SCAN_THRESHOLD = 4;

    private static final int LINEAR_SCAN = -1;

    private static final ImmutableStringIntMap EMPTY = new ImmutableStringIntMap(new String[0], new int[0], LINEAR_SCAN);

    /**
     * {@link #LINEAR_SCAN} for the dense layout, otherwise the hash table mask.
     */
    private final int mask;
    private final String[] keys;
    private final int[] values;

    private ImmutableStringIntMap(String[] keys, int[] values, int mask) {
        this.keys = keys;
        this.values = values;
        this.mask = mask;
    }

    private <T> ImmutableStringIntMap(T[] items, Function<? super T, String> key) {
        // Build into locals first, so each final field is assigned exactly once
        int n = items.length;
        if (n <= LINEAR_SCAN_THRESHOLD) {
            String[] denseKeys = new String[n];
            int[] denseValues = new int[n];
            for (int i = 0; i < n; i++) {
                String name = Objects.requireNonNull(key.apply(items[i]), "key");
                for (int j = 0; j < i; j++) {
                    if (denseKeys[j].equals(name)) {
                        throw new IllegalArgumentException("Duplicate key");
                    }
                }
                denseKeys[i] = name;
                denseValues[i] = i;
            }
            this.keys = denseKeys;
            this.values = denseValues;
            this.mask = LINEAR_SCAN;
        } else {
            int tableSize = Integer.highestOneBit(n * 2 + 1) * 2;
            int tableMask = tableSize - 1;
            String[] tableKeys = new String[tableSize];
            int[] tableValues = new int[tableSize];
            for (int i = 0; i < n; i++) {
                String name = Objects.requireNonNull(key.apply(items[i]), "key");
                int slot = name.hashCode() & tableMask;
                while (true) {
                    String candidate = tableKeys[slot];
                    if (candidate == null) {
                        tableKeys[slot] = name;
                        tableValues[slot] = i;
                        break;
                    } else if (candidate.equals(name)) {
                        throw new IllegalArgumentException("Duplicate key");
                    }
                    slot = (slot + 1) & tableMask;
                }
            }
            this.keys = tableKeys;
            this.values = tableValues;
            this.mask = tableMask;
        }
    }

    /**
     * Creates a map from each item's key to the item's index in {@code items}.
     *
     * @param items The items
     * @param key   Extracts the non-null key of an item
     * @param <T>   The item type
     * @return The map
     * @throws IllegalArgumentException if two items have the same key
     */
    public static <T> ImmutableStringIntMap of(T[] items, Function<? super T, String> key) {
        if (items.length == 0) {
            return EMPTY;
        }
        return new ImmutableStringIntMap(items, key);
    }

    /**
     * Looks up the value of a key.
     *
     * @param key The key, must not be null
     * @param def The value to return when the key is absent
     * @return The value, or {@code def}
     */
    public int get(String key, int def) {
        String[] keys = this.keys;
        if (mask == LINEAR_SCAN) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(key)) {
                    return values[i];
                }
            }
            return def;
        }
        int slot = key.hashCode() & mask;
        while (true) {
            String candidate = keys[slot];
            if (candidate == null) {
                return def;
            } else if (candidate.equals(key)) {
                return values[slot];
            }
            slot = (slot + 1) & mask;
        }
    }

    /**
     * @return Whether this map uses the dense linear-scan layout
     */
    boolean isLinearScan() {
        return mask == LINEAR_SCAN;
    }
}
