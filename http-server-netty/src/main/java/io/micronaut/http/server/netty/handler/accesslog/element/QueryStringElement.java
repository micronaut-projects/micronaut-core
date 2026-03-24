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
 * QueryStringElement LogElement. The request query string.
 *
 * @author yawkat
 * @since 5.0.0
 */
final class QueryStringElement implements LogElement {
    /**
     * The query string marker.
     */
    public static final String QUERY_STRING = "q";

    /**
     * The QueryStringElement instance.
     */
    static final QueryStringElement INSTANCE = new QueryStringElement();

    private QueryStringElement() {
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
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 && queryStart < uri.length() - 1 ? uri.substring(queryStart + 1) : "";
    }

    @Override
    public Set<Event> events() {
        return Event.REQUEST_HEADERS_EVENTS;
    }

    @Override
    public String toString() {
        return '%' + QUERY_STRING;
    }
}
