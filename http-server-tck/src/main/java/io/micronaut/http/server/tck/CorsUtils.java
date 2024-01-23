/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Utility class to do CORS related assertions.
 * @author Sergio del Amo
 * @since 3.9.0
 */
public final class CorsUtils {
    private CorsUtils() {

    }

    /**
     * @param response HTTP Response to run CORS assertions against it.
     */
    public static void assertCorsHeadersNotPresent(HttpResponse<?> response) {
        assertFalse(response.getHeaders().names().contains(HttpHeaders.VARY));
        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
    }

    /**
     * @param response HTTP Response to run CORS assertions against it.
     * @param origin The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_ORIGIN}.
     * @param method The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_METHODS}.
     */
    public static void assertCorsHeaders(HttpResponse<?> response, String origin, HttpMethod method) {
        CorsAssertion.builder()
            .vary("Origin")
            .allowCredentials()
            .allowOrigin(origin)
            .allowMethods(method)
            .maxAge("1800")
            .build()
            .validate(response);
    }

    /**
     * @param response HTTP Response to run CORS assertions against it.
     * @param origin The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_ORIGIN}.
     * @param method The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_METHODS}.
     * @param allowCredentials The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS}.
     */
    public static void assertCorsHeaders(HttpResponse<?> response, String origin, HttpMethod method, boolean allowCredentials) {
        CorsAssertion.builder()
            .vary("Origin")
            .allowCredentials(allowCredentials)
            .allowOrigin(origin)
            .allowMethods(method)
            .maxAge("1800")
            .build()
            .validate(response);
    }

    /**
     * @param response HTTP Response to run CORS assertions against it.
     * @param origin The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_ORIGIN}.
     * @param method The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_METHODS}.
     * @param allowCredentials The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS}.
     * @param allowPrivateNetwork The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK}.
     */
    public static void assertCorsHeaders(HttpResponse<?> response, String origin, HttpMethod method, boolean allowCredentials, boolean allowPrivateNetwork) {
        CorsAssertion.builder()
                .vary("Origin")
                .allowCredentials(allowCredentials)
                .allowOrigin(origin)
                .allowMethods(method)
                .maxAge("1800")
                .allowPrivateNetwork(allowPrivateNetwork)
                .build()
                .validate(response);
    }

    /**
     * @param response HTTP Response to run CORS assertions against it.
     * @param origin The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_ORIGIN}.
     * @param method The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_METHODS}.
     * @param maxAge The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_MAX_AGE}.
     */
    public static void assertCorsHeaders(HttpResponse<?> response, String origin, HttpMethod method, String maxAge) {
        CorsAssertion.builder()
            .vary("Origin")
            .allowCredentials()
            .allowOrigin(origin)
            .allowMethods(method)
            .maxAge(maxAge)
            .build()
            .validate(response);
    }
}
