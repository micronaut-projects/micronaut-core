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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    private static final String NO_STORE = "no-store";
    private static final String PRIVATE = "private";
    private static final String NO_CACHE = "no-cache";
    private static final String MAX_AGE = "max-age";
    /**
     * The number of URIs whose Vary names and invalidations are remembered.
     */
    private static final int MAX_TRACKED_URIS = 10_000;
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final ResponseCacheStore store;
    private final Duration timeToLive;
    private final long maxEntryBytes;
    private final LongSupplier clock;
    /**
     * The names of the request headers the responses of a URI vary by, as last stored, for the
     * most recently used URIs only: a URI that is left out is looked up without them, a miss.
     */
    private final Map<String, List<String>> varyByBase = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > MAX_TRACKED_URIS;
        }
    };
    private final Invalidations invalidations = new Invalidations();

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
        if (DefaultProxyHandler.hostRejection(request) != null) {
            // the handler answers it: a hit would skip its validation
            return CompletableFuture.completedFuture(null);
        }
        String base = base(request);
        if (UNSAFE.contains(method)) {
            // a change of the resource: its responses are stale once the change succeeded, see
            // store. A change that the upstream refuses does not purge them
            state(request).unsafeBase = base;
            return CompletableFuture.completedFuture(null);
        }
        if (!method.equals("GET") && !method.equals("HEAD")
            || request.getHeaders().contains(HttpHeaders.UPGRADE) || request.getHeaders().contains(HttpHeaders.RANGE)
            // preconditions only the upstream can evaluate
            || request.getHeaders().contains(HttpHeaders.IF_MATCH) || request.getHeaders().contains(HttpHeaders.IF_UNMODIFIED_SINCE)) {
            return CompletableFuture.completedFuture(null);
        }
        Directives directives = Directives.of(request.getHeaders().getAll(HttpHeaders.CACHE_CONTROL));
        if (directives.has(NO_STORE)) {
            state(request).noStore = true;
            return CompletableFuture.completedFuture(null);
        }
        String variant = variant(request, varyOf(base));
        if (method.equals("GET")) {
            State state = state(request);
            state.base = base;
            state.epoch = invalidations.epoch();
        }
        boolean revalidate = directives.has(NO_CACHE) || "0".equals(directives.value(MAX_AGE))
            || request.getHeaders().getAll("Pragma").stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).contains(NO_CACHE));
        if (revalidate) {
            return CompletableFuture.completedFuture(null);
        }
        long maxAge = seconds(directives.value(MAX_AGE));
        CompletionStage<ResponseCacheStore.@Nullable CachedResponse> found;
        try {
            found = store.get(base, variant);
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
            if (invalidations.invalidatedAfter(base, cached.storedAtMillis())) {
                // stored before a change of the resource, e.g. while the removal from an
                // asynchronous store is in progress, or by a late store
                return null;
            }
            long now = clock.getAsLong();
            long ageMillis = now - cached.storedAtMillis();
            // the age includes the Age of the response when it was stored
            if (ageMillis + cached.initialAge() * 1000 >= cached.freshMillis()) {
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
        state(request).hit = true;
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
            String weak = etag == null ? null : etag.startsWith("W/") ? etag.substring(2) : etag;
            for (String value : ifNoneMatch) {
                for (String tag : value.split(",")) {
                    String t = tag.strip();
                    // * matches any stored response, whether it has an ETag or not
                    if (t.equals("*") || weak != null && (t.startsWith("W/") ? t.substring(2) : t).equals(weak)) {
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
        State state = (State) GatewayRequestContext.forFilters(request).removeState(this);
        if (state == null) {
            return;
        }
        String unsafeBase = state.unsafeBase;
        if (unsafeBase != null) {
            if (response.code() < 400) {
                // the change is done: the stored responses, and a response that a GET read
                // before it, are stale
                invalidate(unsafeBase);
            }
            return;
        }
        String base = state.base;
        if (base == null || state.hit || state.noStore
            || GatewayRequestContext.find(request).flatMap(GatewayRequestContext::failure).isPresent()) {
            return;
        }
        Directives directives = Directives.of(response.getHeaders().getAll(HttpHeaders.CACHE_CONTROL));
        if (response.code() == 200 && (directives.has(NO_STORE) || directives.has(PRIVATE))) {
            // the resource is no longer cacheable: an older response must not be served instead
            invalidate(base);
            return;
        }
        if (response.code() != 200 || directives.has(NO_STORE) || directives.has(PRIVATE) || directives.has(NO_CACHE)
            || response.getHeaders().contains(HttpHeaders.SET_COOKIE)
            || request.getHeaders().contains(HttpHeaders.AUTHORIZATION) && !directives.has("public") && directives.value("s-maxage") == null) {
            // not stored, and the stored response stays: a 304, a failure of the upstream or a
            // response of one user says nothing about the stored one
            return;
        }
        List<String> vary = new ArrayList<>();
        for (String value : response.getHeaders().getAll(HttpHeaders.VARY)) {
            for (String name : value.split(",")) {
                String n = name.strip().toLowerCase(Locale.ROOT);
                if (n.equals("*")) {
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
        long epoch = state.epoch;
        if (invalidations.invalidatedSince(base, epoch)) {
            // the resource changed while this response was on its way
            return;
        }
        rememberVary(base, vary);
        String storeVariant = variant(request, vary);
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        response.getHeaders().forEach((name, values) -> {
            // the headers of one request, e.g. of a rate limit, are not those of a hit
            if (!name.regionMatches(true, 0, "X-RateLimit-", 0, "X-RateLimit-".length())) {
                values.forEach(v -> headers.add(Map.entry(name, v)));
            }
        });
        long initialAge = seconds(response.getHeaders().get(HttpHeaders.AGE));
        long storedAt = clock.getAsLong();
        int status = response.code();
        // a copy of the bytes at the pace of the relay to the client, which drives the demand: the
        // copy never runs ahead of the client beyond the buffer of the relay
        CloseableByteBody copy = mutable.byteBody().split(ByteBody.SplitBackpressureMode.ORIGINAL);
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
            if (invalidations.invalidatedSince(base, epoch)) {
                return;
            }
            try {
                store.put(base, storeVariant, new ResponseCacheStore.CachedResponse(status, headers, body, storedAt, fresh, Math.max(0, initialAge), vary))
                    .whenComplete((ignored, e) -> {
                        if (e != null) {
                            LOG.debug("The response cache could not store a response", e);
                        } else if (invalidations.invalidatedSince(base, epoch)) {
                            // a change of the resource completed while the store was in progress: the
                            // stored response is older than it
                            invalidate(base);
                        }
                    });
            } catch (RuntimeException e) {
                LOG.debug("The response cache could not store a response", e);
            }
        });
    }

    private long freshMillis(Directives directives, MutableHttpResponse<?> response) {
        long sMaxAge = seconds(directives.value("s-maxage"));
        if (sMaxAge >= 0) {
            return sMaxAge * 1000;
        }
        long maxAge = seconds(directives.value(MAX_AGE));
        if (maxAge >= 0) {
            return maxAge * 1000;
        }
        String expires = response.getHeaders().get(HttpHeaders.EXPIRES);
        if (expires != null) {
            try {
                // the freshness left now, plus the age it already has, which the lookup counts
                long initialAge = Math.max(0, seconds(response.getHeaders().get(HttpHeaders.AGE)));
                return ZonedDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - clock.getAsLong()
                    + initialAge * 1000;
            } catch (DateTimeParseException e) {
                // an invalid Expires is in the past
                return 0;
            }
        }
        return timeToLive.toMillis();
    }

    /**
     * The resource of a request: the route, the host, so that the tenants of a route on several
     * hosts do not share responses, and the path and the query. Each part but the last starts
     * with its length, so that no two resources have the same base.
     */
    private static String base(HttpRequest<?> request) {
        String routeId = GatewayRequestContext.routeId(request);
        // the revision of the route: a route that a refresh points to another upstream does not
        // serve the responses of the previous one. A digest, since a URI may hold credentials
        String revision = GatewayRequestContext.find(request)
            .map(context -> digest(List.of(context.route().getUri().toString())))
            .orElse("-");
        String host = request.getHeaders().get(HttpHeaders.HOST);
        if (host == null) {
            host = Objects.requireNonNullElse(request.getUri().getRawAuthority(), "");
        }
        // the host and the port, e.g. of two tenants on two ports of a PreserveHostHeader
        // upstream, and the scheme
        host = host.toLowerCase(Locale.ROOT);
        String scheme = request.isSecure() ? "https" : "http";
        String query = request.getUri().getRawQuery();
        return routeId.length() + ":" + routeId + " " + revision + " " + scheme + " " + host.length() + ":" + host + " "
            + request.getPath() + (query == null ? "" : "?" + query);
    }

    private static String digest(List<String> values) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        update(digest, values);
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * The key of a response: the resource, the credentials, so that users do not share
     * responses, and the values of the headers the response varies by. The credentials are a
     * SHA-256 digest of all their values, never a hash code that two users could share.
     */
    private static String variant(HttpRequest<?> request, List<String> vary) {
        if (vary.isEmpty()) {
            return credentialsDigest(request);
        }
        // the values of the headers a response varies by, e.g. Cookie or Authorization, are
        // credentials too: the store only sees a digest of them, each value with its length
        List<String> parts = new ArrayList<>(vary.size() * 2 + 1);
        parts.add(credentialsDigest(request));
        for (String name : vary) {
            parts.add(name);
            parts.add(String.join(",", request.getHeaders().getAll(name)));
        }
        return digest(parts);
    }

    private static String credentialsDigest(HttpRequest<?> request) {
        List<String> authorization = request.getHeaders().getAll(HttpHeaders.AUTHORIZATION);
        List<String> cookies = request.getHeaders().getAll(HttpHeaders.COOKIE);
        if (authorization.isEmpty() && cookies.isEmpty()) {
            return "-";
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // every Java platform has SHA-256
            throw new IllegalStateException(e);
        }
        update(digest, authorization);
        digest.update((byte) 0);
        update(digest, cookies);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, List<String> values) {
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            // the length first: the values cannot run into each other
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }

    /**
     * Remove the responses of a resource. A store that fails, also synchronously, is logged:
     * the request goes on.
     *
     * @param base The resource
     * @return The stage of the removal, never failed
     */
    private CompletionStage<?> invalidate(String base) {
        invalidations.invalidate(base, clock.getAsLong());
        CompletionStage<?> removal;
        try {
            removal = store.invalidate(base);
        } catch (RuntimeException e) {
            removal = CompletableFuture.failedFuture(e);
        }
        return removal.handle((ignored, failure) -> {
            if (failure != null) {
                LOG.debug("The response cache could not remove the responses of {}", base, failure);
            }
            return null;
        });
    }

    private List<String> varyOf(String base) {
        synchronized (varyByBase) {
            return varyByBase.getOrDefault(base, List.of());
        }
    }

    private void rememberVary(String base, List<String> vary) {
        synchronized (varyByBase) {
            if (vary.isEmpty()) {
                // the default: nothing to keep
                varyByBase.remove(base);
            } else {
                varyByBase.put(base, List.copyOf(vary));
            }
        }
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

    private State state(HttpRequest<?> request) {
        GatewayRequestContext context = GatewayRequestContext.forFilters(request);
        State state = context.state(this, State.class);
        if (state == null) {
            state = new State();
            context.state(this, state);
        }
        return state;
    }

    @Override
    public String toString() {
        return "LocalResponseCache=" + timeToLive + ", " + maxEntryBytes;
    }

    /**
     * When the resources were last invalidated, so that a response that a request read before
     * the invalidation of its resource is not stored after it. The most recently invalidated
     * resources are remembered; for a resource that is no longer remembered, a response read
     * before the oldest forgotten invalidation is not stored.
     */
    private static final class Invalidations {
        private final LinkedHashMap<String, Long> last = new LinkedHashMap<>(16, 0.75f, true);
        private final java.util.HashMap<String, Long> lastAt = new java.util.HashMap<>();
        private long epoch;
        private long forgotten = -1;
        private long forgottenAt = Long.MIN_VALUE;

        synchronized long epoch() {
            return epoch;
        }

        synchronized void invalidate(String base, long nowMillis) {
            epoch++;
            last.put(base, epoch);
            lastAt.put(base, nowMillis);
            if (last.size() > MAX_TRACKED_URIS) {
                Map.Entry<String, Long> eldest = last.entrySet().iterator().next();
                forgotten = Math.max(forgotten, eldest.getValue());
                Long at = lastAt.remove(eldest.getKey());
                if (at != null) {
                    forgottenAt = Math.max(forgottenAt, at);
                }
                last.remove(eldest.getKey());
            }
        }

        /**
         * @param base           A resource
         * @param storedAtMillis When a response of it was stored
         * @return Whether the resource was invalidated at or after that time
         */
        synchronized boolean invalidatedAfter(String base, long storedAtMillis) {
            Long at = lastAt.get(base);
            return at != null ? at >= storedAtMillis : forgottenAt >= storedAtMillis;
        }

        /**
         * @param base  A resource
         * @param epoch The epoch a request read, {@code -1} if unknown
         * @return Whether the resource was invalidated after the epoch, or may have been
         */
        synchronized boolean invalidatedSince(String base, long epoch) {
            if (epoch < 0) {
                return true;
            }
            Long at = last.get(base);
            if (at != null) {
                return at > epoch;
            }
            return forgotten > epoch;
        }
    }

    /**
     * What the lookup of a request leaves for the store of its response.
     */
    private static final class State {
        @Nullable
        private String unsafeBase;
        @Nullable
        private String base;
        private long epoch = -1;
        private boolean noStore;
        private boolean hit;
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
