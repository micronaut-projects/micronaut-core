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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rate limits (RL-01 to RL-07) and the in-flight limit of gateway routes.
 */
class RateLimitTest extends GatewayTestBase {

    private static final GatewayRateLimiter.BucketKey K = new GatewayRateLimiter.BucketKey("r", "k");

    static final String SPEC = "RateLimitTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "5s");
    }

    @Test
    void rl01OverTheLimitIs429AndTheResponsesCarryTheHeaders() throws IOException {
        RawHttp.Response first = get("/rl/inspect/x", "X-User: a");
        assertEquals(200, first.status(), first.toString());
        // a token a second, a request takes 30 of 60: two requests a minute
        assertEquals("30", first.header("X-RateLimit-Remaining"));
        assertEquals("1", first.header("X-RateLimit-Replenish-Rate"));
        assertEquals("60", first.header("X-RateLimit-Burst-Capacity"));
        assertEquals("30", first.header("X-RateLimit-Requested-Tokens"));
        assertEquals("0", get("/rl/inspect/x", "X-User: a").header("X-RateLimit-Remaining"));
        int before = counters().get("inspect");
        RawHttp.Response denied = get("/rl/inspect/x", "X-User: a");
        assertEquals(429, denied.status(), denied.toString());
        assertEquals("0", denied.header("X-RateLimit-Remaining"));
        assertEquals(before, counters().get("inspect"), "the upstream was called");
        // another key has its own bucket
        assertEquals(200, get("/rl/inspect/x", "X-User: b").status());
    }

    @Test
    void rl02AnEmptyKeyIsDeniedWith403UnlessAllowed() throws IOException {
        assertEquals(403, get("/rl/inspect/x").status());
        assertEquals(200, get("/rl-empty-allowed/inspect/x").status());
    }

    @Test
    void rl03AFailingLimiterLetsTheRequestThroughWithTheHeaders() throws IOException {
        for (int i = 0; i < 3; i++) {
            RawHttp.Response response = get("/rl-failing/inspect/x", "X-User: a");
            assertEquals(200, response.status(), response.toString());
            assertEquals("-1", response.header("X-RateLimit-Remaining"));
        }
    }

    @Test
    void rl04AZeroBurstDeniesEveryRequestAndABurstBelowTheRateIsInvalid() throws IOException {
        assertEquals(429, get("/rl-zero/inspect/x", "X-User: a").status());
        assertThrows(IllegalArgumentException.class, () -> GatewayRateLimit.of(10, 5));
    }

    @Test
    void rl05TheBucketRefillsAtTheReplenishRate() throws Exception {
        AtomicLong now = new AtomicLong();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);
        GatewayRateLimit limit = GatewayRateLimit.of(2, 4).withLimiter(limiter);
        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.tryAcquire(K, limit.limit()).toCompletableFuture().get().allowed(), "token " + i);
        }
        assertFalse(limiter.tryAcquire(K, limit.limit()).toCompletableFuture().get().allowed(), "the bucket is empty");
        // half a second: one token
        now.addAndGet(TimeUnit.MILLISECONDS.toNanos(500));
        GatewayRateLimiter.Decision refilled = limiter.tryAcquire(K, limit.limit()).toCompletableFuture().get();
        assertTrue(refilled.allowed());
        assertEquals(0, refilled.remaining());
        // never more than the burst capacity
        now.addAndGet(TimeUnit.SECONDS.toNanos(60));
        assertEquals(3, limiter.tryAcquire(K, limit.limit()).toCompletableFuture().get().remaining());
        // a request that takes more tokens than there are is denied
        GatewayRateLimit expensive = limit.withRequestedTokens(4);
        assertFalse(limiter.tryAcquire(K, expensive.limit()).toCompletableFuture().get().allowed());
    }

    @Test
    void rl06KeyResolvers() throws Exception {
        // the client behind a trusted proxy
        assertEquals(200, get("/rl-ip/inspect/x", "X-Forwarded-For: 192.0.2.1").status());
        assertEquals(429, get("/rl-ip/inspect/x", "X-Forwarded-For: 192.0.2.1").status());
        assertEquals(200, get("/rl-ip/inspect/x", "X-Forwarded-For: 192.0.2.2").status());
        // the principal
        HttpRequest<?> request = HttpRequest.GET("/x").setAttribute(HttpAttributes.PRINCIPAL, (Principal) () -> "alice");
        assertEquals("alice", KeyResolver.principalName().resolve(request));
        assertNull(KeyResolver.principalName().resolve(HttpRequest.GET("/x")));
        // a header
        assertEquals("t1", KeyResolver.header("X-Tenant").resolve(HttpRequest.GET("/x").header("X-Tenant", "t1")));
    }

    @Test
    void rl07TheHeadersCanBeTurnedOff() throws IOException {
        RawHttp.Response response = get("/rl-no-headers/inspect/x", "X-User: a");
        assertEquals(200, response.status());
        assertNull(response.header("X-RateLimit-Remaining"));
        RawHttp.Response denied = get("/rl-no-headers/inspect/x", "X-User: a");
        assertEquals(429, denied.status());
        assertNull(denied.header("X-RateLimit-Remaining"));
    }

    @Test
    void aBulkheadLimitsTheRequestsOfAKeyInFlight() throws Exception {
        CompletableFuture<RawHttp.Response> slow = CompletableFuture.supplyAsync(() -> {
            try {
                return get("/bulkhead/up/slow?ms=1000", "X-User: a");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(300);
        assertEquals(429, get("/bulkhead/up/slow?ms=0", "X-User: a").status(), "the key has a request in flight");
        assertEquals(200, get("/bulkhead/up/slow?ms=0", "X-User: b").status(), "another key");
        assertEquals(200, slow.get(10, TimeUnit.SECONDS).status());
        assertEquals(200, get("/bulkhead/up/slow?ms=0", "X-User: a").status(), "the request in flight ended");
    }

    @Test
    void theBucketsAreCappedAndAnIpv6ClientIsKeyedByItsNetwork() throws Exception {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new AtomicLong()::get, 2);
        GatewayRateLimiter.Limit limit = GatewayRateLimit.of(1, 1).limit();
        assertTrue(limiter.tryAcquire(new GatewayRateLimiter.BucketKey("r", "a"), limit).toCompletableFuture().get().allowed());
        assertTrue(limiter.tryAcquire(new GatewayRateLimiter.BucketKey("r", "b"), limit).toCompletableFuture().get().allowed());
        assertFalse(limiter.tryAcquire(new GatewayRateLimiter.BucketKey("r", "c"), limit).toCompletableFuture().get().allowed(), "no bucket is left");
        assertEquals(2, limiter.size());
        HttpRequest<?> one = new io.micronaut.http.HttpRequestWrapper<Object>(HttpRequest.GET("/x")) {
            @Override
            public java.net.InetSocketAddress getRemoteAddress() {
                return new java.net.InetSocketAddress("2001:db8:1:2:aaaa::1", 1);
            }
        };
        HttpRequest<?> two = new io.micronaut.http.HttpRequestWrapper<Object>(HttpRequest.GET("/x")) {
            @Override
            public java.net.InetSocketAddress getRemoteAddress() {
                return new java.net.InetSocketAddress("2001:db8:1:2:bbbb::2", 1);
            }
        };
        KeyResolver byAddress = KeyResolver.remoteAddress(List.of());
        assertEquals(byAddress.resolve(one), byAddress.resolve(two));
        assertEquals("2001:db8:1:2:0:0:0:0/64", byAddress.resolve(one));
    }

    @Test
    void rl08TheBucketsOfTwoRoutesNeverCollide() throws Exception {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        GatewayRateLimit limit = GatewayRateLimit.of(1, 1).withLimiter(limiter);
        GatewayRateLimiter.BucketKey first = new GatewayRateLimiter.BucketKey("a.b", "c");
        GatewayRateLimiter.BucketKey second = new GatewayRateLimiter.BucketKey("a", "b.c");
        assertNotEquals(first.id(), second.id());
        assertTrue(limiter.tryAcquire(first, limit.limit()).toCompletableFuture().get().allowed());
        assertTrue(limiter.tryAcquire(second, limit.limit()).toCompletableFuture().get().allowed(), "another bucket");
        assertFalse(limiter.tryAcquire(first, limit.limit()).toCompletableFuture().get().allowed());
        // a limiter keyed by strings is given the id
        List<String> ids = new java.util.concurrent.CopyOnWriteArrayList<>();
        GatewayRateLimiter byId = (key, l) -> {
            ids.add(key.id());
            return CompletableFuture.completedFuture(new GatewayRateLimiter.Decision(true, 0));
        };
        byId.tryAcquire(first, limit.limit()).toCompletableFuture().get();
        byId.tryAcquire(second, limit.limit()).toCompletableFuture().get();
        assertEquals(List.of("3:a.b:c", "1:a:b.c"), ids);
    }

    @Test
    void rl09TheBucketsThatRefilledAreRemoved() throws Exception {
        AtomicLong now = new AtomicLong();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);
        GatewayRateLimit limit = GatewayRateLimit.of(10, 10).withLimiter(limiter);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire(new GatewayRateLimiter.BucketKey("r", "k" + i), limit.limit()).toCompletableFuture().get();
        }
        assertEquals(100, limiter.size());
        // a bucket that took a token refills in a tenth of a second
        now.addAndGet(TimeUnit.SECONDS.toNanos(2));
        GatewayRateLimiter.Decision decision = limiter.tryAcquire(new GatewayRateLimiter.BucketKey("r", "k0"), limit.limit()).toCompletableFuture().get();
        assertEquals(1, limiter.size(), "only the bucket just taken from is left");
        assertEquals(9, decision.remaining(), "a removed bucket is full again");
    }

    @Test
    void aRequestTakesThePermitsOfTwoBulkheadsOfARoute() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(200, get("/bulkhead2/up/slow?ms=0", "X-User: a", "X-Tenant: t").status(), "request " + i);
        }
        assertEquals(0, ((GatewayFilters.Bulkhead) Routes.USER_BULKHEAD).keys(), "every permit was released");
    }

    @Test
    void aBulkheadSharedByTwoRoutesLimitsEachRoute() throws Exception {
        CompletableFuture<RawHttp.Response> slow = CompletableFuture.supplyAsync(() -> {
            try {
                return get("/shared-a/up/slow?ms=1000", "X-User: s");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(300);
        assertEquals(429, get("/shared-a/up/slow?ms=0", "X-User: s").status(), "the key has a request in flight on the route");
        assertEquals(200, get("/shared-b/up/slow?ms=0", "X-User: s").status(), "the other route has its own limit");
        assertEquals(200, slow.get(10, TimeUnit.SECONDS).status());
        assertEquals(0, ((GatewayFilters.Bulkhead) Routes.SHARED_BULKHEAD).keys(), "a key without requests in flight is removed");
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        static final GatewayFilter USER_BULKHEAD = GatewayFilters.bulkhead(1, KeyResolver.header("X-User"));
        static final GatewayFilter SHARED_BULKHEAD = GatewayFilters.bulkhead(1, KeyResolver.header("X-User"));
        private final String upstream;

        Routes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            KeyResolver user = KeyResolver.header("X-User");
            gateway.route("rl").path("/rl/**").filters(GatewayFilters.stripPrefix(1), GatewayRateLimit.of(1, 60).withRequestedTokens(30).withKeyResolver(user)).uri(upstream);
            gateway.route("rl-empty-allowed").path("/rl-empty-allowed/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayRateLimit.of(1, 2).withKeyResolver(user).withEmptyKey(false, 403)).uri(upstream);
            gateway.route("rl-failing").path("/rl-failing/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayRateLimit.of(1, 1).withKeyResolver(user)
                    .withLimiter((id, limit) -> CompletableFuture.failedFuture(new IllegalStateException("the store is down")))).uri(upstream);
            gateway.route("rl-zero").path("/rl-zero/**").filters(GatewayFilters.stripPrefix(1), GatewayRateLimit.of(0, 0).withKeyResolver(user)).uri(upstream);
            gateway.route("rl-ip").path("/rl-ip/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayRateLimit.of(1, 60).withRequestedTokens(60).withKeyResolver(KeyResolver.remoteAddress(List.of("127.0.0.1/32", "::1/128")))).uri(upstream);
            gateway.route("rl-no-headers").path("/rl-no-headers/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayRateLimit.of(1, 60).withRequestedTokens(60).withKeyResolver(user).withHeaders(false)).uri(upstream);
            gateway.route("bulkhead").path("/bulkhead/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.bulkhead(1, user))
                .responseTimeout(Duration.ofSeconds(5)).uri(upstream);
            gateway.route("bulkhead2").path("/bulkhead2/**")
                .filters(GatewayFilters.stripPrefix(1), USER_BULKHEAD, GatewayFilters.bulkhead(1, KeyResolver.header("X-Tenant")))
                .responseTimeout(Duration.ofSeconds(5)).uri(upstream);
            gateway.route("shared-a").path("/shared-a/**").filters(GatewayFilters.stripPrefix(1), SHARED_BULKHEAD)
                .responseTimeout(Duration.ofSeconds(5)).uri(upstream);
            gateway.route("shared-b").path("/shared-b/**").filters(GatewayFilters.stripPrefix(1), SHARED_BULKHEAD)
                .responseTimeout(Duration.ofSeconds(5)).uri(upstream);
        }
    }
}
