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

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltSvcCacheTest {
    private static final NettyHttpClient.RequestKey ORIGIN = new NettyHttpClient.RequestKey(null, URI.create("https://example.com"));

    private final AtomicLong now = new AtomicLong(1_000);
    private final AltSvcCache cache = new AltSvcCache(Duration.ofMinutes(5), now::get);

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
    void picksTheFirstHttp3Alternative() {
        cache.update(ORIGIN, List.of("h2=\":8443\", h3=\"alt.example.com:9443\", h3=\":443\""));
        AltSvcCache.Route route = cache.lookup(ORIGIN);
        assertEquals(new AltSvcCache.Route(ORIGIN, "alt.example.com", 9443), route);
    }

    @Test
    void emptyHostIsTheOriginHost() {
        cache.update(ORIGIN, List.of("h3=\":9443\""));
        assertEquals(new AltSvcCache.Route(ORIGIN, "example.com", 9443), cache.lookup(ORIGIN));
    }

    @Test
    void alternativeExpiresAfterMaxAge() {
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=60"));
        now.addAndGet(TimeUnit.SECONDS.toNanos(59));
        assertNotNull(cache.lookup(ORIGIN));
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void newHeaderReplacesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""));
        cache.update(ORIGIN, List.of("h2=\":443\""));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void clearRemovesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""));
        cache.update(ORIGIN, List.of("clear"));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void zeroMaxAgeRemovesAlternatives() {
        cache.update(ORIGIN, List.of("h3=\":443\""));
        cache.update(ORIGIN, List.of("h3=\":443\"; ma=0"));
        assertNull(cache.lookup(ORIGIN));
    }

    @Test
    void brokenAlternativeIsSkippedForAWhile() {
        cache.update(ORIGIN, List.of("h3=\":443\""));
        AltSvcCache.Route route = cache.lookup(ORIGIN);
        assertNotNull(route);
        cache.markBroken(route);
        assertNull(cache.lookup(ORIGIN));
        now.addAndGet(TimeUnit.MINUTES.toNanos(5));
        assertEquals(route, cache.lookup(ORIGIN));
    }

    @Test
    void originsAreSeparate() {
        NettyHttpClient.RequestKey other = new NettyHttpClient.RequestKey(null, URI.create("https://example.com:8443"));
        cache.update(ORIGIN, List.of("h3=\":443\""));
        assertNull(cache.lookup(other));
    }
}
