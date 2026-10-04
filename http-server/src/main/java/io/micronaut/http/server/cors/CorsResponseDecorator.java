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
package io.micronaut.http.server.cors;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

/**
 * Strategy used by {@link CorsFilter} to add CORS headers to an HTTP response.
 *
 * <p>Implementations mutate the supplied response using values from the request and its resolved
 * {@link CorsOriginConfiguration}. They do not select the configuration or decide whether the
 * request is allowed; those steps must be completed before decoration.</p>
 *
 * @since 5.2.0
 */
@DefaultImplementation(DefaultCorsResponseDecorator.class)
public interface CorsResponseDecorator {
    /**
     * Adds headers used only for a successful preflight response. The requested method and header
     * names are copied from the preflight request, while maximum age and private-network permission
     * are obtained from the resolved configuration.
     *
     * <p>This method does not add the headers common to all CORS responses. Call
     * {@link #decorateResponseWithHeaders(HttpRequest, MutableHttpResponse, CorsOriginConfiguration)}
     * as well when constructing a complete preflight response.</p>
     *
     * @param request the CORS preflight request
     * @param response the response to decorate
     * @param config the CORS configuration resolved for the request
     * @since 5.2.0
     */
    void decorateResponseWithHeadersForPreflightRequest(HttpRequest<?> request,
                                                        MutableHttpResponse<?> response,
                                                        CorsOriginConfiguration config);

    /**
     * Adds headers common to successful preflight and actual CORS responses. These include the
     * allowed origin, exposed header names, credentials permission, and the origin-related
     * {@code Vary} header.
     *
     * @param request the CORS request
     * @param response the response to decorate
     * @param config the CORS configuration resolved for the request
     * @since 5.2.0
     */
    void decorateResponseWithHeaders(HttpRequest<?> request,
                                     MutableHttpResponse<?> response,
                                     CorsOriginConfiguration config);
}
