/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.hostresolver;

import io.micronaut.http.HttpRequest;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * Implementation of {@link HostResolver} which resolves the host in the following strategies in order:
 * <ul>
 * <li>1. The value of the HTTP header {@value HttpHeaderHostResolverConfigurationProperties#DEFAULT_HOST_HEADER_NAME}  of the request, if not null</li>
 * <li>2. The host of the request URI, if not null</li>
 * <li>3. The host of the embedded server URI</li>
 * </ul>
 * The protocol is resolved with:
 * <ul>
 * <li>1. The value of the HTTP header {@value HttpHeaderHostResolverConfigurationProperties#DEFAULT_PROTOCOL_HEADER_NAME}  of the request, if not null</li>
 * <li>2. The scheme of the request URI, if not null</li>
 * <li>3. http</li>
 * </ul>
 *
 * @author Sergio del Amo
 * @since 1.2.0
 */
@Singleton
public class HttpHeaderHostResolver implements HostResolver {

    protected static final Logger LOG = LoggerFactory.getLogger(HttpHeaderHostResolver.class);
    private final Provider<EmbeddedServer> embeddedServer;

    private String hostHeaderName;
    private String protocolHeaderName;

    /**
     * @param embeddedServer The embedded server
     * @param httpHeaderHostResolverConfiguration Http Header Host resolver configuration
     */
    public HttpHeaderHostResolver(Provider<EmbeddedServer> embeddedServer,
                               HttpHeaderHostResolverConfiguration httpHeaderHostResolverConfiguration) {
        this.embeddedServer = embeddedServer;
        this.hostHeaderName = httpHeaderHostResolverConfiguration.getHostHeaderName();
        this.protocolHeaderName = httpHeaderHostResolverConfiguration.getProtocolHeaderName();
    }

    /**
     *
     * @param current The current request
     * @return The host
     */
    @Override
    @Nonnull
    public String resolve(@Nullable HttpRequest current) {
        return scheme(current) + "://" + host(current);
    }

    /**
     * Resolves the host in the following strategies in order:
     * 1. The HOST header of the request, if not null
     * 2. The host of the request URI, if not null
     * @param current Current Request
     * @param headerName HTTP Header name
     * @return the resolved host
     */
    @Nullable
    protected String hostByHeaderName(@Nullable HttpRequest current, String headerName) {
        String host = null;
        if (current != null) {
            host = current.getHeaders().get(headerName);
            if (host == null) {
                host = current.getUri().getHost();
            }
        }
        return host;
    }

    /**
     *
     * @param current Current Request
     * @param headerName HTTP Header name
     * @return the resolved scheme
     */
    protected String schemeByHeaderName(@Nullable HttpRequest current, String headerName) {
        String scheme = null;
        if (current != null) {
            scheme = current.getHeaders().get(headerName);
            if (scheme == null) {
                scheme = current.getUri().getScheme();
            }
        }
        return scheme;
    }

    /**
     *
     * @param current Current Request
     * @return the resolved host
     */
    protected String host(@Nullable HttpRequest current) {
        String host = hostByHeaderName(current, getHostHeaderName());
        if (host == null) {
            return embeddedServer.get().getURL().getHost();
        }
        return host;
    }

    /**
     *
     * @param current The current request
     * @return the resolved scheme
     */
    protected String scheme(@Nullable HttpRequest current) {
        String scheme = schemeByHeaderName(current, getProtocolHeaderName());
        if (scheme == null) {
            return "http";
        }
        return scheme;
    }

    /**
     *
     * @return The HTTP Header name used to resolve the HOST
     */
    public String getHostHeaderName() {
        return hostHeaderName;
    }

    /**
     *
     * @return The HTTP Header name used to resolve the scheme
     */
    public String getProtocolHeaderName() {
        return protocolHeaderName;
    }
}
