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

import org.particleframework.cache.annotation.CacheConfig;
import org.particleframework.cache.annotation.CacheInvalidate;
import org.particleframework.cache.annotation.CachePut;
import org.particleframework.cache.annotation.Cacheable;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@CacheConfig(cacheNames = {"counter"})
public class CounterService {
    Map<String, Integer> counters = new LinkedHashMap<>();

    public int incrementNoCache(String name) {
        int value = counters.computeIfAbsent(name, s -> 0);
        counters.put(name, ++value);
        return value;
    }

    @CachePut
    public int increment(String name) {
        int value = counters.computeIfAbsent(name, s -> 0);
        counters.put(name, ++value);
        return value;
    }

    @Cacheable
    public int getValue(String name) {
        return counters.computeIfAbsent(name, s -> 0);
    }


    @CacheInvalidate(all = true)
    public void reset() {
        counters.clear();
    }

    @CacheInvalidate()
    public void reset(String name) {
        counters.remove(name);
    }
    @CacheInvalidate
    public void set(String name, int val) {
        counters.put(name, val);
    }
}
