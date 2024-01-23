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

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS assertion.
 * @author Sergio del Amo
 * @since 3.9.0
 */
public final class CorsAssertion {

    private final String vary;
    private final String accessControlAllowCredentials;

    private final String origin;

    private final List<HttpMethod> allowMethods;

    private final String maxAge;
    private final String allowPrivateNetwork;

    private CorsAssertion(String vary,
                          String accessControlAllowCredentials,
                          String origin,
                          List<HttpMethod> allowMethods,
                          String maxAge, String allowPrivateNetwork) {
        this.vary = vary;
        this.accessControlAllowCredentials = accessControlAllowCredentials;
        this.origin = origin;
        this.allowMethods = allowMethods;
        this.maxAge = maxAge;
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    /**
     * Validate the CORS assertions.
     * @param response HTTP Response to run CORS assertions against it.
     */
    public void validate(HttpResponse<?> response) {
        if (StringUtils.isNotEmpty(vary)) {
            assertEquals(vary, response.getHeaders().get(HttpHeaders.VARY));
        }
        if (StringUtils.isNotEmpty(accessControlAllowCredentials)) {
            assertEquals(accessControlAllowCredentials, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }
        if (StringUtils.isNotEmpty(origin)) {
            assertEquals(origin, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
        if (CollectionUtils.isNotEmpty(allowMethods)) {
            assertEquals(allowMethods.stream().map(HttpMethod::toString).collect(Collectors.joining(",")),
                response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        }
        if (StringUtils.isNotEmpty(maxAge)) {
            assertEquals(maxAge, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
        }

        if (StringUtils.isNotEmpty(allowPrivateNetwork)) {
            assertEquals(allowPrivateNetwork, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK));
        }
    }

    /**
     *
     * @return a CORS Assertion Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * CORS Assertion Builder.
     */
    public static class Builder {
        private String vary;
        private String accessControlAllowCredentials;

        private String origin;

        private List<HttpMethod> allowMethods;

        private String maxAge;

        private String allowPrivateNetwork;

        /**
         *
         * @param varyValue The expected value for the HTTP Header {@value HttpHeaders#VARY}.
         * @return The Builder
         */
        public Builder vary(String varyValue) {
            this.vary = varyValue;
            return this;
        }

        /**
         *
         * @param accessControlAllowCredentials The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS}.
         * @return The Builder
         */
        public Builder allowCredentials(String accessControlAllowCredentials) {
            this.accessControlAllowCredentials = accessControlAllowCredentials;
            return this;
        }

        /**
         *
         * Set expectation of value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS} to {@value StringUtils#TRUE}.
         * @return The Builder
         */
        public Builder allowCredentials() {
            return allowCredentials(StringUtils.TRUE);
        }

        /**
         *
         * Set expectation of value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS} to {@value StringUtils#TRUE}.
         * @param allowCredentials Set expectation of value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS}
         * @return The Builder
         */
        public Builder allowCredentials(boolean allowCredentials) {
            return allowCredentials ? allowCredentials(StringUtils.TRUE) : allowCredentials("");
        }

        /**
         *
         * @param origin The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_ORIGIN}.
         * @return The Builder
         */
        public Builder allowOrigin(String origin) {
            this.origin = origin;
            return this;
        }

        /**
         *
         * @param method The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_METHODS}.
         * @return The Builder
         */
        public Builder allowMethods(HttpMethod method) {
            if (allowMethods == null) {
                this.allowMethods = new ArrayList<>();
            }
            this.allowMethods.add(method);
            return this;
        }

        /**
         *
         * @param allowPrivateNetwork The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK}.
         * @return The Builder
         */
        public Builder allowPrivateNetwork(String allowPrivateNetwork) {
            this.allowPrivateNetwork = accessControlAllowCredentials;
            return this;
        }

        /**
         *
         * Set expectation of value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK} to {@value StringUtils#TRUE}.
         * @return The Builder
         */
        public Builder allowPrivateNetwork() {
            return allowPrivateNetwork(StringUtils.TRUE);
        }

        /**
         *
         * @param allowPrivateNetwork The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK}.
         * @return The Builder
         */
        public Builder allowPrivateNetwork(boolean allowPrivateNetwork) {
            return allowPrivateNetwork ? allowPrivateNetwork(StringUtils.TRUE) : allowPrivateNetwork("");
        }

        /**
         *
         * @param maxAge The expected value for the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_MAX_AGE}.
         * @return The Builder
         */
        public Builder maxAge(String maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         *
         * @return A CORS assertion.
         */
        public CorsAssertion build() {
            return new CorsAssertion(vary, accessControlAllowCredentials, origin, allowMethods, maxAge, allowPrivateNetwork);
        }
    }
}
