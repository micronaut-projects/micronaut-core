/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.lettuce.cache;

import io.micronaut.cache.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@CacheConfig(cacheNames = {"counter"})
public class CounterService {
    Map<String, Integer> counters = new LinkedHashMap<>();
    Map<String, Integer> counters2 = new LinkedHashMap<>();

    public int incrementNoCache(String name) {
        int value = counters.computeIfAbsent(name, (key)-> 0);
        counters.put(name, ++value);
        return value;
    }

    @CachePut
    public int increment(String name) {
        int value = counters.computeIfAbsent(name, (key)-> 0);
        counters.put(name, ++value);
        return value;
    }

    @CachePut("counter")
    @CachePut("counter2")
    public int increment2(String name) {
        int value = counters2.computeIfAbsent(name, (key)-> 0);
        counters2.put(name, ++value);
        return value;
    }

    @Cacheable
    public CompletableFuture<Integer> futureValue(String name) {
        return CompletableFuture.completedFuture(counters.computeIfAbsent(name, (key)-> 0));
    }

    @Cacheable
    public Flux<Integer> flowableValue(String name) {
        return Flux.just(counters.computeIfAbsent(name, (key)-> 0));
    }

    @Cacheable
    public Mono<Integer> singleValue(String name) {
        return Mono.just(counters.computeIfAbsent(name, (key)-> 0));
    }

    @CachePut
    public CompletableFuture<Integer> futureIncrement(String name) {
        int value = counters.computeIfAbsent(name, (key)-> 0);
        counters.put(name, ++value);
        return CompletableFuture.completedFuture(value);
    }

    @Cacheable
    public int getValue(String name) {
        return counters.computeIfAbsent(name, (key)-> 0);
    }

    @Cacheable("counter2")
    public int getValue2(String name) {
        return counters2.computeIfAbsent(name, (key)-> 0);
    }

    @Cacheable
    public Optional<Integer> getOptionalValue(String name) {
        return Optional.ofNullable(counters.get(name));
    }

    @CacheInvalidate(all = true)
    public void reset() {
        counters.clear();
    }

    @CacheInvalidate
    public void reset(String name) {
        counters.remove(name);
    }

    @CacheInvalidate("counter")
    @CacheInvalidate("counter2")
    public void reset2(String name) {
        counters.remove(name);
    }

    @CacheInvalidate(parameters = "name")
    public void set(String name, int val) {
        counters.put(name, val);
    }
}
