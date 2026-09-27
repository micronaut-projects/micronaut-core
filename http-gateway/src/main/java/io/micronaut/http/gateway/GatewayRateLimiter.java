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
     * Take the requested tokens of a request from the bucket of its key.
     *
     * @param id    The id of the bucket: the route and the key of the request
     * @param limit The limit
     * @return The decision
     */
    CompletionStage<Decision> tryAcquire(String id, GatewayRateLimit limit);

    /**
     * The decision for a request.
     *
     * @param allowed   Whether the request is let through
     * @param remaining The tokens left in the bucket, {@code -1} when unknown
     */
    record Decision(boolean allowed, long remaining) {
    }
}
