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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import jakarta.inject.Singleton;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Default implementation of {@link HttpClientAddressResolver}.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Singleton
@Experimental
public class DefaultHttpClientAddressResolver implements HttpClientAddressResolver {

    private final HttpServerConfiguration serverConfiguration;

    /**
     * @param serverConfiguration The server configuration
     */
    public DefaultHttpClientAddressResolver(HttpServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
    }

    @Override
    @Nullable
    public String resolve(HttpRequest request) {
        String configuredHeader = serverConfiguration.getClientAddressHeader();
        if (configuredHeader != null) {
            return request.getHeaders().get(configuredHeader);
        }

        // Behind a TCP load balancer that sends the PROXY protocol, the client it reports. The
        // balancer passes the HTTP request through unchanged, so any forwarding header was
        // written by the client and must not override it.
        InetSocketAddress proxyProtocolClient = HttpClientAddressResolver.proxyProtocolClientAddress(request);
        if (proxyProtocolClient != null) {
            return proxyProtocolClient.getHostString();
        }

        ProxyHeaderParser proxyHeaderParser = new ProxyHeaderParser(request);
        List<String> addresses = proxyHeaderParser.getFor();
        if (addresses.isEmpty()) {
            InetSocketAddress address = request.getRemoteAddress();
            if (address != null) {
                return address.getHostString();
            } else {
                return null;
            }
        } else {
            return addresses.get(0);
        }
    }

}
