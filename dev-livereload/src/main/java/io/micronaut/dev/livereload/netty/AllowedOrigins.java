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
package io.micronaut.dev.livereload.netty;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The origins whose pages may follow the LiveReload socket. A WebSocket is not bound by the same-origin policy, so
 * without a check any site the developer visits could connect to the loopback port, watch the reloads and hold
 * connections open. Always allowed are clients that are no page, which send no origin; the browser extensions the
 * protocol was made for; and pages served by a localhost name or the loopback address, on any port, which are the
 * application's pages wherever its generations bind, and this server's own. A page of another site is refused, and so
 * is a rebinding page, whose origin keeps the name it was loaded from. The script a browser extension injects runs in
 * the page and connects with the page's origin.
 *
 * <p>An application opened through another name, a LAN host or the name of a container, is allowed only when it is
 * configured ({@code micronaut.dev.livereload.allowed-origins}): an origin, {@code http://devbox.lan:8080}, allows that
 * scheme, host and port; a host name alone, {@code devbox.lan}, allows its pages over HTTP or HTTPS on any port. No
 * pattern is accepted: an entry with a wildcard, or one that is not an origin, is ignored with a warning, so that the
 * socket never opens to every site.</p>
 *
 * @param origins The origins configured, each as {@code scheme://host:port}
 * @param hosts The host names configured, of any port
 * @author graemerocher
 * @since 5.3.0
 */
record AllowedOrigins(Set<String> origins, Set<String> hosts) {

    /**
     * The clients that are no page, the browser extensions, and the pages of a localhost name or the loopback address.
     */
    static final AllowedOrigins LOOPBACK = new AllowedOrigins(Set.of(), Set.of());

    private static final Logger LOG = LoggerFactory.getLogger(AllowedOrigins.class);
    // the origins of the LiveReload browser extensions
    private static final Set<String> EXTENSION_SCHEMES = Set.of("chrome-extension", "moz-extension", "safari-web-extension");

    /**
     * The loopback set and the origins configured.
     *
     * @param configured The configured entries, origins or host names
     * @return The allowed origins
     */
    static AllowedOrigins of(Collection<String> configured) {
        Set<String> origins = new LinkedHashSet<>();
        Set<String> hosts = new LinkedHashSet<>();
        for (String entry : configured) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.contains("*")) {
                LOG.warn("The LiveReload origin {} is ignored: name each origin or host, no pattern is accepted", value);
                continue;
            }
            if (value.contains("://")) {
                String origin = canonical(value);
                if (origin == null) {
                    LOG.warn("The LiveReload origin {} is ignored: it is not an http or https origin, such as http://devbox.lan:8080", value);
                } else {
                    origins.add(origin);
                }
            } else if (isHostName(value)) {
                hosts.add(value.toLowerCase(Locale.ROOT));
            } else {
                LOG.warn("The LiveReload origin {} is ignored: give an origin, such as http://devbox.lan:8080, or a host name", value);
            }
        }
        return origins.isEmpty() && hosts.isEmpty() ? LOOPBACK : new AllowedOrigins(Set.copyOf(origins), Set.copyOf(hosts));
    }

    /**
     * Whether a page of this origin may follow the LiveReload socket.
     *
     * @param origin The {@code Origin} header, null when absent
     * @return Whether the upgrade is allowed
     */
    boolean allows(@Nullable String origin) {
        if (origin == null) {
            return true;
        }
        try {
            URI uri = new URI(origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (EXTENSION_SCHEMES.contains(scheme)) {
                return true;
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || host.isEmpty()) {
                return false;
            }
            if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1") || host.equals("[::1]")) {
                return true;
            }
            return hosts.contains(host) || origins.contains(canonical(uri));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Nullable
    private static String canonical(String origin) {
        try {
            URI uri = new URI(origin);
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null || path != null && !path.isEmpty() && !path.equals("/")) {
                return null;
            }
            return canonical(uri);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    @Nullable
    private static String canonical(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || host.isEmpty()) {
            return null;
        }
        // a browser leaves the default port out of an origin
        int port = uri.getPort() >= 0 ? uri.getPort() : scheme.equals("https") ? 443 : 80;
        return scheme + "://" + host + ":" + port;
    }

    private static boolean isHostName(String value) {
        try {
            URI uri = new URI("http://" + value);
            return value.equalsIgnoreCase(uri.getHost()) && uri.getPort() < 0 && uri.getRawPath().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
