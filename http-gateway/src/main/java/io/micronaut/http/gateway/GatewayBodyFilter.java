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

/**
 * A gateway filter that rewrites the whole body of the requests or of the responses of a route,
 * like {@link GatewayFilters#modifyRequestBody(long, BodyRewriter)} and
 * {@link GatewayFilters#modifyResponseBody(long, BodyRewriter)}, which are ones. The handler of
 * the route reads the body, up to {@link #maxBytes()}, decodes it, passes it through the
 * {@link #rewriter()} of each body filter of the target, in the order of the filters, and encodes
 * it again. A body over the limit is {@code 413} for a request and {@code 502} for a response.
 * A route with a body filter no longer streams that body.
 *
 * <p>Like any gateway filter, a body filter may also change the upstream request and response,
 * and declare route filters.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface GatewayBodyFilter extends GatewayFilter {

    /**
     * @return Whether the filter rewrites the body of the request or of the response
     */
    Target target();

    /**
     * @return The maximum number of bytes of the body, encoded and decoded, at least 1
     */
    long maxBytes();

    /**
     * @return The rewriter of the body
     */
    BodyRewriter rewriter();

    /**
     * The body a {@link GatewayBodyFilter} rewrites.
     */
    enum Target {
        /**
         * The body of the request, before it is relayed.
         */
        REQUEST,
        /**
         * The body of the response, before it is relayed to the client.
         */
        RESPONSE
    }
}
