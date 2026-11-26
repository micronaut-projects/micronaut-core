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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The HTTP/3 alternatives that origins advertised with {@code Alt-Svc} (RFC 7838), for the
 * client to send their later requests over QUIC. Only the most preferred {@code h3} alternative
 * of an origin is kept, until its max age passes or the origin advertises something else. An
 * alternative that failed is not offered again for {@link #brokenDuration}.
 *
 * @since 5.3.0
 */
@Internal
final class AltSvcCache {
    /**
     * The name of the {@code Alt-Svc} header.
     */
    static final String HEADER_NAME = "alt-svc";
    /**
     * The ALPN protocol id of HTTP/3.
     */
    static final String PROTOCOL_HTTP_3 = "h3";
    /**
     * The max age of an alternative without {@code ma} parameter, RFC 7838 section 3.1.
     */
    static final long DEFAULT_MAX_AGE_SECONDS = TimeUnit.DAYS.toSeconds(1);
    /**
     * How long a failed alternative is not used, by default.
     */
    static final Duration DEFAULT_BROKEN_DURATION = Duration.ofMinutes(5);
    /**
     * The number of origins whose alternatives are kept at most, so that a client that talks to
     * many origins does not keep an unbounded number of entries.
     */
    static final int MAX_ORIGINS = 1024;

    private final Map<NettyHttpClient.RequestKey, Alternative> alternatives = new ConcurrentHashMap<>();
    private final Map<Route, Long> brokenUntil = new ConcurrentHashMap<>();
    private final Duration brokenDuration;
    private final LongSupplier nanoTime;

    AltSvcCache() {
        this(DEFAULT_BROKEN_DURATION, System::nanoTime);
    }

    /**
     * @param brokenDuration How long a failed alternative is not used
     * @param nanoTime       The clock, in nanoseconds
     */
    AltSvcCache(Duration brokenDuration, LongSupplier nanoTime) {
        this.brokenDuration = brokenDuration;
        this.nanoTime = nanoTime;
    }

    /**
     * The HTTP/3 alternative to send a request to the given origin to, if one is known, has not
     * expired and has not failed recently.
     *
     * @param origin The origin
     * @return The route to the alternative, or {@code null} to use the origin itself
     */
    @Nullable
    Route lookup(NettyHttpClient.RequestKey origin) {
        Alternative alternative = alternatives.get(origin);
        if (alternative == null) {
            return null;
        }
        long now = nanoTime.getAsLong();
        if (now - alternative.expiresAt >= 0) {
            alternatives.remove(origin, alternative);
            return null;
        }
        Route route = new Route(origin, alternative.host == null ? origin.getHost() : alternative.host, alternative.port);
        Long until = brokenUntil.get(route);
        if (until != null) {
            if (now - until < 0) {
                return null;
            }
            brokenUntil.remove(route, until);
        }
        return route;
    }

    /**
     * Applies the {@code Alt-Svc} header fields of a response of the given origin: they replace
     * all alternatives known for it (RFC 7838 section 3.1), and {@code clear} removes them.
     *
     * @param origin The origin that sent the response
     * @param values The values of the {@code Alt-Svc} header fields, not empty
     */
    void update(NettyHttpClient.RequestKey origin, List<String> values) {
        Alternative alternative = null;
        for (String value : values) {
            if (value.trim().equalsIgnoreCase("clear")) {
                alternatives.remove(origin);
                return;
            }
            if (alternative == null) {
                alternative = firstHttp3(value);
            }
        }
        if (alternative == null) {
            alternatives.remove(origin);
            return;
        }
        if (alternatives.size() >= MAX_ORIGINS && !alternatives.containsKey(origin)) {
            removeExpired();
            if (alternatives.size() >= MAX_ORIGINS) {
                return;
            }
        }
        alternatives.put(origin, alternative);
    }

    /**
     * Marks the alternative of a route as failed, so that the origin is used directly for
     * {@link #brokenDuration}.
     *
     * @param route The route that failed
     */
    void markBroken(Route route) {
        if (brokenUntil.size() >= MAX_ORIGINS) {
            long now = nanoTime.getAsLong();
            brokenUntil.values().removeIf(until -> now - until >= 0);
        }
        brokenUntil.put(route, nanoTime.getAsLong() + brokenDuration.toNanos());
    }

    private void removeExpired() {
        long now = nanoTime.getAsLong();
        alternatives.values().removeIf(alternative -> now - alternative.expiresAt >= 0);
    }

    @Nullable
    private Alternative firstHttp3(String value) {
        for (AltValue altValue : parse(value)) {
            if (altValue.protocolId.equals(PROTOCOL_HTTP_3) && altValue.maxAgeSeconds > 0) {
                long maxAgeNanos = TimeUnit.SECONDS.toNanos(Math.min(altValue.maxAgeSeconds, TimeUnit.DAYS.toSeconds(365 * 100L)));
                return new Alternative(altValue.host, altValue.port, nanoTime.getAsLong() + maxAgeNanos);
            }
        }
        return null;
    }

    /**
     * Parses the value of one {@code Alt-Svc} header field (RFC 7838 section 3). Alternatives
     * that are malformed are left out, and so is a {@code clear} value.
     *
     * @param value The header field value
     * @return The alternatives, in the order of preference of the origin
     */
    static List<AltValue> parse(String value) {
        List<AltValue> result = new ArrayList<>();
        for (String altValue : split(value, ',')) {
            AltValue parsed = parseAltValue(altValue);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    @Nullable
    private static AltValue parseAltValue(String altValue) {
        List<String> parts = split(altValue, ';');
        if (parts.isEmpty()) {
            return null;
        }
        String alternative = parts.get(0);
        int eq = alternative.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String protocolId = percentDecode(alternative.substring(0, eq).trim());
        String quotedAuthority = alternative.substring(eq + 1).trim();
        // alt-authority is always a quoted-string
        String authority = quotedAuthority.startsWith("\"") ? unquote(quotedAuthority) : null;
        if (protocolId == null || authority == null) {
            return null;
        }
        int colon = authority.lastIndexOf(':');
        if (colon < 0 || authority.indexOf(']', colon) >= 0) {
            return null;
        }
        int port = parseNonNegative(authority.substring(colon + 1));
        if (port <= 0 || port > 0xFFFF) {
            return null;
        }
        String host = authority.substring(0, colon);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        } else if (host.indexOf(':') >= 0 || host.indexOf('[') >= 0 || host.indexOf(']') >= 0) {
            return null;
        }
        long maxAge = DEFAULT_MAX_AGE_SECONDS;
        for (int i = 1; i < parts.size(); i++) {
            String parameter = parts.get(i);
            int peq = parameter.indexOf('=');
            if (peq <= 0) {
                continue;
            }
            String name = parameter.substring(0, peq).trim().toLowerCase(Locale.ROOT);
            if (name.equals("ma")) {
                String v = unquote(parameter.substring(peq + 1).trim());
                long parsed = v == null ? -1 : parseNonNegativeLong(v);
                if (parsed < 0) {
                    return null;
                }
                maxAge = parsed;
            }
        }
        return new AltValue(protocolId, host.isEmpty() ? null : host, port, maxAge);
    }

    /**
     * Splits on a delimiter outside of quoted strings, and leaves out empty elements.
     */
    private static List<String> split(String value, char delimiter) {
        List<String> result = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quoted) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                addIfNotBlank(result, value.substring(start, i));
                start = i + 1;
            }
        }
        addIfNotBlank(result, value.substring(start));
        return result;
    }

    private static void addIfNotBlank(List<String> result, String element) {
        String trimmed = element.trim();
        if (!trimmed.isEmpty()) {
            result.add(trimmed);
        }
    }

    /**
     * @return The content of a quoted string, or a token as it is
     */
    @Nullable
    private static String unquote(String value) {
        if (!value.startsWith("\"")) {
            return value.isEmpty() || value.indexOf('"') >= 0 ? null : value;
        }
        if (value.length() < 2 || !value.endsWith("\"")) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                i++;
                if (i >= value.length() - 1) {
                    return null;
                }
                c = value.charAt(i);
            } else if (c == '"') {
                return null;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * Decodes the percent-encoding of a protocol id, RFC 7838 section 3.
     */
    @Nullable
    private static String percentDecode(String token) {
        if (token.isEmpty()) {
            return null;
        }
        if (token.indexOf('%') < 0) {
            return token;
        }
        byte[] bytes = new byte[token.length()];
        int n = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '%') {
                if (i + 2 >= token.length()) {
                    return null;
                }
                int hi = Character.digit(token.charAt(i + 1), 16);
                int lo = Character.digit(token.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                bytes[n++] = (byte) (hi << 4 | lo);
                i += 2;
            } else {
                bytes[n++] = (byte) c;
            }
        }
        return new String(bytes, 0, n, StandardCharsets.UTF_8);
    }

    private static int parseNonNegative(String digits) {
        long value = parseNonNegativeLong(digits);
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    /**
     * @return The value of a string of digits, {@link Long#MAX_VALUE} if it is larger, or
     * {@code -1} if it is not a string of digits
     */
    private static long parseNonNegativeLong(String digits) {
        if (digits.isEmpty()) {
            return -1;
        }
        long value = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            if (value > (Long.MAX_VALUE - 9) / 10) {
                value = Long.MAX_VALUE;
            } else if (value != Long.MAX_VALUE) {
                value = value * 10 + (c - '0');
            }
        }
        return value;
    }

    /**
     * One alternative of an {@code Alt-Svc} header field.
     *
     * @param protocolId    The ALPN protocol id
     * @param host          The host of the alternative, or {@code null} for the host of the origin
     * @param port          The port of the alternative
     * @param maxAgeSeconds The max age of the alternative, in seconds
     */
    record AltValue(String protocolId, @Nullable String host, int port, long maxAgeSeconds) {
    }

    /**
     * A known HTTP/3 alternative of an origin.
     *
     * @param host      The host, or {@code null} for the host of the origin
     * @param port      The UDP port
     * @param expiresAt When the alternative expires, in {@link #nanoTime} time
     */
    private record Alternative(@Nullable String host, int port, long expiresAt) {
    }

    /**
     * Where to send the requests to an origin over HTTP/3. The TLS handshake still authenticates
     * the origin.
     *
     * @param origin The origin
     * @param host   The host to connect to
     * @param port   The UDP port to connect to
     */
    record Route(NettyHttpClient.RequestKey origin, String host, int port) {
    }
}
