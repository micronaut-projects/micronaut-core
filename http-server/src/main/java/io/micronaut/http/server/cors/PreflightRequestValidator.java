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

/**
 * Strategy used by {@link CorsFilter} to validate CORS preflight requests after an applicable
 * {@link CorsOriginConfiguration} has been resolved for the request origin.
 *
 * <p>Validation covers the requested HTTP method and headers, private-network access, and whether
 * the requested resource is available for the requested method. Origin matching is performed
 * while resolving {@code config} and is therefore outside this contract.</p>
 *
 * @since 5.2.0
 */
@DefaultImplementation(DefaultPreflightRequestValidator.class)
public interface PreflightRequestValidator {
    /**
     * Determines whether a preflight request may proceed under the resolved CORS configuration.
     *
     * @param request the request to validate; expected to be a CORS preflight request
     * @param config the CORS configuration previously resolved for the request origin
     * @return {@code true} when the preflight request is valid and may proceed, or {@code false}
     * when the server should reject it
     * @since 5.2.0
     */
    boolean validatePreflightRequest(HttpRequest<?> request,
                                     CorsOriginConfiguration config);
}
