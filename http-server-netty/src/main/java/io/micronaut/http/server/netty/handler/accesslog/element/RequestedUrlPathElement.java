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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.core.annotation.NonNull;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Set;

/**
 * RequestedUrlPathElement LogElement. The request URL path.
 *
 * @author yawkat
 * @since 5.0.0
 */
final class RequestedUrlPathElement implements LogElement {
    /**
     * The requested URL path marker.
     */
    public static final String REQUESTED_URL_PATH = "U";

    /**
     * The RequestedUrlPathElement instance.
     */
    static final RequestedUrlPathElement INSTANCE = new RequestedUrlPathElement();

    private RequestedUrlPathElement() {
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String onRequestHeaders(@NonNull ConnectionMetadata metadata,
                                   @NonNull String method,
                                   @NonNull HttpHeaders headers,
                                   @NonNull String uri,
                                   @NonNull String protocol) {
        return extractPath(uri);
    }

    private static String extractPath(String uri) {
        int pathStart = 0;
        int schemeSeparator = uri.indexOf("://");
        if (schemeSeparator >= 0) {
            pathStart = uri.indexOf('/', schemeSeparator + 3);
            if (pathStart < 0) {
                return "/";
            }
        } else if (uri.startsWith("//")) {
            pathStart = uri.indexOf('/', 2);
            if (pathStart < 0) {
                return "/";
            }
        }

        int queryStart = uri.indexOf('?', pathStart);
        int fragmentStart = uri.indexOf('#', pathStart);
        int pathEnd = uri.length();
        if (queryStart >= 0 && queryStart < pathEnd) {
            pathEnd = queryStart;
        }
        if (fragmentStart >= 0 && fragmentStart < pathEnd) {
            pathEnd = fragmentStart;
        }
        return uri.substring(pathStart, pathEnd);
    }

    @Override
    public Set<Event> events() {
        return Event.REQUEST_HEADERS_EVENTS;
    }

    @Override
    public String toString() {
        return '%' + REQUESTED_URL_PATH;
    }
}
