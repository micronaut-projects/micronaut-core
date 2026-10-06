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
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * What a server runtime received of a request, read by
 * {@link DirectRouteLookup#find(DirectRequest, io.micronaut.http.HttpResponseFactory)}: the runtime
 * implements it over its own request, e.g. the Netty request or a servlet request, before it
 * creates the {@link io.micronaut.http.HttpRequest}. The lookup reads only what the direct routes
 * need: the path and the method, and the headers, the query and the peer address when a
 * condition asks for them, so an implementation should compute them lazily, e.g. decode the
 * query on the first call of {@link #queryParameters(String)} only.
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
     * @return The path of the request as it was received, without the query, like
     * {@link io.micronaut.http.HttpRequest#getPath()}: the path of the request target in origin
     * form, so for an absolute-form target, e.g. {@code GET http://host/p}, the path only
     */
    String path();

    /**
     * @param name The name of a header, compared ignoring case
     * @return Its values, in the order they were received, empty if the request has none
     */
    List<String> headers(String name);

    /**
     * The values of a query parameter, decoded like the parameters of the
     * {@link io.micronaut.http.HttpRequest} the server would create. The lookup only calls it for
     * a route with a query condition: the server decodes the query when it is first called.
     *
     * @param name The name of the parameter
     * @return Its values, in their order, empty if the request has none
     */
    List<String> queryParameters(String name);

    /**
     * @return The address of the peer of the connection, or {@code null} if it is not known
     */
    @Nullable InetSocketAddress peerAddress();
}
