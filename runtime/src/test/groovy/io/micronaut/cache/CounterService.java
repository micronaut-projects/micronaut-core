/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cache;

import io.micronaut.cache.annotation.CacheConfig;
import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.CachePut;
import io.micronaut.cache.annotation.Cacheable;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

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
