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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Responses in the memory of this gateway, least recently used first out once the stored bytes,
 * the keys included, go over the capacity. The variants of a resource are indexed by the
 * resource: an invalidation removes them without a scan of the other responses.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class InMemoryResponseCacheStore implements ResponseCacheStore {

    private final long capacity;
    private final LinkedHashMap<Key, CachedResponse> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Set<String>> variants = new HashMap<>();
    private long size;

    /**
     * @param capacity The maximum number of bytes of the stored responses
     */
    public InMemoryResponseCacheStore(long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("The capacity of a response cache is negative: " + capacity);
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized CompletionStage<@Nullable CachedResponse> get(String resource, String variant) {
        return CompletableFuture.completedFuture(entries.get(new Key(resource, variant)));
    }

    @Override
    public synchronized CompletionStage<?> put(String resource, String variant, CachedResponse response) {
        Key key = new Key(resource, variant);
        long entrySize = key.size() + response.size();
        if (entrySize > capacity) {
            return CompletableFuture.completedFuture(null);
        }
        CachedResponse old = entries.put(key, response);
        if (old != null) {
            size -= key.size() + old.size();
        }
        variants.computeIfAbsent(resource, r -> new HashSet<>()).add(variant);
        size += entrySize;
        Iterator<Map.Entry<Key, CachedResponse>> eldest = entries.entrySet().iterator();
        while (size > capacity && eldest.hasNext()) {
            Map.Entry<Key, CachedResponse> entry = eldest.next();
            size -= entry.getKey().size() + entry.getValue().size();
            unindex(entry.getKey());
            eldest.remove();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<?> invalidate(String resource) {
        Set<String> of = variants.remove(resource);
        if (of != null) {
            for (String variant : of) {
                Key key = new Key(resource, variant);
                CachedResponse removed = entries.remove(key);
                if (removed != null) {
                    size -= key.size() + removed.size();
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void unindex(Key key) {
        Set<String> of = variants.get(key.resource());
        if (of != null) {
            of.remove(key.variant());
            if (of.isEmpty()) {
                variants.remove(key.resource());
            }
        }
    }

    /**
     * Remove every response.
     */
    public synchronized void clear() {
        entries.clear();
        variants.clear();
        size = 0;
    }

    /**
     * @return The number of stored responses
     */
    public synchronized int count() {
        return entries.size();
    }

    /**
     * @return The bytes of the stored responses, their keys included
     */
    public synchronized long size() {
        return size;
    }

    /**
     * The key of a response.
     *
     * @param resource The resource
     * @param variant  The variant
     */
    private record Key(String resource, String variant) {
        /**
         * @return The bytes the key holds: two a character, and the overhead of the objects
         */
        long size() {
            return 2L * (resource.length() + variant.length()) + 96;
        }
    }
}
