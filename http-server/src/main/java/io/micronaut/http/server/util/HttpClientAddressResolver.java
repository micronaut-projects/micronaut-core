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

import io.micronaut.context.annotation.DefaultImplementation;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequest;

import java.net.InetSocketAddress;

/**
 * Resolves the client IP address from the request.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@DefaultImplementation(DefaultHttpClientAddressResolver.class)
public interface HttpClientAddressResolver {

    /**
     * The request attribute that holds the client address reported by a PROXY protocol header,
     * an {@link InetSocketAddress}. The server sets it on the requests of a listener with the
     * PROXY protocol enabled when the header carries an address. Unlike
     * {@link HttpRequest#getRemoteAddress()}, which stays the peer of the connection (the load
     * balancer), it is the client that connected to the load balancer.
     *
     * @since 5.3.0
     */
    String PROXY_PROTOCOL_CLIENT_ADDRESS = "micronaut.http.server.proxy-protocol.client-address";

    /**
     * The client address reported by a PROXY protocol header of the connection of the request.
     *
     * @param request The request
     * @return The address, or {@code null} if the connection did not send one
     * @since 5.3.0
     */
    static @Nullable InetSocketAddress proxyProtocolClientAddress(HttpRequest<?> request) {
        return request.getAttribute(PROXY_PROTOCOL_CLIENT_ADDRESS, InetSocketAddress.class).orElse(null);
    }

    /**
     * @param request The current request
     * @return The client address
     */
    @Nullable
    String resolve(HttpRequest request);

}
