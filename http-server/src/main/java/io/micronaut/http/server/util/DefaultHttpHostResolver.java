/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.HttpServerConfiguration.HostResolutionConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.URI;

/**
 * Default implementation of {@link HttpHostResolver}.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Singleton
@Experimental
public class DefaultHttpHostResolver implements HttpHostResolver {

    private final Provider<EmbeddedServer> embeddedServer;
    private final HttpServerConfiguration serverConfiguration;

    /**
     * @param serverConfiguration The server configuration
     * @param embeddedServer The embedded server provider
     */
    public DefaultHttpHostResolver(HttpServerConfiguration serverConfiguration,
                                   Provider<EmbeddedServer> embeddedServer) {
        this.serverConfiguration = serverConfiguration;
        this.embeddedServer = embeddedServer;
    }

    @NonNull
    @Override
    public String resolve(@Nullable HttpRequest request) {
        if (request != null) {
            HostResolutionConfiguration configuration = serverConfiguration.getHostResolution();
            if (configuration != null) {
                return getConfiguredHost(request, configuration);
            } else {
                return getDefaultHost(request);
            }
        } else {
            return getEmbeddedHost();
        }
    }

    /**
     * @return The host resolved from the embedded server
     */
    protected String getEmbeddedHost() {
        EmbeddedServer server = embeddedServer.get();
        int port = server.getPort();
        if (port > -1 && port != 80 && port != 443) {
            return server.getScheme() + "://" + server.getHost() + ":" + port;
        } else {
            return server.getScheme() + "://" + server.getHost();
        }
    }

    /**
     * @param request The current request
     * @return The default host
     */
    protected String getDefaultHost(HttpRequest request) {
        ProxyHeaderParser proxyHeaderParser = new ProxyHeaderParser(request);
        if (proxyHeaderParser.getHost() != null) {
            return createHost(proxyHeaderParser.getScheme(), proxyHeaderParser.getHost(), proxyHeaderParser.getPort());
        }

        String hostHeader = request.getHeaders().get(HttpHeaders.HOST);
        if (hostHeader != null) {
            return getConfiguredHost(request, null, HttpHeaders.HOST, null, true);
        }

        URI uri = request.getUri();
        if (uri.getHost() != null) {
            return createHost(uri.getScheme(), uri.getHost(), uri.getPort());
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
    protected String getConfiguredHost(HttpRequest request, String schemeHeader, String hostHeader, String portHeader, boolean isPortInHost) {
        HttpHeaders headers = request.getHeaders();
        String scheme = null;
        if (schemeHeader != null) {
            scheme = headers.get(schemeHeader);
        }
        if (scheme == null) {
            scheme = request.getUri().getScheme();
        }
        if (scheme == null) {
            scheme = embeddedServer.get().getScheme();
        }

        String host = null;
        if (hostHeader != null) {
            host = headers.get(hostHeader);
        }
        if (host == null) {
            host = request.getUri().getHost();
        }
        if (host == null) {
            host = embeddedServer.get().getHost();
        }

        Integer port;
        if (isPortInHost && host != null && host.contains(":")) {
            String[] parts = host.split(":");
            host = parts[0].trim();
            port = Integer.valueOf(parts[1].trim());
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

    private String createHost(String scheme, String host, @Nullable Integer port) {
        if (port != null && port != 80 && port != 443) {
            return scheme + "://" + host + ":" + port;
        } else {
            return scheme + "://" + host;
        }
    }

}
