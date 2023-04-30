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

import io.micronaut.core.annotation.Internal;

/**
 * Fixed-size String->int map optimized for very fast read operations.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class StringIntMap {
    private final int mask;
    private final String[] keys;
    private final int[] values;

    /**
     * Create a new map. The given size <b>must not</b> be exceeded by {@link #put} operations, or
     * there may be infinite loops. There is no sanity check for this for performance reasons!
     *
     * @param size The maximum size of the map
     */
    public StringIntMap(int size) {
        // min size: at least one slot, aim for 50% load factor
        int tableSize = (size * 2) + 1;
        // round to next power of two for efficient hash code masking
        tableSize = Integer.highestOneBit(tableSize) * 2;
        this.mask = tableSize - 1;
        this.keys = new String[tableSize];
        this.values = new int[keys.length];
    }

    private int probe(String key) {
        int n = keys.length;
        int i = key.hashCode() & mask;
        while (true) {
            String candidate = keys[i];
            if (candidate == null) {
                return ~i;
            } else if (candidate.equals(key)) {
                return i;
            } else {
                i++;
                if (i == n) {
                    i = 0;
                }
            }
        }
    }

    public int get(String key, int def) {
        int i = probe(key);
        return i < 0 ? def : values[i];
    }

    public void put(String key, int value) {
        int tableIndex = ~probe(key);
        if (tableIndex < 0) {
            throw new IllegalArgumentException("Duplicate key");
        }
        keys[tableIndex] = key;
        values[tableIndex] = value;
    }
}
