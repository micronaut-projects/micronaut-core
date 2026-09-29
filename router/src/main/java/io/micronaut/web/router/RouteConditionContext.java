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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.function.Supplier;

/**
 * What the router asks the server for a request, to evaluate the conditions of a route on the
 * host, the client address and the time of the request, see
 * {@link io.micronaut.web.router.builder.RouteCondition.Host},
 * {@link io.micronaut.web.router.builder.RouteCondition.RemoteAddress} and
 * {@link io.micronaut.web.router.builder.RouteCondition.TimeWindow}. The HTTP server provides a
 * bean that resolves them with its host and client address resolvers and its configuration;
 * without one, the router reads the {@code Host} header, the peer of the connection and the
 * system clock, see {@link #fallback()}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface RouteConditionContext {

    /**
     * @param request The request
     * @return The host of the request, as a host, a host and a port or a URI with a scheme, or
     * {@code null} if it has none
     */
    @Nullable String host(HttpRequest<?> request);

    /**
     * @param request The request
     * @return The address of the client of the request, with or without a port, or {@code null}
     * if it has none
     */
    @Nullable String clientAddress(HttpRequest<?> request);

    /**
     * @return The clock that tells when a request arrives
     */
    Clock clock();

    /**
     * @return The context without a server: the {@code Host} header, or the host of the URI, the
     * peer of the connection and the system clock
     */
    static RouteConditionContext fallback() {
        return Fallback.INSTANCE;
    }

    /**
     * @param context Supplies the context when a condition first needs it
     * @return A context resolved when it is first used, once
     */
    static RouteConditionContext lazy(Supplier<RouteConditionContext> context) {
        Supplier<RouteConditionContext> memoized = SupplierUtil.memoized(context);
        return new RouteConditionContext() {
            @Override
            public @Nullable String host(HttpRequest<?> request) {
                return memoized.get().host(request);
            }

            @Override
            public @Nullable String clientAddress(HttpRequest<?> request) {
                return memoized.get().clientAddress(request);
            }

            @Override
            public Clock clock() {
                return memoized.get().clock();
            }
        };
    }

    /**
     * The address of the peer of the connection of a request.
     *
     * @param request The request
     * @return The address, or {@code null} if it has none
     */
    static @Nullable String peerAddress(HttpRequest<?> request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return null;
        }
        InetAddress address = remote.getAddress();
        return address != null ? address.getHostAddress() : remote.getHostString();
    }

    /**
     * The context without a server.
     */
    final class Fallback implements RouteConditionContext {
        private static final Fallback INSTANCE = new Fallback();

        private Fallback() {
        }

        @Override
        public @Nullable String host(HttpRequest<?> request) {
            String host = request.getHeaders().get(HttpHeaders.HOST);
            return host != null ? host : request.getUri().getHost();
        }

        @Override
        public @Nullable String clientAddress(HttpRequest<?> request) {
            return peerAddress(request);
        }

        @Override
        public Clock clock() {
            return Clock.systemUTC();
        }
    }
}
