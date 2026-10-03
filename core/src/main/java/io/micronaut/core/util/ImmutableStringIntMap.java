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
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An immutable map from {@link String} keys to {@code int} values, built for fast lookups.
 *
 * <p>Every entry is inserted in the constructor and every field is final, so an instance is
 * safely published even through a data race (JLS 17.5): a thread that sees the reference also
 * sees the complete table. Callers can therefore cache an instance in a plain, non-volatile field.
 * Do not add mutators or non-final fields to this class, as that would break the guarantee.</p>
 *
 * <p>Up to {@link #LINEAR_SCAN_THRESHOLD} keys are stored densely and looked up by a linear scan
 * with {@link String#equals}. Larger maps use open addressing with linear probing over the
 * spread hash code and a load factor of at most 50%.</p>
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

    private static final ImmutableStringIntMap EMPTY = new ImmutableStringIntMap(new String[0], null);

    /**
     * The keys in order in the dense layout, with {@code null} for a skipped duplicate, otherwise
     * the hash table, whose size is a power of two.
     */
    private final String[] keys;
    /**
     * The value of each hash table slot, or {@code null} for the dense layout, where a key's value
     * is its position.
     */
    private final int @Nullable [] values;

    private ImmutableStringIntMap(String[] keys, int @Nullable [] values) {
        this.keys = keys;
        this.values = values;
    }

    private <T> ImmutableStringIntMap(T[] items, Function<? super T, String> key, boolean skipDuplicates, @Nullable Supplier<String> owner) {
        // Build into locals first, so each final field is assigned exactly once
        int n = items.length;
        if (n <= LINEAR_SCAN_THRESHOLD) {
            String[] denseKeys = new String[n];
            for (int i = 0; i < n; i++) {
                String name = Objects.requireNonNull(key.apply(items[i]), "key");
                // Checking each key against all earlier ones is quadratic, but this layout holds at
                // most LINEAR_SCAN_THRESHOLD keys, so it costs a handful of equals calls, once per map.
                // With skipDuplicates a later duplicate is not added: its slot stays empty, so every
                // other key keeps its position as its value.
                boolean duplicate = false;
                for (int j = 0; j < i; j++) {
                    if (name.equals(denseKeys[j])) {
                        if (!skipDuplicates) {
                            throw duplicateKey(name, owner);
                        }
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    denseKeys[i] = name;
                }
            }
            this.keys = denseKeys;
            this.values = null;
        } else {
            int tableSize = Integer.highestOneBit(n * 2 + 1) * 2;
            int tableMask = tableSize - 1;
            String[] tableKeys = new String[tableSize];
            int[] tableValues = new int[tableSize];
            for (int i = 0; i < n; i++) {
                String name = Objects.requireNonNull(key.apply(items[i]), "key");
                int slot = slot(name, tableMask);
                while (true) {
                    String candidate = tableKeys[slot];
                    if (candidate == null) {
                        tableKeys[slot] = name;
                        tableValues[slot] = i;
                        break;
                    } else if (candidate.equals(name)) {
                        if (skipDuplicates) {
                            break;
                        }
                        throw duplicateKey(name, owner);
                    }
                    slot = (slot + 1) & tableMask;
                }
            }
            this.keys = tableKeys;
            this.values = tableValues;
        }
    }

    /**
     * The first slot to probe for a key. The high half of the hash code is folded into the low
     * half, as {@link java.util.HashMap} does, because the table mask only keeps the low bits:
     * names that differ only in a trailing digit, such as {@code field1}..{@code field24}, would
     * otherwise land in a few neighbouring slots and form long probe chains.
     *
     * @param key  The key
     * @param mask The table mask
     * @return The slot
     */
    private static int slot(String key, int mask) {
        int h = key.hashCode();
        return (h ^ (h >>> 16)) & mask;
    }

    private static IllegalArgumentException duplicateKey(String key, @Nullable Supplier<String> owner) {
        String message = "Duplicate key [" + key + "]";
        return new IllegalArgumentException(owner == null ? message : message + " in " + owner.get());
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
        return of(items, key, false, null);
    }

    /**
     * Creates a map from each item's key to the item's index in {@code items}.
     *
     * @param items          The items
     * @param key            Extracts the non-null key of an item
     * @param skipDuplicates Whether only the first item with a key is mapped and later items with
     *                       the same key are ignored, instead of rejecting a duplicate key
     * @param owner          Describes what is indexed, for the message of a duplicate key, such as
     *                       {@code "the properties of com.example.Book"}; only called for a
     *                       duplicate, and unused if {@code skipDuplicates} is set
     * @param <T>            The item type
     * @return The map
     * @throws IllegalArgumentException if two items have the same key and {@code skipDuplicates}
     *                                  is not set
     */
    public static <T> ImmutableStringIntMap of(T[] items, Function<? super T, String> key, boolean skipDuplicates, @Nullable Supplier<String> owner) {
        if (items.length == 0) {
            return EMPTY;
        }
        return new ImmutableStringIntMap(items, key, skipDuplicates, owner);
    }

    /**
     * Looks up the value of a key.
     *
     * @param key The key
     * @param def The value to return when the key is absent
     * @return The value, or {@code def}
     * @throws NullPointerException if {@code key} is null, whatever the size of the map
     */
    public int get(String key, int def) {
        Objects.requireNonNull(key, "key");
        String[] keys = this.keys;
        int[] values = this.values;
        if (values == null) {
            for (int i = 0; i < keys.length; i++) {
                // an empty slot of a skipped duplicate is null, which equals no key
                if (key.equals(keys[i])) {
                    return i;
                }
            }
            return def;
        }
        int mask = keys.length - 1;
        int slot = slot(key, mask);
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
        return values == null;
    }
}
