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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CorsFilterTest {

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
}
