/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.core.util.clhm;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A class that can determine the weight of an entry. The total weight threshold
 * is used to determine when an eviction is required.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author ben.manes@gmail.com (Ben Manes)
 * @see <a href="https://code.google.com/p/concurrentlinkedhashmap/">
 *      https://code.google.com/p/concurrentlinkedhashmap/</a>
 */
@ThreadSafe
public interface EntryWeigher<K, V> {

    /**
     * Measures an entry's weight to determine how many units of capacity that
     * the key and value consumes. An entry must consume a minimum of one unit.
     *
     * @param key the key to weigh
     * @param value the value to weigh
     * @return the entry's weight
     */
    int weightOf(K key, V value);
}
