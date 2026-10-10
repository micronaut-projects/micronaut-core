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
import io.micronaut.http.HttpRequestWrapper;

import java.util.function.Predicate;

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

    @Override
    public boolean test(HttpRequest<?> request) {
        return request instanceof AssumedUpgradeRequest<?> || isWebSocketUpgrade(request);
    }

    /**
     * A view of a request that meets this condition and otherwise has the conditions of the
     * routes apply to it as to the request, to find the WebSocket a plain request is for.
     *
     * @param request The request
     * @param <B>     The body type
     * @return The view of the request
     */
    public static <B> HttpRequest<B> assumeUpgrade(HttpRequest<B> request) {
        return new AssumedUpgradeRequest<>(request);
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

    private static final class AssumedUpgradeRequest<B> extends HttpRequestWrapper<B> {
        AssumedUpgradeRequest(HttpRequest<B> delegate) {
            super(delegate);
        }
    }
}
