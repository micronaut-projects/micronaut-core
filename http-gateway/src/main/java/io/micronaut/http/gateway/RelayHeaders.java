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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The header fields a relay does not pass on as they are: the trailers that would be read as
 * header fields of the request or the response, the transport metadata of the Netty HTTP/2
 * codec, and the {@code Host} that a relay sends.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RelayHeaders {

    /**
     * The fields a relay drops from the trailers in both directions: a recipient that merges the
     * trailers into the header section, as RFC 9110 lets it do for some fields, would otherwise
     * read a forwarded address, credentials, the framing or the routing of the message from the
     * peer.
     */
    private static final Set<String> TRAILERS_NOT_RELAYED = Set.of(
        "forwarded", "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-forwarded-port",
        "x-forwarded-prefix", "x-real-ip",
        "authorization", "proxy-authorization", "proxy-authenticate", "www-authenticate", "proxy-connection",
        "cookie", "set-cookie",
        "host", "connection", "keep-alive", "te", "trailer", "transfer-encoding", "upgrade",
        "content-length", "content-encoding", "content-type", "content-range", "location"
    );

    /**
     * The prefix of the header fields that the Netty HTTP/2 codec adds for the stream of a
     * message, e.g. {@code x-http2-stream-id}: metadata of one connection, not of the message.
     */
    private static final String HTTP2_EXTENSION_PREFIX = "x-http2-";

    private RelayHeaders() {
    }

    /**
     * Remove the transport metadata of the inbound or the upstream connection.
     *
     * @param headers The headers of the relayed message
     */
    static void removeTransportHeaders(MutableHttpHeaders headers) {
        List<String> names = null;
        for (String name : headers.names()) {
            if (name.regionMatches(true, 0, HTTP2_EXTENSION_PREFIX, 0, HTTP2_EXTENSION_PREFIX.length())) {
                if (names == null) {
                    names = new ArrayList<>(2);
                }
                names.add(name);
            }
        }
        if (names != null) {
            for (String name : names) {
                headers.remove(name);
            }
        }
    }

    /**
     * @param name The name of a trailer field
     * @return Whether a relay passes it on
     */
    static boolean isRelayedTrailer(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !TRAILERS_NOT_RELAYED.contains(lower) && !lower.startsWith("proxy-")
            && !lower.startsWith(HTTP2_EXTENSION_PREFIX) && !isForwardedAlias(lower);
    }

    private static boolean isForwardedAlias(String lower) {
        return lower.indexOf('_') >= 0 && TRAILERS_NOT_RELAYED.contains(lower.replace('_', '-'));
    }

    /**
     * @param trailers The trailers of a relayed body
     * @return The trailers a relay passes on
     */
    static HttpHeaders sanitizeTrailers(HttpHeaders trailers) {
        boolean clean = true;
        for (String name : trailers.names()) {
            if (!isRelayedTrailer(name)) {
                clean = false;
                break;
            }
        }
        if (clean) {
            return trailers;
        }
        SimpleHttpHeaders sanitized = new SimpleHttpHeaders(ConversionService.SHARED);
        trailers.forEach((name, values) -> {
            if (isRelayedTrailer(name)) {
                for (String value : values) {
                    sanitized.add(name, value);
                }
            }
        });
        return sanitized;
    }

    /**
     * A relayed body whose trailers are sanitized, see {@link #sanitizeTrailers(HttpHeaders)}.
     * Only a streamed body of unknown length can carry trailers on HTTP/1, so that a body of a
     * known length keeps its framing.
     *
     * @param factory The factory of bodies
     * @param body    The body, which is claimed
     * @return The body to relay
     */
    static CloseableByteBody withSanitizedTrailers(ByteBodyFactory factory, CloseableByteBody body) {
        if (body instanceof AvailableByteBody || body.expectedLength().isPresent()) {
            return body;
        }
        return factory.withTrailers(body, body.trailers().thenApply(RelayHeaders::sanitizeTrailers));
    }

    /**
     * Validate a {@code Host}: a host, a registered name, an IPv4 address or an IPv6 literal in
     * brackets, and an optional port of up to five digits, so that it cannot be read as another
     * target, e.g. {@code evil.example/path}, {@code user@evil.example} or {@code [::1}.
     *
     * @param host The value of a {@code Host}
     * @return The reason it is rejected, or {@code null}
     */
    static @Nullable String hostRejection(String host) {
        if (host.isEmpty()) {
            return "An empty Host header";
        }
        String port;
        if (host.charAt(0) == '[') {
            int end = host.indexOf(']');
            if (end < 0 || !isIpLiteral(host.substring(1, end))) {
                return "An invalid Host header";
            }
            String rest = host.substring(end + 1);
            if (rest.isEmpty()) {
                port = null;
            } else if (rest.charAt(0) == ':') {
                port = rest.substring(1);
            } else {
                return "An invalid Host header";
            }
        } else {
            int colon = host.indexOf(':');
            String name = colon < 0 ? host : host.substring(0, colon);
            port = colon < 0 ? null : host.substring(colon + 1);
            if (name.isEmpty() || !isRegName(name)) {
                return "An invalid Host header";
            }
        }
        if (port != null && !isPort(port)) {
            return "An invalid Host header";
        }
        return null;
    }

    private static boolean isRegName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || c == '.' || c == '-' || c == '_' || c == '~' || c == '%';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpLiteral(String literal) {
        if (literal.indexOf(':') < 0) {
            return false;
        }
        int zone = literal.indexOf('%');
        String address = zone < 0 ? literal : literal.substring(0, zone);
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            boolean allowed = c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c == ':' || c == '.';
            if (!allowed) {
                return false;
            }
        }
        return zone < 0 || isRegName(literal.substring(zone + 1)) && zone + 1 < literal.length();
    }

    private static boolean isPort(String port) {
        if (port.isEmpty() || port.length() > 5) {
            return false;
        }
        for (int i = 0; i < port.length(); i++) {
            char c = port.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return Integer.parseInt(port) <= 65535;
    }

    /**
     * @param uri A URI of an upstream
     * @return The URI without its user info, which may hold credentials, e.g. for a log or the
     * management endpoint
     */
    static String redact(URI uri) {
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null) {
            return uri.toString();
        }
        String s = uri.toString();
        int at = s.indexOf(userInfo + "@");
        return at < 0 ? s : s.substring(0, at) + "***@" + s.substring(at + userInfo.length() + 1);
    }
}
