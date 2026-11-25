/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.util;

import io.micronaut.context.BeanProvider;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.HttpServerConfiguration.HostResolutionConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link HttpHostResolver}.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Singleton
public class DefaultHttpHostResolver implements HttpHostResolver {

    private static final String DEFAULT_HOST = "http://localhost";
    private static final HostResolutionConfiguration DEFAULT_HOST_RESOLUTION = new HostResolutionConfiguration();
    @Nullable
    private final BeanProvider<EmbeddedServer> embeddedServer;
    private final HttpServerConfiguration serverConfiguration;

    /**
     * @param serverConfiguration The server configuration
     * @param embeddedServer The embedded server provider
     */
    @Inject
    public DefaultHttpHostResolver(HttpServerConfiguration serverConfiguration,
                                   @Nullable BeanProvider<EmbeddedServer> embeddedServer) {
        this.serverConfiguration = serverConfiguration;
        this.embeddedServer = embeddedServer;
    }

    @Override
    public String resolve(@Nullable HttpRequest request) {
        String host;
        if (request != null) {
            HostResolutionConfiguration configuration = serverConfiguration.getHostResolution();
            if (configuration != null && configuration.headersConfigured()) {
                host = getConfiguredHost(request, configuration);
            } else {
                host = getDefaultHost(request);
            }
        } else {
            host = getEmbeddedHost();
        }
        return validateHost(host);
    }

    /**
     * Validates the host transforming the host value if necessary.
     * @param host The host
     * @return The transformed host
     */
    protected String validateHost(String host) {
        if (!host.equals(DEFAULT_HOST)) {
            HostResolutionConfiguration configuration = serverConfiguration.getHostResolution();
            if (configuration != null) {
                List<Pattern> allowedHosts = configuration.getAllowedHosts();
                if (!allowedHosts.isEmpty() && allowedHosts.stream()
                        .map(pattern -> pattern.matcher(host))
                        .noneMatch(Matcher::matches)) {
                    return DEFAULT_HOST;
                }
            }
        }
        return host;
    }

    /**
     * @return The host resolved from the embedded server
     */
    protected String getEmbeddedHost() {
        if (embeddedServer != null) {
            EmbeddedServer server = embeddedServer.get();
            return createHost(server.getScheme(), server.getHost(), server.getPort());
        } else {
            return DEFAULT_HOST;
        }
    }

    /**
     * Resolves the host when no host, protocol or port header is configured. The {@code Forwarded} header
     * is read only while {@link HostResolutionConfiguration#isAllowForwarded()} is true, and the
     * {@code X-Forwarded-*} headers only while {@link HostResolutionConfiguration#isAllowXForwarded()} is true.
     * A subclass that overrides this method without calling {@code super} does not honour these settings.
     *
     * @param request The current request
     * @return The default host
     */
    protected String getDefaultHost(HttpRequest request) {
        HostResolutionConfiguration configuration = serverConfiguration.getHostResolution();
        if (configuration == null) {
            // no host-resolution property is set: use the defaults of the configuration class
            configuration = DEFAULT_HOST_RESOLUTION;
        }
        boolean allowForwarded = configuration.isAllowForwarded();
        boolean allowXForwarded = configuration.isAllowXForwarded();
        if (allowForwarded || allowXForwarded) {
            ProxyHeaderParser proxyHeaderParser = new ProxyHeaderParser(request, allowForwarded, allowXForwarded);
            String proxyScheme = proxyHeaderParser.getScheme();
            if (isValidHostName(proxyHeaderParser.getHost()) && (proxyScheme == null || isValidScheme(proxyScheme))) {
                return createHost(proxyHeaderParser.getScheme(), proxyHeaderParser.getHost(), proxyHeaderParser.getPort());
            }
        }

        String hostHeader = request.getHeaders().get(HttpHeaders.HOST);
        if (hostHeader != null && isValidAuthority(hostHeader, true)) {
            return getConfiguredHost(request, null, HttpHeaders.HOST, null, true);
        }

        URI uri = request.getUri();
        if (uri.getHost() != null) {
            Integer port = uri.getPort();
            if (port < 0) {
                port = null;
            }
            return createHost(uri.getScheme(), uri.getHost(), port);
        }

        return getEmbeddedHost();
    }

    /**
     * @param request The current request
     * @param configuration The configuration
     * @return The configured host
     */
    protected String getConfiguredHost(HttpRequest request, HostResolutionConfiguration configuration) {
        return getConfiguredHost(request, configuration.getProtocolHeader(), configuration.getHostHeader(), configuration.getPortHeader(), configuration.isPortInHost());
    }

    /**
     * @param request The current request
     * @param schemeHeader The scheme or protocol header name
     * @param hostHeader The host header name
     * @param portHeader The port header name
     * @param isPortInHost If the port can be part of the host value
     * @return The configured host
     */
    protected String getConfiguredHost(HttpRequest request, @Nullable String schemeHeader, @Nullable String hostHeader, @Nullable String portHeader, boolean isPortInHost) {
        HttpHeaders headers = request.getHeaders();
        String scheme = null;
        if (schemeHeader != null) {
            scheme = headers.get(schemeHeader);
            if (scheme != null && !isValidScheme(scheme)) {
                scheme = null;
            }
        }
        if (scheme == null) {
            scheme = request.getUri().getScheme();
        }
        if (scheme == null && embeddedServer != null) {
            scheme = embeddedServer.get().getScheme();
        }

        String host = null;
        if (hostHeader != null) {
            host = headers.get(hostHeader);
            if (host != null && !isValidAuthority(host, isPortInHost)) {
                host = null;
            }
        }
        if (host == null) {
            host = request.getUri().getHost();
        }
        if (host == null && embeddedServer != null) {
            host = embeddedServer.get().getHost();
        }

        Integer port;
        int separatorIndex = -1;
        if (host != null) {
            // the port separator of a bracketed IPv6 literal follows the closing bracket
            int ipv6End = host.startsWith("[") ? host.indexOf(']') : -1;
            separatorIndex = host.indexOf(':', ipv6End + 1);
        }
        if (isPortInHost && separatorIndex != -1) {
            try {
                port = Integer.valueOf(host.substring(separatorIndex + 1).trim());
            } catch (NumberFormatException e) {
                port = null;
            }
            host = host.substring(0, separatorIndex).trim();
        } else if (portHeader != null) {
            port = headers.get(portHeader, Integer.class).orElse(null);
        } else {
            port = request.getUri().getPort();
            if (port < 0) {
                port = null;
            }
        }

        return createHost(scheme, host, port);
    }

    /**
     * @param value A scheme
     * @return Whether it has the syntax of a URI scheme (RFC 3986 section 3.1)
     */
    private static boolean isValidScheme(String value) {
        if (value.isEmpty() || !isAsciiLetter(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isAsciiLetter(c) && !isAsciiDigit(c) && c != '+' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    /**
     * @param value A host value, optionally followed by a port
     * @param portInHost Whether anything after the port separator is removed before the host is used
     * @return Whether the host is a valid host name or bracketed IP literal, and the port, if
     * it stays part of the host, is made of digits
     */
    private static boolean isValidAuthority(String value, boolean portInHost) {
        String trimmed = value.trim();
        int hostEnd;
        if (trimmed.startsWith("[")) {
            hostEnd = trimmed.indexOf(']') + 1;
            if (hostEnd == 0) {
                return false;
            }
        } else {
            int separator = trimmed.indexOf(':');
            hostEnd = separator == -1 ? trimmed.length() : separator;
        }
        if (!isValidHostName(trimmed.substring(0, hostEnd))) {
            return false;
        }
        if (hostEnd == trimmed.length()) {
            return true;
        }
        if (trimmed.charAt(hostEnd) != ':') {
            return false;
        }
        if (portInHost) {
            return true;
        }
        String port = trimmed.substring(hostEnd + 1);
        if (port.isEmpty()) {
            return false;
        }
        for (int i = 0; i < port.length(); i++) {
            if (!isAsciiDigit(port.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param host A host without a port
     * @return Whether it is a host name or IPv4 address made of letters, digits, {@code -},
     * {@code .}, {@code _} and {@code ~}, or a bracketed IP literal
     */
    private static boolean isValidHostName(@Nullable String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (host.startsWith("[")) {
            if (host.length() < 3 || host.charAt(host.length() - 1) != ']') {
                return false;
            }
            for (int i = 1; i < host.length() - 1; i++) {
                char c = host.charAt(i);
                if (!isAsciiLetter(c) && !isAsciiDigit(c) && c != ':' && c != '.' && c != '%' && c != '-' && c != '_' && c != '~') {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (!isAsciiLetter(c) && !isAsciiDigit(c) && c != '-' && c != '.' && c != '_' && c != '~') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private String createHost(@Nullable String scheme, @Nullable String host, @Nullable Integer port) {
        scheme = scheme == null ? "http" : scheme;
        host = host == null ? "localhost" : host;
        if (port != null && port != 80 && port != 443) {
            return scheme + "://" + host + ":" + port;
        } else {
            return scheme + "://" + host;
        }
    }

}
