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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaderTuple;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.filter.ResponseHeaderPopulator;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.http.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static io.micronaut.http.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;

/**
 * Creates response-header populators for the configured cross-origin
 * policies.
 *
 * <p>Each policy is exposed as an independent bean. When a policy is not
 * configured, its factory method disables only that bean.</p>
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
     * Creates a populator for the configured Cross-Origin-Resource-Policy
     * header.
     *
     * @return A populator for the configured policy
     * @throws DisabledBeanException If no resource policy is configured
     */
    @Named("crossOriginResourcePolicy")
    @Singleton
    @Requires(property = "micronaut.server.cors.cross-origin-resource-policy")
    ResponseHeaderPopulator crossOriginResourcePolicy() {
        CrossOriginResourcePolicy policy = corsConfiguration.getCrossOriginResourcePolicy();
        if (policy == null) {
            throw new DisabledBeanException("Cross-origin resource policy is not set");
        }
        return _ -> new HttpHeaderTuple(CROSS_ORIGIN_RESOURCE_POLICY, policy);
    }

    /**
     * Creates a populator for the configured Cross-Origin-Embedder-Policy
     * header.
     *
     * @return A populator for the configured policy
     * @throws DisabledBeanException If no embedder policy is configured
     */
    @Named("crossOriginEmbedderPolicy")
    @Singleton
    @Requires(property = "micronaut.server.cors.cross-origin-embedder-policy")
    ResponseHeaderPopulator crossOriginEmbedderPolicy() {
        CrossOriginEmbedderPolicy policy = corsConfiguration.getCrossOriginEmbedderPolicy();
        if (policy == null) {
            throw new DisabledBeanException("Cross-origin embedder policy is not set");
        }
        return _ -> new HttpHeaderTuple(CROSS_ORIGIN_EMBEDDER_POLICY, policy);
    }
}
