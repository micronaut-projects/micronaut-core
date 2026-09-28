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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The response cache of gateway routes (CA-01 to CA-08, UP-19).
 */
class ResponseCacheTest extends GatewayTestBase {

    static final String SPEC = "ResponseCacheTest";
    static final AtomicLong CLOCK = new AtomicLong(1_000_000);

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "5s");
    }

    @BeforeEach
    void clearStore() {
        gateway.getApplicationContext().getBean(Routes.class).store.clear();
        calls.clear();
    }

    /**
     * An upstream path that answers with the headers, a body naming the call, and counts the calls.
     */
    private void answer(String path, String... headers) {
        scripted.on(path, (request, in, out, socket) -> {
            request.readBody(in);
            int n = calls.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            String body = "call " + n;
            StringBuilder response = new StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ").append(body.length()).append("\r\n");
            for (String header : headers) {
                response.append(header).append("\r\n");
            }
            ScriptedUpstream.write(out, response.append("\r\n").append(request.method().equals("HEAD") ? "" : body).toString());
            return true;
        });
    }

    private int calls(String path) {
        AtomicInteger n = calls.get(path);
        return n == null ? 0 : n.get();
    }

    private static String httpDate(long epochMillis) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC));
    }

    @Test
    void ca01AFreshResponseIsServedFromTheCacheUntilItExpires() throws IOException {
        answer("/ca01", "Cache-Control: public, max-age=10");
        assertEquals("call 1", get("/cached/ca01").text());
        RawHttp.Response cached = get("/cached/ca01");
        assertEquals("call 1", cached.text());
        assertEquals("0", cached.header("Age"));
        assertEquals(1, calls("/ca01"));
        CLOCK.addAndGet(11_000);
        assertEquals("call 2", get("/cached/ca01").text());

        answer("/expires", "Expires: " + httpDate(System.currentTimeMillis() + 3_600_000));
        get("/cached-real-clock/expires");
        assertEquals("call 1", get("/cached-real-clock/expires").text());
    }

    @Test
    void ca02AConditionalRequestIsAnswered304FromTheCache() throws IOException {
        answer("/ca02", "Cache-Control: max-age=60", "ETag: \"v1\"", "Last-Modified: Mon, 01 Jan 2024 00:00:00 GMT");
        get("/cached/ca02");
        RawHttp.Response etag = get("/cached/ca02", "If-None-Match: \"v1\"");
        assertEquals(304, etag.status(), etag.toString());
        assertEquals("\"v1\"", etag.header("ETag"));
        RawHttp.Response since = get("/cached/ca02", "If-Modified-Since: Tue, 02 Jan 2024 00:00:00 GMT");
        assertEquals(304, since.status(), since.toString());
        assertEquals(200, get("/cached/ca02", "If-None-Match: \"v0\"").status());
        assertEquals(1, calls("/ca02"));
        // the client revalidates
        assertEquals("call 2", get("/cached/ca02", "Cache-Control: max-age=0").text());
    }

    @Test
    void ca03PrivateNoStoreAndARequestNoCacheBypassTheCache() throws IOException {
        answer("/private", "Cache-Control: private, max-age=60");
        get("/cached/private");
        assertEquals("call 2", get("/cached/private").text());
        answer("/nostore", "Cache-Control: no-store");
        get("/cached/nostore");
        assertEquals("call 2", get("/cached/nostore").text());
        answer("/ca03", "Cache-Control: max-age=60");
        get("/cached/ca03");
        assertEquals("call 2", get("/cached/ca03", "Cache-Control: no-cache").text());
        assertEquals("call 3", get("/cached/ca03", "Pragma: no-cache").text());
        // a request no-store is not stored
        answer("/reqnostore", "Cache-Control: max-age=60");
        get("/cached/reqnostore", "Cache-Control: no-store");
        assertEquals("call 2", get("/cached/reqnostore").text());
    }

    @Test
    void ca04UsersAndVariantsDoNotShareEntries() throws IOException {
        answer("/users", "Cache-Control: public, max-age=60");
        assertEquals("call 1", get("/cached/users", "Authorization: Bearer a").text());
        assertEquals("call 2", get("/cached/users", "Authorization: Bearer b").text());
        assertEquals("call 1", get("/cached/users", "Authorization: Bearer a").text());
        assertEquals("call 3", get("/cached/users", "Cookie: s=1").text());
        // without public, a request with Authorization is not stored
        answer("/auth", "Cache-Control: max-age=60");
        get("/cached/auth", "Authorization: Bearer a");
        assertEquals("call 2", get("/cached/auth", "Authorization: Bearer a").text());
        answer("/vary", "Cache-Control: max-age=60", "Vary: Accept-Language");
        assertEquals("call 1", get("/cached/vary", "Accept-Language: en").text());
        assertEquals("call 2", get("/cached/vary", "Accept-Language: fr").text());
        assertEquals("call 1", get("/cached/vary", "Accept-Language: en").text());
        answer("/varyall", "Cache-Control: max-age=60", "Vary: *");
        get("/cached/varyall");
        assertEquals("call 2", get("/cached/varyall").text());
        answer("/cookie", "Cache-Control: max-age=60", "Set-Cookie: s=1");
        get("/cached/cookie");
        assertEquals("call 2", get("/cached/cookie").text());
    }

    @Test
    void ca05HeadDoesNotPopulateAndAnUnsafeOrUncacheableResponseInvalidates() throws IOException {
        answer("/ca05", "Cache-Control: max-age=60");
        RawHttp.Response head = RawHttp.exchange(gateway.getPort(), "HEAD /cached/ca05 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII), true);
        assertEquals(200, head.status(), head.toString());
        assertEquals("call 2", get("/cached/ca05").text());
        assertEquals("call 2", get("/cached/ca05").text());
        // a HEAD is served from the cache
        RawHttp.Response cachedHead = RawHttp.exchange(gateway.getPort(), "HEAD /cached/ca05 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII), true);
        assertEquals(200, cachedHead.status());
        assertEquals(2, calls("/ca05"));
        // a POST invalidates
        send("POST", "/cached/ca05", "x", "Content-Type: text/plain");
        assertEquals("call 4", get("/cached/ca05").text());
        // an uncacheable response replaces the stored one
        answer("/ca05", "Cache-Control: no-store");
        get("/cached/ca05", "Cache-Control: no-cache");
        answer("/ca05", "Cache-Control: max-age=60");
        assertEquals("call 6", get("/cached/ca05").text());
    }

    @Test
    void ca06TheAgeGrowsWhileTheResponseIsServed() throws IOException {
        answer("/ca06", "Cache-Control: max-age=100", "Age: 5");
        get("/cached/ca06");
        CLOCK.addAndGet(3_000);
        assertEquals("8", get("/cached/ca06").header("Age"));
        CLOCK.addAndGet(10_000);
        assertEquals("18", get("/cached/ca06").header("Age"));
        // the client accepts at most 10 seconds
        assertEquals("call 2", get("/cached/ca06", "Cache-Control: max-age=10").text());
    }

    @Test
    void ca07AnIncompleteOrTooLargeResponseIsNotCached() throws IOException {
        AtomicInteger cut = new AtomicInteger();
        scripted.on("/cut", (request, in, out, socket) -> {
            cut.incrementAndGet();
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nCache-Control: max-age=60\r\nContent-Length: 10\r\n\r\nhalf");
            return false;
        });
        get("/cached/cut");
        get("/cached/cut");
        assertEquals(2, cut.get());
        answer("/big", "Cache-Control: max-age=60");
        scripted.on("/big", (request, in, out, socket) -> {
            calls.computeIfAbsent("/big", p -> new AtomicInteger()).incrementAndGet();
            String body = "b".repeat(2000);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nCache-Control: max-age=60\r\nContent-Length: " + body.length() + "\r\n\r\n" + body);
            return true;
        });
        assertEquals(2000, get("/cached/big").body().length);
        assertEquals(2000, get("/cached/big").body().length);
        assertEquals(2, calls("/big"));
    }

    @Test
    void ca08AndUp19RangeAndUpgradeRequestsBypassTheCache() throws IOException {
        answer("/range", "Cache-Control: max-age=60");
        get("/cached/range");
        assertEquals("call 2", get("/cached/range", "Range: bytes=0-1").text());
        answer("/upgrade", "Cache-Control: max-age=60");
        get("/cached/upgrade", "Connection: Upgrade", "Upgrade: websocket");
        get("/cached/upgrade", "Connection: Upgrade", "Upgrade: websocket");
        assertEquals(2, calls("/upgrade"));
    }

    @Test
    void theStoreIsBoundedByItsCapacity() {
        InMemoryResponseCacheStore store = new InMemoryResponseCacheStore(1000);
        for (int i = 0; i < 10; i++) {
            store.put("k" + i, new ResponseCacheStore.CachedResponse(200, List.of(), new byte[300], 0, 1000, 0, List.of()));
        }
        assertTrue(store.size() <= 1000, String.valueOf(store.size()));
        assertEquals(2, store.count());
        assertNull(store.get("k0").toCompletableFuture().join());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String scripted;
        final InMemoryResponseCacheStore store = new InMemoryResponseCacheStore(1_000_000);

        Routes(@Value("${test.scripted}") String scripted) {
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("cached").path("/cached/**").filters(GatewayFilters.stripPrefix(1),
                new LocalResponseCache(store, Duration.ZERO, 1000, CLOCK::get)).uri(scripted);
            gateway.route("cached-real-clock").path("/cached-real-clock/**").filters(GatewayFilters.stripPrefix(1),
                GatewayFilters.localResponseCache(Duration.ZERO, 100_000)).uri(scripted);
        }
    }
}
