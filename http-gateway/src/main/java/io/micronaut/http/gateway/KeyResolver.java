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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.builder.Cidr;
import org.jspecify.annotations.Nullable;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

/**
 * The key a request is counted under by a {@link GatewayRateLimit rate limit} or a
 * {@link GatewayFilters#bulkhead(int, KeyResolver) bulkhead}, like Spring Cloud Gateway's
 * {@code KeyResolver}. A bean of this type named in the configuration, e.g.
 * {@code key-resolver: tenant}, is one too. It runs for every request of its route, so it is
 * synchronous: it reads the request, e.g. a header or the principal, and does no IO.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface KeyResolver {

    /**
     * @param request The request
     * @return The key, {@code null} or empty for none, e.g. a request without a principal
     */
    @Nullable String resolve(HttpRequest<?> request);

    /**
     * The address of the client: the peer, or, when the peer is a trusted proxy, the right-most
     * address of {@code X-Forwarded-For} that is not a trusted proxy. An IPv6 address is keyed by
     * its {@code /64} network, which a single client usually holds whole: the addresses of one
     * network share a key.
     *
     * @param trustedProxies The trusted proxies, in CIDR notation; none to key by the peer
     * @return The resolver
     */
    static KeyResolver remoteAddress(List<String> trustedProxies) {
        List<Cidr> trusted = trustedProxies.stream().map(Cidr::parse).toList();
        return request -> ClientAddress.key(request, trusted);
    }

    /**
     * @return The resolver of the name of the principal of the request
     */
    static KeyResolver principalName() {
        return request -> request.getUserPrincipal().map(Principal::getName).orElse(null);
    }

    /**
     * @param name The name of a header
     * @return The resolver of the first value of the header
     */
    static KeyResolver header(String name) {
        Objects.requireNonNull(name, "name");
        return request -> request.getHeaders().get(name);
    }
}
