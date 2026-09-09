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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaderEntry;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.filter.ResponseHeaderPopulator;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static io.micronaut.http.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static io.micronaut.http.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;

/**
 * Creates a response-header populator for the configured cross-origin
 * policies.
 */
@Factory
@Internal
final class CorsResponseHeaderPopulatorFactory {
    private final HttpServerConfiguration.CorsConfiguration corsConfiguration;

    /**
     * @param corsConfiguration The CORS configuration containing the optional
     *                          cross-origin response policies
     */
    CorsResponseHeaderPopulatorFactory(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
        this.corsConfiguration = corsConfiguration;
    }

    /**
     * Creates a populator for the configured cross-origin policy headers.
     *
     * @return A populator for the configured policies
     * @throws DisabledBeanException If no cross-origin policy is configured
     */
    @Singleton
    ResponseHeaderPopulator crossOriginPolicies() {
        if (corsConfiguration.getCrossOriginResourcePolicy() == null
            && corsConfiguration.getCrossOriginEmbedderPolicy() == null) {
            throw new DisabledBeanException("No cross-origin response policies configured");
        }
        return (request, response) -> {
            List<HttpHeaderEntry> headers = new ArrayList<>(2);
            CrossOriginResourcePolicy policy = corsConfiguration.getCrossOriginResourcePolicy();
            if (policy != null) {
                headers.add(new HttpHeaderEntry(CROSS_ORIGIN_RESOURCE_POLICY, policy.toString()));
            }
            CrossOriginEmbedderPolicy embedderPolicy = corsConfiguration.getCrossOriginEmbedderPolicy();
            if (embedderPolicy != null) {
                headers.add(new HttpHeaderEntry(CROSS_ORIGIN_EMBEDDER_POLICY, embedderPolicy.toString()));
            }
            return headers.isEmpty() ? null : headers;
        };
    }
}
