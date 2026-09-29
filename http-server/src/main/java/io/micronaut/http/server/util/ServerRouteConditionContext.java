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
package io.micronaut.http.server.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteConditionContext;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;

/**
 * The {@link RouteConditionContext} of the server: the host and the client address of a request
 * as the {@link HttpHostResolver} and the {@link HttpClientAddressResolver} resolve them, with the
 * configuration {@code micronaut.server.host-resolution} and
 * {@code micronaut.server.client-address-header}, and the {@link Clock} bean if there is one,
 * otherwise the system clock. A request whose client address the resolver does not resolve, e.g.
 * without the configured header, has the address of the peer of its connection.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class ServerRouteConditionContext implements RouteConditionContext {

    private final HttpHostResolver hostResolver;
    private final HttpClientAddressResolver clientAddressResolver;
    private final Clock clock;

    /**
     * @param hostResolver          The host resolver
     * @param clientAddressResolver The client address resolver
     * @param clock                 The clock, or {@code null} for the system clock
     */
    ServerRouteConditionContext(HttpHostResolver hostResolver,
                                HttpClientAddressResolver clientAddressResolver,
                                @Nullable Clock clock) {
        this.hostResolver = hostResolver;
        this.clientAddressResolver = clientAddressResolver;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public @Nullable String host(HttpRequest<?> request) {
        return hostResolver.resolve(request);
    }

    @Override
    public @Nullable String clientAddress(HttpRequest<?> request) {
        String address = clientAddressResolver.resolve(request);
        return address != null ? address : RouteConditionContext.peerAddress(request);
    }

    @Override
    public Clock clock() {
        return clock;
    }
}
