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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.web.router.builder.RequestCondition.AllOf;
import io.micronaut.web.router.builder.RequestCondition.AnyOf;
import io.micronaut.web.router.builder.RequestCondition.Body;
import io.micronaut.web.router.builder.RequestCondition.Cookie;
import io.micronaut.web.router.builder.RequestCondition.Custom;
import io.micronaut.web.router.builder.RequestCondition.Header;
import io.micronaut.web.router.builder.RequestCondition.Host;
import io.micronaut.web.router.builder.RequestCondition.Method;
import io.micronaut.web.router.builder.RequestCondition.Not;
import io.micronaut.web.router.builder.RequestCondition.PeerAddress;
import io.micronaut.web.router.builder.RequestCondition.Query;
import io.micronaut.web.router.builder.RequestCondition.RemoteAddress;
import io.micronaut.web.router.builder.RequestCondition.TimeWindow;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.direct.DirectRequest;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * The conditions of direct routes: the conditions a server decides from the request it
 * received, before it creates the {@link io.micronaut.http.HttpRequest}, see
 * {@link DirectRouteSpec#where(RequestCondition)}. They are evaluated against a
 * {@link DirectRequest}, with the matchers of {@link RequestConditions}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DirectConditions {

    private DirectConditions() {
    }

    /**
     * Reject a condition a direct route cannot decide.
     *
     * @param condition The condition
     * @param route     The route, for the message
     * @throws IllegalArgumentException if the condition, or a part of it, needs the request
     */
    static void check(RequestCondition condition, Object route) {
        String unsupported = unsupported(condition);
        if (unsupported != null) {
            throw new IllegalArgumentException("The " + route + " cannot have the condition " + condition
                + ": a direct route is matched before the request is created, from its method, path, headers and peer address only, "
                + "and " + unsupported + " needs the request. Use method, header, cookie, query, host, peerAddress "
                + "or time conditions, or declare an ordinary route instead");
        }
    }

    /**
     * @return A description of the first part of the condition a direct route cannot decide, or {@code null}
     */
    private static @Nullable String unsupported(RequestCondition condition) {
        return switch (condition) {
            case Method method -> null;
            case Header header -> null;
            case Cookie cookie -> null;
            case Host host -> null;
            case PeerAddress peerAddress -> null;
            case Query query -> null;
            case TimeWindow timeWindow -> null;
            case RemoteAddress remoteAddress -> "a client address condition, resolved from the request and its forwarded headers";
            case Custom custom -> "a condition of a lambda";
            case Body<?> body -> "a body condition, which reads the body of the request";
            case Not not -> unsupported(not.condition());
            case AllOf allOf -> firstUnsupported(allOf.conditions());
            case AnyOf anyOf -> firstUnsupported(anyOf.conditions());
        };
    }

    private static @Nullable String firstUnsupported(List<RequestCondition> conditions) {
        for (RequestCondition part : conditions) {
            String unsupported = unsupported(part);
            if (unsupported != null) {
                return unsupported;
            }
        }
        return null;
    }

    /**
     * Whether a request meets a normalized condition.
     *
     * @param condition The condition, checked with {@link #check}
     * @param request   The request as the server received it
     * @param context   Gives the clock of the time conditions
     * @return Whether it meets the condition
     */
    static boolean matches(RequestCondition condition, DirectRequest request, RouteConditionContext context) {
        return switch (condition) {
            case Method method -> method.methods().contains(request.methodName())
                || HttpMethod.parse(request.methodName()) == HttpMethod.CUSTOM && method.methods().contains(HttpMethod.CUSTOM.name());
            case Header header -> RequestConditions.anyValue(header.value(), request.headers(header.name()));
            case Query query -> RequestConditions.anyValue(query.value(), request.queryParameters(query.name()));
            case TimeWindow timeWindow -> RequestConditions.timeWindow(timeWindow, context.clock().instant());
            case Cookie cookie -> RequestConditions.cookie(cookie, request.headers(HttpHeaders.COOKIE));
            case Host host -> RequestConditions.host(host.host(), host(request));
            case PeerAddress peerAddress -> {
                InetSocketAddress peer = request.peerAddress();
                InetAddress address = peer == null ? null : peer.getAddress();
                yield address != null && RequestConditions.inRanges(peerAddress.ranges(), Cidr.address(address));
            }
            case AllOf allOf -> {
                for (RequestCondition part : allOf.conditions()) {
                    if (!matches(part, request, context)) {
                        yield false;
                    }
                }
                yield true;
            }
            case AnyOf anyOf -> {
                for (RequestCondition part : anyOf.conditions()) {
                    if (matches(part, request, context)) {
                        yield true;
                    }
                }
                yield false;
            }
            case Not not -> !matches(not.condition(), request, context);
            // rejected when the route is built
            case Custom custom -> throw new IllegalStateException("Unsupported condition of a direct route: " + custom);
            case RemoteAddress remoteAddress -> throw new IllegalStateException("Unsupported condition of a direct route: " + remoteAddress);
            case Body<?> body -> throw new IllegalStateException("Unsupported condition of a direct route: " + body);
        };
    }

    /**
     * @return The {@code Host} header, never a forwarded host: HTTP/2 servers convert the
     * authority of a request to it
     */
    private static @Nullable String host(DirectRequest request) {
        List<String> hosts = request.headers(HttpHeaders.HOST);
        return hosts.isEmpty() ? null : hosts.get(0);
    }
}
