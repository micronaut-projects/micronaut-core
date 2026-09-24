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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.filter.ServerRequestView;
import org.jspecify.annotations.Nullable;

/**
 * Finds the bytes of the body of the request a route is bound with, which a filter may have
 * replaced with another request: a request that is a {@link ServerHttpRequest} has its own bytes,
 * e.g. a request a filter continued with to replace the body, and an
 * {@link HttpRequestWrapper} has the bytes of the request it wraps, e.g. a request with another
 * method or URI.
 *
 * <p>A request whose {@link HttpRequest#getBody() body} is an object a filter set is bound from
 * that object, like before: the body binders only look for the bytes of a request without one.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ServerRequestBody {

    private ServerRequestBody() {
    }

    /**
     * The server request whose bytes are the body of the given request.
     *
     * @param request The request
     * @return The request itself if it is a server request, the server request it wraps or is
     * a mutable view of, see {@link ServerRequestView}, or {@code null} if there is none
     */
    public static @Nullable ServerHttpRequest<?> of(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (true) {
            if (current instanceof ServerHttpRequest<?> server) {
                return server;
            }
            if (current instanceof ServerRequestView view) {
                // e.g. the mutable view of the request that a filter method continued with
                return view.serverRequest();
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
    }
}
