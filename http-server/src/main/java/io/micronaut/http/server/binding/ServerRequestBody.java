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
import io.micronaut.http.body.DirectByteBodyAccess;
import org.jspecify.annotations.Nullable;

/**
 * Finds the bytes of the body of the request a route is bound with, which a filter may have
 * replaced with another request: a request that is a {@link ServerHttpRequest} has its own bytes,
 * e.g. a request a filter continued with to replace the body, and an
 * {@link HttpRequestWrapper} has the bytes of the request it wraps, e.g. a request with another
 * method or URI. A mutable copy of a server request is a server request with the bytes of that
 * request, as long as its body was not set: a request with {@link DirectByteBodyAccess direct
 * access} to its bytes that has none, e.g. because a filter set its body, even to {@code null},
 * has no bytes to bind.
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
     * a mutable copy of, or {@code null} if there is none or the body of a copy was set
     */
    public static @Nullable ServerHttpRequest<?> of(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (true) {
            if (current instanceof DirectByteBodyAccess access && access.byteBodyDirect() == null) {
                // e.g. a mutable copy whose body a filter set, even to null
                return null;
            }
            if (current instanceof ServerHttpRequest<?> server) {
                return server;
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
    }

    /**
     * The first server request of the given request, whose body a filter may have set: the
     * request itself, or the server request it wraps.
     *
     * @param request The request
     * @return The server request, or {@code null} if there is none
     */
    public static @Nullable ServerHttpRequest<?> serverRequest(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (true) {
            if (current instanceof ServerHttpRequest<?> server) {
                return server;
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
    }

    /**
     * Whether a filter set the body of the given request, even to {@code null}: its body is then
     * the object the filter set, and not the bytes of the server request, which are never read.
     * The first request that gives access to the bytes it was received with, see
     * {@link DirectByteBodyAccess}, walking the wrappers, answers: it has none once the body was
     * replaced.
     *
     * @param request The request
     * @return {@code true} if the body was set
     */
    public static boolean isBodySet(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (true) {
            if (current instanceof DirectByteBodyAccess access) {
                return access.byteBodyDirect() == null;
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return false;
            }
        }
    }
}
