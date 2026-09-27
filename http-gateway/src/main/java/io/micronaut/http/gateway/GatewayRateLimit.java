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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.builder.RouteFilterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A rate limit of the requests of a route, like Spring Cloud Gateway's
 * {@code RequestRateLimiter}: a token bucket per key of a request, see {@link KeyResolver}, that
 * holds up to {@link #burstCapacity()} tokens and refills at {@link #replenishRate()} tokens per
 * second. A request takes {@link #requestedTokens()} tokens; when the bucket has fewer, it is
 * answered with {@code 429} without calling the upstream. A request without a key is answered
 * with {@link #emptyKeyStatus()}, {@code 403} by default, unless {@link #denyEmptyKey()} is
 * {@code false}. The responses carry {@code X-RateLimit-Remaining},
 * {@code X-RateLimit-Replenish-Rate}, {@code X-RateLimit-Burst-Capacity} and
 * {@code X-RateLimit-Requested-Tokens}, unless {@link #includeHeaders()} is {@code false}. When
 * the {@link GatewayRateLimiter limiter} fails, e.g. its store is down, the request is let
 * through (fail open), with the headers. A route filter.
 *
 * @param replenishRate   The tokens added per second
 * @param burstCapacity   The tokens a bucket holds, at least the replenish rate; {@code 0}
 *                        denies every request
 * @param requestedTokens The tokens a request takes, default 1
 * @param keyResolver     The key of a request
 * @param denyEmptyKey    Whether a request without a key is denied
 * @param emptyKeyStatus  The status of a request denied for its empty key
 * @param includeHeaders  Whether the responses carry the rate limit headers
 * @param limiter         The store of the buckets
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record GatewayRateLimit(long replenishRate,
                               long burstCapacity,
                               long requestedTokens,
                               KeyResolver keyResolver,
                               boolean denyEmptyKey,
                               int emptyKeyStatus,
                               boolean includeHeaders,
                               GatewayRateLimiter limiter) implements GatewayFilter {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";
    public static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";
    public static final String REQUESTED_TOKENS_HEADER = "X-RateLimit-Requested-Tokens";

    private static final Logger LOG = LoggerFactory.getLogger(GatewayRateLimit.class);

    /**
     * @param replenishRate   The tokens added per second
     * @param burstCapacity   The tokens a bucket holds
     * @param requestedTokens The tokens a request takes
     * @param keyResolver     The key of a request
     * @param denyEmptyKey    Whether a request without a key is denied
     * @param emptyKeyStatus  The status of a request denied for its empty key
     * @param includeHeaders  Whether the responses carry the rate limit headers
     * @param limiter         The store of the buckets
     */
    public GatewayRateLimit {
        Objects.requireNonNull(keyResolver, "keyResolver");
        Objects.requireNonNull(limiter, "limiter");
        if (replenishRate < 0 || burstCapacity < 0 || requestedTokens < 1) {
            throw new IllegalArgumentException("A rate limit needs a replenish rate and a burst capacity of at least 0 and requested tokens of at least 1");
        }
        if (burstCapacity > 0 && burstCapacity < replenishRate) {
            throw new IllegalArgumentException("The burst capacity " + burstCapacity + " of a rate limit is lower than its replenish rate " + replenishRate);
        }
        HttpStatus.valueOf(emptyKeyStatus);
    }

    /**
     * A rate limit by the address of the client, in the memory of this gateway, with the headers.
     *
     * @param replenishRate The tokens added per second
     * @param burstCapacity The tokens a bucket holds
     * @return The rate limit
     */
    public static GatewayRateLimit of(long replenishRate, long burstCapacity) {
        return new GatewayRateLimit(replenishRate, burstCapacity, 1, KeyResolver.remoteAddress(List.of()), true, 403, true, new InMemoryRateLimiter());
    }

    /**
     * @param keyResolver The key of a request
     * @return A copy with the key resolver
     */
    public GatewayRateLimit withKeyResolver(KeyResolver keyResolver) {
        return new GatewayRateLimit(replenishRate, burstCapacity, requestedTokens, keyResolver, denyEmptyKey, emptyKeyStatus, includeHeaders, limiter);
    }

    /**
     * @param requestedTokens The tokens a request takes
     * @return A copy with the requested tokens
     */
    public GatewayRateLimit withRequestedTokens(long requestedTokens) {
        return new GatewayRateLimit(replenishRate, burstCapacity, requestedTokens, keyResolver, denyEmptyKey, emptyKeyStatus, includeHeaders, limiter);
    }

    /**
     * @param denyEmptyKey   Whether a request without a key is denied
     * @param emptyKeyStatus The status it is answered with
     * @return A copy
     */
    public GatewayRateLimit withEmptyKey(boolean denyEmptyKey, int emptyKeyStatus) {
        return new GatewayRateLimit(replenishRate, burstCapacity, requestedTokens, keyResolver, denyEmptyKey, emptyKeyStatus, includeHeaders, limiter);
    }

    /**
     * @param includeHeaders Whether the responses carry the rate limit headers
     * @return A copy
     */
    public GatewayRateLimit withHeaders(boolean includeHeaders) {
        return new GatewayRateLimit(replenishRate, burstCapacity, requestedTokens, keyResolver, denyEmptyKey, emptyKeyStatus, includeHeaders, limiter);
    }

    /**
     * @param limiter The store of the buckets
     * @return A copy
     */
    public GatewayRateLimit withLimiter(GatewayRateLimiter limiter) {
        return new GatewayRateLimit(replenishRate, burstCapacity, requestedTokens, keyResolver, denyEmptyKey, emptyKeyStatus, includeHeaders, limiter);
    }

    @Override
    public void configure(RouteFilterSpec<?> route) {
        route.beforeReplacingAsync(request -> {
            CompletionStage<GatewayRateLimiter.Decision> decided = keyResolver.resolve(request).thenCompose(key -> {
                if (key == null || key.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                String routeId = GatewayRequestContext.routeId(request);
                CompletionStage<GatewayRateLimiter.Decision> decision;
                try {
                    decision = limiter.tryAcquire(routeId + "." + key, this);
                } catch (RuntimeException e) {
                    decision = CompletableFuture.failedFuture(e);
                }
                return decision.exceptionally(failure -> {
                    // fail open: an unavailable store does not stop the traffic
                    LOG.warn("The rate limiter of the gateway route {} failed, the request is let through: {}", routeId, failure.toString());
                    return new GatewayRateLimiter.Decision(true, -1);
                });
            });
            return decided.thenApply(decision -> {
                if (decision == null) {
                    if (denyEmptyKey) {
                        return HttpResponse.status(HttpStatus.valueOf(emptyKeyStatus));
                    }
                    return null;
                }
                if (!decision.allowed()) {
                    MutableHttpResponse<?> tooMany = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS);
                    headers(tooMany, decision);
                    return tooMany;
                }
                GatewayRequestContext.forFilters(request).state(this, decision);
                return null;
            });
        });
        if (includeHeaders) {
            route.after((request, response) -> {
                GatewayRateLimiter.Decision decision = GatewayRequestContext.forFilters(request).state(this, GatewayRateLimiter.Decision.class);
                if (decision != null) {
                    headers(response, decision);
                }
            });
        }
    }

    private void headers(MutableHttpResponse<?> response, GatewayRateLimiter.Decision decision) {
        if (!includeHeaders) {
            return;
        }
        response.header(REMAINING_HEADER, String.valueOf(decision.remaining()));
        response.header(REPLENISH_RATE_HEADER, String.valueOf(replenishRate));
        response.header(BURST_CAPACITY_HEADER, String.valueOf(burstCapacity));
        response.header(REQUESTED_TOKENS_HEADER, String.valueOf(requestedTokens));
    }

    @Override
    public String toString() {
        return "RequestRateLimiter=" + replenishRate + ", " + burstCapacity + ", " + requestedTokens;
    }
}
