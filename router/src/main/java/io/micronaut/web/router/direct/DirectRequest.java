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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * What a server runtime received of a request, before it creates the
 * {@link io.micronaut.http.HttpRequest}: what {@link DirectRouteLookup#match(DirectRequest)}
 * matches the direct routes with, and what the function of a direct route reads, see
 * {@link io.micronaut.web.router.builder.DirectContext#request()}. The runtime implements it
 * over its own request, e.g. the Netty request or a servlet request. The lookup reads only what
 * the direct routes need: the path and the method, and the headers, the query and the peer
 * address when a condition asks for them, so an implementation should compute them lazily, e.g.
 * decode the query on the first call of {@link #queryParameters(String)} only.
 *
 * <p>A runtime that validates the request target or the query lazily throws an
 * {@link InvalidDirectRequestException} from {@link #path()} or {@link #queryParameters(String)}
 * when it finds them invalid: the request is then not answered by a direct route. The function of
 * a matched route reads a path that was validated, but may read an invalid query: its route then
 * declines the request.</p>
 *
 * <p>It is read on the thread that received the request, and by the function of a route that
 * runs on an executor, after the runtime handed the request over: one thread at a time.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface DirectRequest {

    /**
     * @return The name of the method, e.g. {@code GET}, or the name of a custom method
     */
    String methodName();

    /**
     * @return The method, {@link HttpMethod#CUSTOM} for a custom method, like
     * {@link io.micronaut.http.HttpRequest#getMethod()}
     */
    default HttpMethod method() {
        return HttpMethod.parse(methodName());
    }

    /**
     * @return The path of the request as it was received, without the query, like
     * {@link io.micronaut.http.HttpRequest#getPath()}: the path of the request target in origin
     * form, so for an absolute-form target, e.g. {@code GET http://host/p}, the path only
     * @throws InvalidDirectRequestException if the runtime validates the request target lazily,
     *                                       when the path is first read, and rejects it
     */
    String path();

    /**
     * @param name The name of a header, compared ignoring case
     * @return Its values, in the order they were received, empty if the request has none
     */
    List<String> headers(String name);

    /**
     * @param name The name of a header, compared ignoring case
     * @return Its first value, or {@code null} if the request has none
     */
    default @Nullable String header(String name) {
        List<String> values = headers(name);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * The values of a query parameter, decoded like the parameters of the
     * {@link io.micronaut.http.HttpRequest} the server would create. The lookup only calls it for
     * a route with a query condition: the server decodes the query when it is first called.
     *
     * @param name The name of the parameter
     * @return Its values, in their order, empty if the request has none
     * @throws InvalidDirectRequestException if the runtime decodes the query lazily, when it is
     *                                       first read, and rejects it
     */
    List<String> queryParameters(String name);

    /**
     * @param name The name of the parameter
     * @return Its first value, or {@code null} if the request has none
     * @throws InvalidDirectRequestException if the runtime decodes the query lazily, when it is
     *                                       first read, and rejects it
     */
    default @Nullable String queryParameter(String name) {
        List<String> values = queryParameters(name);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * @return The address of the peer of the connection, or {@code null} if it is not known
     */
    @Nullable InetSocketAddress peerAddress();
}
