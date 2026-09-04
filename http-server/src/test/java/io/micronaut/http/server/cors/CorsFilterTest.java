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

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.HttpServerConfiguration;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorsFilterTest {

    private static final String REQUEST_ORIGIN = "https://example.com";
    private static final String FIXED_ORIGIN = "https://fixed.example.com";

    @Test
    void populatesConfiguredCrossOriginPoliciesWithoutOverwritingExistingHeaders() {
        HttpServerConfiguration.CorsConfiguration corsConfiguration = new HttpServerConfiguration.CorsConfiguration();
        corsConfiguration.setCrossOriginEmbedderPolicy(CrossOriginEmbedderPolicy.REQUIRE_CORP);
        corsConfiguration.setCrossOriginResourcePolicy(CrossOriginResourcePolicy.SAME_SITE);

        CorsFilter filter = new CorsFilter(corsConfiguration, null, null, null);
        HttpRequest<?> request = HttpRequest.GET("/").header(HttpHeaders.ORIGIN, "https://example.com");

        MutableHttpResponse<?> response = HttpResponse.ok();
        filter.filterResponse(request, response);

        assertEquals("require-corp", response.getHeaders().get(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY));
        assertEquals("same-site", response.getHeaders().get(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY));
        assertFalse(response.getHeaders().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        MutableHttpResponse<?> responseWithPolicies = HttpResponse.ok()
            .header(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY, "unsafe-none")
            .header(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY, "same-origin");
        filter.filterResponse(request, responseWithPolicies);

        assertEquals("unsafe-none", responseWithPolicies.getHeaders().get(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY));
        assertEquals("same-origin", responseWithPolicies.getHeaders().get(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY));
    }

    @Test
    void invokesDeprecatedOriginOverrideForCorsResponses() {
        CorsFilter filter = new CorsFilterWithFixedOrigin(corsConfiguration());
        HttpRequest<?> request = HttpRequest.GET("/").header(HttpHeaders.ORIGIN, REQUEST_ORIGIN);

        MutableHttpResponse<?> response = HttpResponse.ok();
        filter.filterResponse(request, response);

        assertEquals(FIXED_ORIGIN, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowsDeprecatedOriginOverrideToCallSuper() {
        CorsFilter filter = new CorsFilterWithDefaultOrigin(corsConfiguration());
        HttpRequest<?> request = HttpRequest.GET("/").header(HttpHeaders.ORIGIN, REQUEST_ORIGIN);

        MutableHttpResponse<?> response = HttpResponse.ok();
        filter.filterResponse(request, response);

        assertEquals(REQUEST_ORIGIN, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void invokesDeprecatedPreflightMutators() {
        HttpServerConfiguration.CorsConfiguration corsConfiguration = corsConfiguration();
        CorsOriginConfiguration originConfiguration = corsConfiguration.getConfigurations().get("default");
        originConfiguration.setAllowCredentials(true);
        originConfiguration.setAllowPrivateNetwork(true);
        originConfiguration.setExposedHeaders(List.of("X-Exposed"));
        originConfiguration.setMaxAge(600L);

        CorsFilter filter = new CorsFilter(corsConfiguration, null, null, null);
        HttpRequest<?> request = HttpRequest.OPTIONS("/")
            .header(HttpHeaders.ORIGIN, REQUEST_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, "true");

        MutableHttpResponse<?> response = HttpResponse.ok();
        filter.filterResponse(request, response);

        assertEquals("POST", response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        assertEquals("X-Requested", response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
        assertEquals("true", response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK));
        assertEquals("600", response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
        assertEquals(REQUEST_ORIGIN, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("X-Exposed", response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        assertEquals("true", response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("Origin", response.getHeaders().get(HttpHeaders.VARY));
    }

    @Test
    void invokesDeprecatedOriginOverrideForShortCircuitedPreflightResponse() {
        HttpServerConfiguration.CorsConfiguration corsConfiguration = corsConfiguration();
        CorsOriginConfiguration originConfiguration = corsConfiguration.getConfigurations().get("default");
        CorsFilter filter = new CorsFilter(
            corsConfiguration,
            null,
            null,
            new DefaultCorsResponseDecorator(corsConfiguration),
            (origin, routeMatches) -> originConfiguration,
            (requestToValidate, config) -> true
        ) {
            @Override
            protected void setOrigin(@Nullable String origin, MutableHttpResponse<?> response) {
                response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FIXED_ORIGIN);
            }
        };
        HttpRequest<?> request = HttpRequest.OPTIONS("/")
            .header(HttpHeaders.ORIGIN, REQUEST_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");

        HttpResponse<?> response = filter.filterPreFlightRequest(request);
        assertNotNull(response);

        assertEquals(FIXED_ORIGIN, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private static HttpServerConfiguration.CorsConfiguration corsConfiguration() {
        HttpServerConfiguration.CorsConfiguration corsConfiguration = new HttpServerConfiguration.CorsConfiguration();
        corsConfiguration.setEnabled(true);
        corsConfiguration.setConfigurations(Map.of("default", new CorsOriginConfiguration()));
        return corsConfiguration;
    }

    private static final class CorsFilterWithFixedOrigin extends CorsFilter {
        private CorsFilterWithFixedOrigin(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
            super(corsConfiguration, null, null, null);
        }

        @Override
        protected void setOrigin(@Nullable String origin, MutableHttpResponse<?> response) {
            response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FIXED_ORIGIN);
        }
    }

    private static final class CorsFilterWithDefaultOrigin extends CorsFilter {
        private CorsFilterWithDefaultOrigin(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
            super(corsConfiguration, null, null, null);
        }

        @Override
        protected void setOrigin(@Nullable String origin, MutableHttpResponse<?> response) {
            super.setOrigin(origin, response);
        }
    }
}
