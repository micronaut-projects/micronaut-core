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
import org.jspecify.annotations.Nullable;
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
 * through (fail open), unless {@link #failOpen()} is {@code false}: then it is answered with
 * {@code 503}. A route filter.
 *
 * <p>Create one with {@link #of(long, long)} or {@link #builder(long, long)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayRateLimit implements GatewayFilter {

    /**
     * The tokens left in the bucket.
     */
    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    /**
     * The replenish rate.
     */
    public static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";
    /**
     * The burst capacity.
     */
    public static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";
    /**
     * The tokens a request takes.
     */
    public static final String REQUESTED_TOKENS_HEADER = "X-RateLimit-Requested-Tokens";

    private static final Logger LOG = LoggerFactory.getLogger(GatewayRateLimit.class);
    /**
     * The decision of a limiter that failed, for a rate limit that does not fail open.
     */
    private static final GatewayRateLimiter.Decision LIMITER_FAILED = new GatewayRateLimiter.Decision(false, -1);

    private final GatewayRateLimiter.Limit limit;
    private final KeyResolver keyResolver;
    private final boolean denyEmptyKey;
    private final int emptyKeyStatus;
    private final boolean includeHeaders;
    private final boolean failOpen;
    private final GatewayRateLimiter limiter;

    private GatewayRateLimit(Builder builder) {
        this.limit = new GatewayRateLimiter.Limit(builder.replenishRate, builder.burstCapacity, builder.requestedTokens);
        this.keyResolver = builder.keyResolver;
        this.denyEmptyKey = builder.denyEmptyKey;
        this.emptyKeyStatus = builder.emptyKeyStatus;
        this.includeHeaders = builder.includeHeaders;
        this.failOpen = builder.failOpen;
        this.limiter = builder.limiter == null ? new InMemoryRateLimiter() : builder.limiter;
    }

    /**
     * A rate limit by the address of the client, in the memory of this gateway, with the headers.
     *
     * @param replenishRate The tokens added per second
     * @param burstCapacity The tokens a bucket holds
     * @return The rate limit
     */
    public static GatewayRateLimit of(long replenishRate, long burstCapacity) {
        return builder(replenishRate, burstCapacity).build();
    }

    /**
     * @param replenishRate The tokens added per second
     * @param burstCapacity The tokens a bucket holds, at least the replenish rate; {@code 0}
     *                      denies every request
     * @return A builder, by the address of the client, in the memory of this gateway, with the
     * headers
     */
    public static Builder builder(long replenishRate, long burstCapacity) {
        return new Builder(replenishRate, burstCapacity);
    }

    /**
     * @return A builder with the settings of this rate limit, and its limiter
     */
    public Builder toBuilder() {
        return new Builder(limit.replenishRate(), limit.burstCapacity())
            .requestedTokens(limit.requestedTokens())
            .keyResolver(keyResolver)
            .emptyKey(denyEmptyKey, emptyKeyStatus)
            .includeHeaders(includeHeaders)
            .failOpen(failOpen)
            .limiter(limiter);
    }

    /**
     * @return The tokens added per second
     */
    public long replenishRate() {
        return limit.replenishRate();
    }

    /**
     * @return The tokens a bucket holds; {@code 0} denies every request
     */
    public long burstCapacity() {
        return limit.burstCapacity();
    }

    /**
     * @return The tokens a request takes, default 1
     */
    public long requestedTokens() {
        return limit.requestedTokens();
    }

    /**
     * @return The limit of a bucket, as the {@link GatewayRateLimiter} takes it
     */
    public GatewayRateLimiter.Limit limit() {
        return limit;
    }

    /**
     * @return The key of a request
     */
    public KeyResolver keyResolver() {
        return keyResolver;
    }

    /**
     * @return Whether a request without a key is denied
     */
    public boolean denyEmptyKey() {
        return denyEmptyKey;
    }

    /**
     * @return The status of a request denied for its empty key
     */
    public int emptyKeyStatus() {
        return emptyKeyStatus;
    }

    /**
     * @return Whether the responses carry the rate limit headers
     */
    public boolean includeHeaders() {
        return includeHeaders;
    }

    /**
     * @return Whether a request is let through when the limiter fails, {@code true} by default;
     * otherwise it is answered with {@code 503}
     * @since 5.3.0
     */
    public boolean failOpen() {
        return failOpen;
    }

    /**
     * @return The store of the buckets
     */
    public GatewayRateLimiter limiter() {
        return limiter;
    }

    /**
     * @param keyResolver The key of a request
     * @return A copy with the key resolver
     */
    public GatewayRateLimit withKeyResolver(KeyResolver keyResolver) {
        return toBuilder().keyResolver(keyResolver).build();
    }

    /**
     * @param requestedTokens The tokens a request takes
     * @return A copy with the requested tokens
     */
    public GatewayRateLimit withRequestedTokens(long requestedTokens) {
        return toBuilder().requestedTokens(requestedTokens).build();
    }

    /**
     * @param denyEmptyKey   Whether a request without a key is denied
     * @param emptyKeyStatus The status it is answered with
     * @return A copy
     */
    public GatewayRateLimit withEmptyKey(boolean denyEmptyKey, int emptyKeyStatus) {
        return toBuilder().emptyKey(denyEmptyKey, emptyKeyStatus).build();
    }

    /**
     * @param includeHeaders Whether the responses carry the rate limit headers
     * @return A copy
     */
    public GatewayRateLimit withHeaders(boolean includeHeaders) {
        return toBuilder().includeHeaders(includeHeaders).build();
    }

    /**
     * @param limiter The store of the buckets
     * @return A copy
     */
    public GatewayRateLimit withLimiter(GatewayRateLimiter limiter) {
        return toBuilder().limiter(limiter).build();
    }

    @Override
    public void configure(RouteFilterSpec<?> route) {
        route.beforeReplacingAsync(request -> {
            String key = keyResolver.resolve(request);
            CompletionStage<GatewayRateLimiter.@Nullable Decision> decided;
            if (key == null || key.isEmpty()) {
                decided = CompletableFuture.completedFuture(null);
            } else {
                String routeId = GatewayRequestContext.routeId(request);
                CompletionStage<GatewayRateLimiter.Decision> decision;
                try {
                    decision = limiter.tryAcquire(new GatewayRateLimiter.BucketKey(routeId, key), limit);
                } catch (RuntimeException e) {
                    decision = CompletableFuture.failedFuture(e);
                }
                decided = decision.exceptionally(failure -> {
                    if (!failOpen) {
                        LOG.warn("The rate limiter of the gateway route {} failed, the request is refused: {}", routeId, failure.toString());
                        return LIMITER_FAILED;
                    }
                    // fail open: an unavailable store does not stop the traffic
                    LOG.warn("The rate limiter of the gateway route {} failed, the request is let through: {}", routeId, failure.toString());
                    return new GatewayRateLimiter.Decision(true, -1);
                });
            }
            return decided.thenApply(decision -> {
                if (decision == LIMITER_FAILED) {
                    return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE);
                }
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
        // set, not added: a value of the upstream is not read instead of the one of the gateway
        response.getHeaders().set(REMAINING_HEADER, String.valueOf(decision.remaining()));
        response.getHeaders().set(REPLENISH_RATE_HEADER, String.valueOf(limit.replenishRate()));
        response.getHeaders().set(BURST_CAPACITY_HEADER, String.valueOf(limit.burstCapacity()));
        response.getHeaders().set(REQUESTED_TOKENS_HEADER, String.valueOf(limit.requestedTokens()));
    }

    @Override
    public String toString() {
        return "RequestRateLimiter=" + limit.replenishRate() + ", " + limit.burstCapacity() + ", " + limit.requestedTokens();
    }

    /**
     * The builder of {@link GatewayRateLimit}.
     */
    public static final class Builder {
        /**
         * The most tokens a limit counts: the limiter counts nanotokens in a long.
         */
        private static final long MAX_TOKENS = Long.MAX_VALUE / 1_000_000_000L;
        private final long replenishRate;
        private final long burstCapacity;
        private long requestedTokens = 1;
        private KeyResolver keyResolver = KeyResolver.remoteAddress(List.of());
        private boolean denyEmptyKey = true;
        private int emptyKeyStatus = HttpStatus.FORBIDDEN.getCode();
        private boolean includeHeaders = true;
        private boolean failOpen = true;
        private @Nullable GatewayRateLimiter limiter;

        private Builder(long replenishRate, long burstCapacity) {
            if (replenishRate < 0 || burstCapacity < 0) {
                throw new IllegalArgumentException("A rate limit needs a replenish rate and a burst capacity of at least 0");
            }
            if (replenishRate == 0 && burstCapacity > 0) {
                // a bucket that never refills denies its key for good once it is empty
                throw new IllegalArgumentException("A rate limit with a burst capacity needs a replenish rate of at least 1");
            }
            if (replenishRate > MAX_TOKENS || burstCapacity > MAX_TOKENS) {
                throw new IllegalArgumentException("A rate limit counts at most " + MAX_TOKENS + " tokens");
            }
            if (burstCapacity > 0 && burstCapacity < replenishRate) {
                throw new IllegalArgumentException("The burst capacity " + burstCapacity + " of a rate limit is lower than its replenish rate " + replenishRate);
            }
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }

        /**
         * @param requestedTokens The tokens a request takes, at least 1
         * @return This builder
         */
        public Builder requestedTokens(long requestedTokens) {
            if (requestedTokens < 1 || requestedTokens > MAX_TOKENS) {
                throw new IllegalArgumentException("A rate limit needs requested tokens of at least 1 and at most " + MAX_TOKENS);
            }
            this.requestedTokens = requestedTokens;
            return this;
        }

        /**
         * @param keyResolver The key of a request
         * @return This builder
         */
        public Builder keyResolver(KeyResolver keyResolver) {
            this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
            return this;
        }

        /**
         * @param denyEmptyKey   Whether a request without a key is denied
         * @param emptyKeyStatus The status it is answered with
         * @return This builder
         */
        public Builder emptyKey(boolean denyEmptyKey, int emptyKeyStatus) {
            HttpStatus.valueOf(emptyKeyStatus);
            this.denyEmptyKey = denyEmptyKey;
            this.emptyKeyStatus = emptyKeyStatus;
            return this;
        }

        /**
         * @param includeHeaders Whether the responses carry the rate limit headers
         * @return This builder
         */
        public Builder includeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
            return this;
        }

        /**
         * @param failOpen Whether a request is let through when the limiter fails, see
         *                 {@link GatewayRateLimit#failOpen()}
         * @return This builder
         * @since 5.3.0
         */
        public Builder failOpen(boolean failOpen) {
            this.failOpen = failOpen;
            return this;
        }

        /**
         * @param limiter The store of the buckets, {@link InMemoryRateLimiter} by default
         * @return This builder
         */
        public Builder limiter(GatewayRateLimiter limiter) {
            this.limiter = Objects.requireNonNull(limiter, "limiter");
            return this;
        }

        /**
         * @return The rate limit
         */
        public GatewayRateLimit build() {
            return new GatewayRateLimit(this);
        }
    }
}
