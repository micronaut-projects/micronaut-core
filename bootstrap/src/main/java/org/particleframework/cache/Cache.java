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
package org.particleframework.cache;

/**
 * <p>Base cache interface implemented by both {@link SyncCache} and {@link AsyncCache}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Cache<C> {
    /**
     * @return The name of the cache
     */
    String getName();

    /**
     * @return The native cache implementation
     */
    C getNativeCache();

    /**
     * <p>Cache the specified value using the specified key</p>
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    void put(Object key, Object value);

    /**
     * Invalidate the value for the given key
     * @param key The key to invalid
     */
    void invalidate(Object key);

    /**
     * Invalidate all cached values within this cache
     */
    void invalidateAll();
}
