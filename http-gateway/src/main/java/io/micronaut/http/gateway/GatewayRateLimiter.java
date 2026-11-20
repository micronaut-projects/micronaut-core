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

import java.util.concurrent.CompletionStage;

/**
 * Where a {@link GatewayRateLimit} keeps its token buckets: {@link InMemoryRateLimiter} by
 * default, or a shared store, e.g. Redis, as a bean named in the configuration. A limiter that
 * fails lets the request through (fail open).
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface GatewayRateLimiter {

    /**
     * Take the requested tokens of a request from the bucket of its route and key. A store keyed
     * by strings uses {@link BucketKey#id()}.
     *
     * @param key   The bucket: the route and the key of the request
     * @param limit The limit of the bucket
     * @return The decision
     */
    CompletionStage<Decision> tryAcquire(BucketKey key, Limit limit);

    /**
     * The bucket of a request: a key of one route never shares the bucket of another route,
     * whatever the ids of the routes and the keys, e.g. the route {@code a.b} and the key
     * {@code c}, and the route {@code a} and the key {@code b.c}.
     *
     * @param routeId The id of the route
     * @param key     The key of the request, see {@link KeyResolver}
     */
    record BucketKey(String routeId, String key) {

        /**
         * @return The id of the bucket as a string, for a store keyed by strings: the length of
         * the route id comes first, so that no two buckets have the same id
         */
        public String id() {
            return routeId.length() + ":" + routeId + ":" + key;
        }
    }

    /**
     * The limit of a bucket, see {@link GatewayRateLimit}.
     *
     * @param replenishRate   The tokens added per second
     * @param burstCapacity   The tokens a bucket holds; {@code 0} denies every request
     * @param requestedTokens The tokens a request takes
     */
    record Limit(long replenishRate, long burstCapacity, long requestedTokens) {
    }

    /**
     * The decision for a request.
     *
     * @param allowed   Whether the request is let through
     * @param remaining The tokens left in the bucket, {@code -1} when unknown
     */
    record Decision(boolean allowed, long remaining) {
    }
}
