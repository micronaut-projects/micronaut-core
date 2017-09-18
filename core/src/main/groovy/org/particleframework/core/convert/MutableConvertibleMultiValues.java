/*
 * Copyright 2017 original authors
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
package org.particleframework.core.convert;

import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableConvertibleMultiValues<V> extends ConvertibleMultiValues<V> {

    /**
     * Adds a value for the given key. Note that this method will not remove items currently associated with the key.
     *
     * @param key The key
     * @param value The value
     * @return This instance
     */
    MutableConvertibleMultiValues<V> add(CharSequence key, V value);

    /**
     * Replaces the value or values at the given key with the specified value. This method will override any previous values.
     * Use {@link #add(CharSequence, Object)} if you wish to add additional keys
     *
     * @param key The key
     * @param value The value
     * @return This instance
     */
    MutableConvertibleMultiValues<V> put(CharSequence key, V value);

    /**
     * Remove the given value from the given key
     *
     * @param key The key
     * @param value The value
     * @return This instance
     */
    MutableConvertibleMultiValues<V> remove(CharSequence key, V value);

    /**
     * Clear all values associated with the given key
     *
     * @param key They key
     * @return This instance
     */
    MutableConvertibleMultiValues<V> clear(CharSequence key);

    /**
     * Clear all values
     *
     * @return This instance
     */
    MutableConvertibleMultiValues<V> clear();
}
