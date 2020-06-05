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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
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
    public String resolve(@Nonnull HttpRequest request) {
        String configuredHeader = serverConfiguration.getClientAddressHeader();
        if (configuredHeader != null) {
            return request.getHeaders().get(configuredHeader);
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
