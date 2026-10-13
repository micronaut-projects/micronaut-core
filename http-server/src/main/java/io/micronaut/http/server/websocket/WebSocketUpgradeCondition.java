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
package io.micronaut.http.server.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The condition of the route of a WebSocket: a WebSocket upgrade request, whose
 * {@code Connection} header has the {@code Upgrade} token and whose {@code Upgrade} header has
 * the {@code websocket} token. A plain HTTP request to the path of a WebSocket is left to the
 * other routes.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public enum WebSocketUpgradeCondition implements Predicate<HttpRequest<?>> {
    INSTANCE;

    private static final String UPGRADE = "upgrade";
    private static final String WEBSOCKET = "websocket";
    private static final String ASSUME_UPGRADE = WebSocketUpgradeCondition.class.getName() + ".ASSUME_UPGRADE";

    /**
     * Whether a route of a WebSocket was registered. It is only ever set: with it set, a lookup
     * for the WebSocket at the path of a plain request is merely not skipped.
     */
    private static volatile boolean registered;

    @Override
    public boolean test(HttpRequest<?> request) {
        return isWebSocketUpgrade(request) || request.getAttribute(ASSUME_UPGRADE).isPresent();
    }

    /**
     * Mark that a route of a WebSocket is registered with this condition.
     */
    static void registered() {
        registered = true;
    }

    /**
     * Run a lookup of the routes of a request, which meets this condition during the lookup and
     * otherwise has the conditions of the routes apply to it as they are, to also find the
     * WebSocket a plain request is for. The request is marked with an attribute for the lookup,
     * so that conditions that read the request of the context see the same request.
     *
     * @param request The request
     * @param lookup  The lookup
     * @param <T>     The result type
     * @return The result of the lookup
     */
    public static <T> T assumingUpgrade(HttpRequest<?> request, Supplier<T> lookup) {
        if (!registered) {
            // no WebSocket can match: the lookup finds the same routes
            return lookup.get();
        }
        request.setAttribute(ASSUME_UPGRADE, Boolean.TRUE);
        try {
            return lookup.get();
        } finally {
            request.removeAttribute(ASSUME_UPGRADE, Boolean.class);
        }
    }

    /**
     * @param request The request
     * @return Whether the request asks to upgrade to a WebSocket
     */
    private static boolean isWebSocketUpgrade(HttpRequest<?> request) {
        HttpHeaders headers = request.getHeaders();
        return hasToken(headers, HttpHeaders.CONNECTION, UPGRADE)
            && hasToken(headers, HttpHeaders.UPGRADE, WEBSOCKET);
    }

    private static boolean hasToken(HttpHeaders headers, String name, String token) {
        for (String value : headers.getAll(name)) {
            for (String part : StringUtils.splitOmitEmptyStrings(value, ',')) {
                if (part.trim().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "WebSocket upgrade request";
    }
}
