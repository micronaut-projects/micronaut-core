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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.server.ServerResponseAttributes;
import io.micronaut.web.router.builder.RouteFilterSpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The response cache of a gateway route, see
 * {@link GatewayFilters#localResponseCache(ResponseCacheStore, Duration, long)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class LocalResponseCache implements GatewayFilter {
    private static final Logger LOG = LoggerFactory.getLogger(LocalResponseCache.class);
    private static final String KEY = "micronaut.gateway.cache.key";
    private static final String NO_STORE = "micronaut.gateway.cache.no-store";
    private static final String HIT = "micronaut.gateway.cache.hit";
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final ResponseCacheStore store;
    private final Duration timeToLive;
    private final long maxEntryBytes;
    private final LongSupplier clock;
    /**
     * The names of the request headers the responses of a URI vary by, as last stored.
     */
    private final Map<String, List<String>> varyByBase = new ConcurrentHashMap<>();

    LocalResponseCache(ResponseCacheStore store, Duration timeToLive, long maxEntryBytes, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive");
        if (timeToLive.isNegative()) {
            throw new IllegalArgumentException("The time to live of a response cache is negative: " + timeToLive);
        }
        if (maxEntryBytes < 0) {
            throw new IllegalArgumentException("The maximum size of a cached response is negative: " + maxEntryBytes);
        }
        this.maxEntryBytes = maxEntryBytes;
        this.clock = clock;
    }

    @Override
    public void configure(RouteFilterSpec<?> route) {
        route.beforeReplacingAsync(this::lookup);
        route.after(this::store);
    }

    private CompletionStage<? extends @Nullable HttpResponse<?>> lookup(HttpRequest<?> request) {
        String method = request.getMethodName();
        String base = base(request);
        if (UNSAFE.contains(method)) {
            // a change of the resource: its responses are stale
            return store.invalidate(base).handle((ignored, failure) -> null);
        }
        if (!method.equals("GET") && !method.equals("HEAD")
            || request.getHeaders().contains(HttpHeaders.UPGRADE) || request.getHeaders().contains(HttpHeaders.RANGE)) {
            return CompletableFuture.completedFuture(null);
        }
        Directives directives = Directives.of(request.getHeaders().getAll(HttpHeaders.CACHE_CONTROL));
        if (directives.has("no-store")) {
            request.setAttribute(NO_STORE, Boolean.TRUE);
            return CompletableFuture.completedFuture(null);
        }
        String key = key(base, request, varyByBase.getOrDefault(base, List.of()));
        if (method.equals("GET")) {
            request.setAttribute(KEY, key);
        }
        boolean revalidate = directives.has("no-cache") || "0".equals(directives.value("max-age"))
            || request.getHeaders().getAll("Pragma").stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).contains("no-cache"));
        if (revalidate) {
            return CompletableFuture.completedFuture(null);
        }
        long maxAge = seconds(directives.value("max-age"));
        CompletionStage<ResponseCacheStore.@Nullable CachedResponse> found;
        try {
            found = store.get(key);
        } catch (RuntimeException e) {
            found = CompletableFuture.failedFuture(e);
        }
        return found.handle((cached, failure) -> {
            if (failure != null) {
                LOG.debug("The response cache failed, the request goes to the upstream", failure);
                return null;
            }
            if (cached == null) {
                return null;
            }
            long now = clock.getAsLong();
            long ageMillis = now - cached.storedAtMillis();
            if (ageMillis >= cached.freshMillis()) {
                return null;
            }
            long age = cached.initialAge() + ageMillis / 1000;
            if (maxAge >= 0 && age > maxAge) {
                return null;
            }
            return answer(request, cached, age);
        });
    }

    private HttpResponse<?> answer(HttpRequest<?> request, ResponseCacheStore.CachedResponse cached, long age) {
        request.setAttribute(HIT, Boolean.TRUE);
        String etag = null;
        String lastModified = null;
        for (Map.Entry<String, String> header : cached.headers()) {
            if (header.getKey().equalsIgnoreCase(HttpHeaders.ETAG)) {
                etag = header.getValue();
            } else if (header.getKey().equalsIgnoreCase(HttpHeaders.LAST_MODIFIED)) {
                lastModified = header.getValue();
            }
        }
        if (notModified(request, etag, lastModified)) {
            MutableHttpResponse<?> response = HttpResponse.status(HttpStatus.NOT_MODIFIED);
            for (Map.Entry<String, String> header : cached.headers()) {
                String name = header.getKey().toLowerCase(Locale.ROOT);
                if (name.equals("etag") || name.equals("cache-control") || name.equals("expires") || name.equals("vary")
                    || name.equals("last-modified") || name.equals("date")) {
                    response.getHeaders().add(header.getKey(), header.getValue());
                }
            }
            response.getHeaders().set(HttpHeaders.AGE, String.valueOf(age));
            return response;
        }
        ByteBodyFactory factory = request instanceof ServerHttpRequest<?> server
            ? server.byteBodyFactory() : ByteBodyFactory.createDefault(io.micronaut.core.io.buffer.ByteArrayBufferFactory.INSTANCE);
        MutableByteBodyHttpResponse<?> response = MutableByteBodyHttpResponse.of(HttpResponse.status(HttpStatus.valueOf(cached.status())), factory.adapt(cached.body()));
        for (Map.Entry<String, String> header : cached.headers()) {
            if (!header.getKey().equalsIgnoreCase(HttpHeaders.AGE)) {
                response.getHeaders().add(header.getKey(), header.getValue());
            }
        }
        response.getHeaders().set(HttpHeaders.AGE, String.valueOf(age));
        // the bytes as the upstream sent them, e.g. compressed
        response.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, true);
        return response;
    }

    private static boolean notModified(HttpRequest<?> request, @Nullable String etag, @Nullable String lastModified) {
        List<String> ifNoneMatch = request.getHeaders().getAll(HttpHeaders.IF_NONE_MATCH);
        if (!ifNoneMatch.isEmpty()) {
            if (etag == null) {
                return false;
            }
            String weak = etag.startsWith("W/") ? etag.substring(2) : etag;
            for (String value : ifNoneMatch) {
                for (String tag : value.split(",")) {
                    String t = tag.strip();
                    if (t.equals("*") || (t.startsWith("W/") ? t.substring(2) : t).equals(weak)) {
                        return true;
                    }
                }
            }
            return false;
        }
        String since = request.getHeaders().get(HttpHeaders.IF_MODIFIED_SINCE);
        if (since == null || lastModified == null) {
            return false;
        }
        try {
            return !ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME)
                .isAfter(ZonedDateTime.parse(since, DateTimeFormatter.RFC_1123_DATE_TIME));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void store(HttpRequest<?> request, MutableHttpResponse<?> response) {
        String key = request.getAttribute(KEY, String.class).orElse(null);
        if (key == null || request.getAttribute(HIT).isPresent() || request.getAttribute(NO_STORE).isPresent()
            || request.getAttribute(GatewayAttributes.FAILURE).isPresent()) {
            return;
        }
        String base = base(request);
        Directives directives = Directives.of(response.getHeaders().getAll(HttpHeaders.CACHE_CONTROL));
        if (response.code() != 200 || directives.has("no-store") || directives.has("private") || directives.has("no-cache")
            || response.getHeaders().contains(HttpHeaders.SET_COOKIE)
            || request.getHeaders().contains(HttpHeaders.AUTHORIZATION) && !directives.has("public") && directives.value("s-maxage") == null) {
            // not cacheable: an older response of the URI must not be served instead
            store.invalidate(base);
            return;
        }
        List<String> vary = new ArrayList<>();
        for (String value : response.getHeaders().getAll(HttpHeaders.VARY)) {
            for (String name : value.split(",")) {
                String n = name.strip().toLowerCase(Locale.ROOT);
                if (n.equals("*")) {
                    store.invalidate(base);
                    return;
                }
                if (!n.isEmpty()) {
                    vary.add(n);
                }
            }
        }
        long fresh = freshMillis(directives, response);
        if (fresh <= 0 || !(response instanceof MutableByteBodyHttpResponse<?> mutable)) {
            return;
        }
        long length = response.getHeaders().contentLength().orElse(-1);
        if (length > maxEntryBytes) {
            return;
        }
        varyByBase.put(base, List.copyOf(vary));
        String storeKey = key(base, request, vary);
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        response.getHeaders().forEach((name, values) -> values.forEach(v -> headers.add(Map.entry(name, v))));
        long initialAge = seconds(response.getHeaders().get(HttpHeaders.AGE));
        long storedAt = clock.getAsLong();
        int status = response.code();
        // a copy of the bytes, read at the pace of the client or faster: the client is never slowed down
        CloseableByteBody copy = mutable.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
        ByteBodyFactory factory = request instanceof ServerHttpRequest<?> server
            ? server.byteBodyFactory() : ByteBodyFactory.createDefault(io.micronaut.core.io.buffer.ByteArrayBufferFactory.INSTANCE);
        factory.limit(copy, maxEntryBytes).buffer().whenComplete((bytes, failure) -> {
            if (failure != null) {
                // incomplete, failed or too large: not cached
                return;
            }
            byte[] body;
            try (bytes) {
                body = bytes.toByteArray();
            }
            store.put(storeKey, new ResponseCacheStore.CachedResponse(status, headers, body, storedAt, fresh, Math.max(0, initialAge), vary));
        });
    }

    private long freshMillis(Directives directives, MutableHttpResponse<?> response) {
        long sMaxAge = seconds(directives.value("s-maxage"));
        if (sMaxAge >= 0) {
            return sMaxAge * 1000;
        }
        long maxAge = seconds(directives.value("max-age"));
        if (maxAge >= 0) {
            return maxAge * 1000;
        }
        String expires = response.getHeaders().get(HttpHeaders.EXPIRES);
        if (expires != null) {
            try {
                return ZonedDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - clock.getAsLong();
            } catch (DateTimeParseException e) {
                // an invalid Expires is in the past
                return 0;
            }
        }
        return timeToLive.toMillis();
    }

    private static String base(HttpRequest<?> request) {
        String query = request.getUri().getRawQuery();
        return GatewayAttributes.routeId(request) + " " + request.getPath() + (query == null ? "" : "?" + query);
    }

    /**
     * The key of a response: the URI, the credentials, so that users do not share responses,
     * and the values of the headers the response varies by.
     */
    private static String key(String base, HttpRequest<?> request, List<String> vary) {
        StringBuilder key = new StringBuilder(base).append('\n');
        key.append(Objects.hashCode(request.getHeaders().getAll(HttpHeaders.AUTHORIZATION))).append(' ');
        key.append(Objects.hashCode(request.getHeaders().getAll(HttpHeaders.COOKIE)));
        for (String name : vary) {
            key.append('\n').append(name).append('=').append(String.join(",", request.getHeaders().getAll(name)));
        }
        return key.toString();
    }

    private static long seconds(@Nullable String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value.strip().replace("\"", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "LocalResponseCache=" + timeToLive + ", " + maxEntryBytes;
    }

    /**
     * The directives of {@code Cache-Control}.
     *
     * @param values The directives, with their values or {@code null}
     */
    private record Directives(Map<String, @Nullable String> values) {
        static Directives of(List<String> headers) {
            Map<String, @Nullable String> values = new java.util.HashMap<>();
            for (String header : headers) {
                for (String directive : header.split(",")) {
                    String d = directive.strip();
                    if (d.isEmpty()) {
                        continue;
                    }
                    int eq = d.indexOf('=');
                    if (eq < 0) {
                        values.put(d.toLowerCase(Locale.ROOT), null);
                    } else {
                        values.put(d.substring(0, eq).strip().toLowerCase(Locale.ROOT), d.substring(eq + 1).strip());
                    }
                }
            }
            return new Directives(values);
        }

        boolean has(String name) {
            return values.containsKey(name);
        }

        @Nullable String value(String name) {
            return values.get(name);
        }
    }
}
