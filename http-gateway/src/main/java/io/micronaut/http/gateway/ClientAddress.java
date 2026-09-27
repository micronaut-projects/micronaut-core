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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.util.ForwardedHeaders;
import io.micronaut.web.router.builder.Cidr;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * The address of the client of a request behind trusted proxies.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ClientAddress {

    private static final int IPV6_LENGTH = 16;
    private static final int IPV6_NETWORK_LENGTH = 8;

    private ClientAddress() {
    }

    /**
     * The address of the client: the right-most address of {@code X-Forwarded-For} that is not a
     * trusted proxy, walking from the right while the addresses are trusted proxies, when the
     * peer is a trusted proxy; otherwise the peer.
     *
     * @param request The request
     * @param trusted The trusted proxies
     * @return The address, or {@code null}
     */
    public static byte @Nullable [] of(HttpRequest<?> request, List<Cidr> trusted) {
        InetSocketAddress peer = request.getRemoteAddress();
        byte[] address = peer == null || peer.getAddress() == null ? null : Cidr.address(peer.getAddress().getHostAddress());
        if (address == null || !isTrusted(address, trusted)) {
            return address;
        }
        List<String> hops = new ArrayList<>();
        for (String value : request.getHeaders().getAll(ForwardedHeaders.X_FORWARDED_FOR)) {
            for (String hop : value.split(",")) {
                hops.add(hop.strip());
            }
        }
        for (int i = hops.size() - 1; i >= 0; i--) {
            byte[] hop = Cidr.address(hops.get(i));
            if (hop == null) {
                return null;
            }
            address = hop;
            if (!isTrusted(hop, trusted)) {
                return hop;
            }
        }
        return address;
    }

    /**
     * @param request The request
     * @param trusted The trusted proxies
     * @return The address of the client as text, see {@link #of(HttpRequest, List)}, or {@code null}
     */
    public static @Nullable String text(HttpRequest<?> request, List<Cidr> trusted) {
        byte[] address = of(request, trusted);
        if (address == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * @param request The request
     * @param trusted The trusted proxies
     * @return The key of the client, see {@link KeyResolver#remoteAddress(List)}: its IPv4 address,
     * or the {@code /64} network of its IPv6 address, or {@code null}
     */
    public static @Nullable String key(HttpRequest<?> request, List<Cidr> trusted) {
        byte[] address = of(request, trusted);
        if (address == null) {
            return null;
        }
        if (address.length == IPV6_LENGTH) {
            byte[] network = new byte[IPV6_LENGTH];
            System.arraycopy(address, 0, network, 0, IPV6_NETWORK_LENGTH);
            address = network;
        }
        try {
            String text = InetAddress.getByAddress(address).getHostAddress();
            return address.length == IPV6_LENGTH ? text + "/64" : text;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean isTrusted(byte[] address, List<Cidr> trusted) {
        for (Cidr range : trusted) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }
}
