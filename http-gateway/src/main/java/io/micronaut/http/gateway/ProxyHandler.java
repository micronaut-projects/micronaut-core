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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.web.router.builder.AsyncRequestHandler;

/**
 * The handler of a gateway route: it relays each request to the upstream of the route and relays
 * the response back while its body streams. Get one from the {@link ProxyHandlerFactory}, and
 * route to it like to any handler, e.g.
 * {@code routes.any("/api/orders/{+path}").consumesAll().handleAsync(proxies.create(route))}, or declare
 * {@link GatewayRoutes gateway routes}, which do both.
 *
 * <p>For each request it:</p>
 * <ol>
 *     <li>answers {@code 400} for a path that an upstream could resolve to another path than the
 *     router matched: a dot segment, an encoded slash or backslash, a path parameter
 *     ({@code ;}) or a control character;</li>
 *     <li>builds the upstream request: the method, the raw path and query, and the headers of the
 *     request, without the hop-by-hop headers, except {@code Upgrade} and
 *     {@code Connection: Upgrade} for a request to switch protocols, e.g. to a WebSocket, and with
 *     the forwarded headers of the gateway configuration;</li>
 *     <li>applies the {@link GatewayFilter proxy transforms} of the route to it, once;</li>
 *     <li>sends it with the raw HTTP client, without following redirects, with a {@code Host}
 *     header of the upstream and without decompressing the response;</li>
 *     <li>applies the proxy transforms to the response, without its hop-by-hop headers, and
 *     relays it while its body streams, with its trailers, or relays the switched connection of a
 *     {@code 101} response.</li>
 * </ol>
 * A request that cannot be relayed fails with an {@link UpstreamException}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface ProxyHandler extends AsyncRequestHandler permits DefaultProxyHandler {

    /**
     * @return The route the handler relays for
     */
    GatewayRoute getRoute();
}
