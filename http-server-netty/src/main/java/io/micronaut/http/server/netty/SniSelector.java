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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.SslContextAutoLoader;
import io.micronaut.http.netty.SslContextHolder;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Selects the SSL context of a listener by the TLS server name (SNI). Exact host names take
 * precedence over wildcard entries ({@code *.example.com}, matching exactly one label).
 *
 * @param byHostName SSL context loaders by normalized host name or wildcard pattern
 * @param strict     Whether requests for another host than the server name are rejected with 421
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record SniSelector(Map<String, SslContextAutoLoader> byHostName, boolean strict) {
    /**
     * The server name the client sent in the TLS handshake, set on the connection channel when
     * an SNI-enabled listener completes the selection. Absent when the client sent no name.
     */
    static final AttributeKey<String> SERVER_NAME = AttributeKey.valueOf(SniSelector.class, "server-name");

    /**
     * Find the loader for the given server name.
     *
     * @param serverName The server name from the client hello, or {@code null}
     * @return The loader, or {@code null} if the default certificate applies
     */
    @Nullable
    SslContextAutoLoader find(@Nullable String serverName) {
        if (serverName == null) {
            return null;
        }
        String name = normalize(serverName);
        SslContextAutoLoader exact = byHostName.get(name);
        if (exact != null) {
            return exact;
        }
        int dot = name.indexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return byHostName.get("*" + name.substring(dot));
        }
        return null;
    }

    /**
     * Take the current context for the given server name.
     *
     * @param serverName The server name from the client hello, or {@code null}
     * @return The retained holder, or {@code null} if the default certificate applies or the
     * host specific context is not available
     */
    @Nullable
    SslContextHolder takeRetained(@Nullable String serverName) {
        SslContextAutoLoader loader = find(serverName);
        return loader == null ? null : loader.takeRetained();
    }

    /**
     * Whether the host of a request matches the TLS server name of its connection.
     *
     * @param serverName The server name of the connection
     * @param authority  The {@code Host} header or {@code :authority}, possibly with a port
     * @return {@code true} if the request may be served
     */
    static boolean authorityMatches(String serverName, @Nullable String authority) {
        if (authority == null) {
            // HTTP/1.0 without a host: nothing to compare against
            return true;
        }
        String host = authority.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            host = end < 0 ? host : host.substring(1, end);
        } else {
            int colon = host.lastIndexOf(':');
            if (colon >= 0 && host.indexOf(':') == colon) {
                host = host.substring(0, colon);
            }
        }
        return normalize(host).equals(normalize(serverName));
    }

    private static String normalize(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
    }
}
