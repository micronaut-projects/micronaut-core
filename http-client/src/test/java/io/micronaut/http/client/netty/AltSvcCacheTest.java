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
package io.micronaut.http.client.netty;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltSvcCacheTest {
    private static final NettyHttpClient.RequestKey ORIGIN = new NettyHttpClient.RequestKey("example.com", 443, true);

    private final AtomicLong now = new AtomicLong(1_000);
    private final List<AltSvcCache.Route> retired = new ArrayList<>();
    private final AltSvcCache cache = new AltSvcCache(Duration.ofMinutes(5), now::get, retired::add);

    @Test
    void parsesAlternatives() {
        List<AltSvcCache.AltValue> values = AltSvcCache.parse("h3=\":443\"; ma=3600; persist=1, h2=\"alt.example.com:8443\", h3=\"[::1]:9443\"");
        assertEquals(List.of(
            new AltSvcCache.AltValue("h3", null, 443, 3600),
            new AltSvcCache.AltValue("h2", "alt.example.com", 8443, AltSvcCache.DEFAULT_MAX_AGE_SECONDS),
            new AltSvcCache.AltValue("h3", "::1", 9443, AltSvcCache.DEFAULT_MAX_AGE_SECONDS)
        ), values);
    }

    @Test
    void parsesPercentEncodedProtocolIdsAndQuotedParameters() {
        assertEquals(
            List.of(new AltSvcCache.AltValue("w=x:y#z", null, 80, 10)),
            AltSvcCache.parse("w%3Dx%3Ay#z=\":80\";ma=\"10\""));
    }

    @Test
    void skipsMalformedAlternatives() {
        assertEquals(
            List.of(new AltSvcCache.AltValue("h3", null, 443, AltSvcCache.DEFAULT_MAX_AGE_SECONDS)),
            AltSvcCache.parse("h3=\":0\", h3=\":70000\", h3=\"no-port\", h3=:443, =\":1\", h3=\":443\", h3=\":443\"; ma=-1"));
        assertTrue(AltSvcCache.parse("clear").isEmpty());
        assertTrue(AltSvcCache.parse("").isEmpty());
    }

    @Test
    void picksTheFirstHttp3AlternativeOnTheOriginHost() {
        cache.update(ORIGIN, List.of("h2=\":8443\", h3=\"alt.example.com:9443\", h3=\"EXAMPLE.com:7443\", h3=\":443\""), 0);
        assertEquals(new AltSvcCache.Route(ORIGIN, "example.com", 7443), cache.lookup(ORIGIN));
    }

    @Test
    void alternativesOnOtherHostsAreIgnored() {
        cache.update(ORIGIN, List.of("h3=\"127.0.0.1:443\", h3=\"alt.example.com:443\""), 0);
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void emptyHostIsTheOriginHost() {
        cache.update(ORIGIN, List.of("h3=\":9443\""), 0);
        assertEquals(new AltSvcCache.Route(ORIGIN, "example.com", 9443), cache.lookup(ORIGIN));
    }

    @Test
    void alternativeExpiresAfterMaxAge() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 0);
        now.addAndGet(TimeUnit.SECONDS.toNanos(59));
        assertNotNull(cache.lookup(ORIGIN));
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void newHeaderReplacesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        cache.update(ORIGIN, List.of("h2=\":443\""), 0);
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void clearRemovesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        cache.update(ORIGIN, List.of("clear"), 0);
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void zeroMaxAgeRemovesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=0"), 0);
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void brokenAlternativeIsSkippedForAWhile() {
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        AltSvcCache.Route route = cache.lookup(ORIGIN);
        assertNotNull(route);
        cache.markBroken(route);
        assertNull(cache.lookup(ORIGIN));
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertEquals(route, cache.lookup(ORIGIN));
    }

    @Test
    void originsAreSeparate() {
        NettyHttpClient.RequestKey other = new NettyHttpClient.RequestKey("example.com", 8443, true);
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        assertNull(cache.lookup(other));
    }

    @Test
    void maxAgeCountsFromTheAgeOfTheResponse() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 50);
        now.addAndGet(TimeUnit.SECONDS.toNanos(9));
        assertNotNull(cache.lookup(ORIGIN));
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void responseOlderThanTheMaxAgeRemovesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 60);
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void evictRemovesOnlyThatAlternative() {
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        cache.evict(new AltSvcCache.Route(ORIGIN, "example.com", 8443));
        AltSvcCache.Route route = cache.lookup(ORIGIN);
        assertNotNull(route);
        cache.evict(route);
        assertNull(cache.lookup(ORIGIN));
        assertEquals(List.of(route), retired);
    }

    @Test
    void replacedClearedAndExpiredAlternativesAreRetired() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 0);
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=120"), 0);
        assertTrue(retired.isEmpty(), "same endpoint, only the max age changed");
        cache.update(ORIGIN, List.of("h3=\":8443\""), 0);
        assertEquals(List.of(new AltSvcCache.Route(ORIGIN, "example.com", 443)), retired);
        cache.update(ORIGIN, List.of("clear"), 0);
        assertEquals(new AltSvcCache.Route(ORIGIN, "example.com", 8443), retired.get(1));
        cache.update(ORIGIN, List.of("h3=\":9443\"; ma=1"), 0);
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertNull(cache.lookup(ORIGIN));
        assertEquals(new AltSvcCache.Route(ORIGIN, "example.com", 9443), retired.get(2));
    }

    @Test
    void maxAgeIsCapped() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=99999999999"), 0);
        now.addAndGet(TimeUnit.SECONDS.toNanos(AltSvcCache.MAX_MAX_AGE_SECONDS));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void sameAdvertisementRefreshesTheMaxAge() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 0);
        now.addAndGet(TimeUnit.SECONDS.toNanos(50));
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 0);
        now.addAndGet(TimeUnit.SECONDS.toNanos(50));
        assertNotNull(cache.lookup(ORIGIN));
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"), 60);
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void repeatedFailuresBackOffExponentially() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=86400"), 0);
        AltSvcCache.Route route = cache.lookup(ORIGIN);
        assertNotNull(route);
        cache.markBroken(route);
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertEquals(route, cache.lookup(ORIGIN));
        cache.markBroken(route);
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertNull(cache.lookup(ORIGIN), "the second failure keeps it out for 10 minutes");
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertEquals(route, cache.lookup(ORIGIN));
        cache.confirm(route);
        cache.markBroken(route);
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertEquals(route, cache.lookup(ORIGIN), "a response in between ends the backoff");
    }

    @Test
    void cachesAreBounded() {
        for (int i = 0; i < AltSvcCache.MAX_ORIGINS; i++) {
            NettyHttpClient.RequestKey origin = new NettyHttpClient.RequestKey("host" + i + ".example.com", 443, true);
            cache.update(origin, List.of("h3=\":443\"; ma=" + (1000 + i)), 0);
            cache.markBroken(new AltSvcCache.Route(origin, origin.getHost(), 443));
        }
        // a new origin evicts the alternative that expires first, rather than being refused
        cache.update(ORIGIN, List.of("h3=\":443\""), 0);
        AltSvcCache.Route route = cache.lookup(ORIGIN);
        assertNotNull(route);
        assertTrue(retired.contains(new AltSvcCache.Route(new NettyHttpClient.RequestKey("host0.example.com", 443, true), "host0.example.com", 443)));
        cache.markBroken(route);
        assertNull(cache.lookup(ORIGIN), "the failure is remembered although the map was full");
    }
}
