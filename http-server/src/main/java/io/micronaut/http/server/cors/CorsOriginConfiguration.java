/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.http.HttpMethod;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Stores configuration for CORS.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
public class CorsOriginConfiguration {

    /**
     * Constant to represent any value.
     */
    public static final List<String> ANY = Collections.singletonList("*");

    /**
     * Constant to represent any method.
     */
    public static final List<HttpMethod> ANY_METHOD = Collections.emptyList();

    private List<String> allowedOrigins = ANY;
    private List<HttpMethod> allowedMethods = ANY_METHOD;
    private List<String> allowedHeaders = ANY;
    private List<String> exposedHeaders = Collections.emptyList();
    private boolean allowCredentials = true;
    private Long maxAge = 1800L;

    /**
     * @return The allowed origins
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the allowed origins.
     *
     * @param allowedOrigins The allow origins
     */
    public void setAllowedOrigins(@Nullable List<String> allowedOrigins) {
        if (allowedOrigins != null) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /**
     * @return The allowed methods
     */
    public List<HttpMethod> getAllowedMethods() {
        return allowedMethods;
    }

    /**
     * Sets the allowed methods.
     *
     * @param allowedMethods The allowed methods
     */
    public void setAllowedMethods(@Nullable List<HttpMethod> allowedMethods) {
        if (allowedMethods != null) {
            this.allowedMethods = allowedMethods;
        }
    }

    /**
     * @return The allowed headers
     */
    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    /**
     * Sets the allowed headers.
     *
     * @param allowedHeaders The allowed headers
     */
    public void setAllowedHeaders(@Nullable List<String> allowedHeaders) {
        if (allowedHeaders != null) {
            this.allowedHeaders = allowedHeaders;
        }
    }

    /**
     * @return The exposed headers
     */
    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    /**
     * Sets the exposed headers.
     *
     * @param exposedHeaders The exposed headers
     */
    public void setExposedHeaders(@Nullable List<String> exposedHeaders) {
        if (exposedHeaders != null) {
            this.exposedHeaders = exposedHeaders;
        }
    }

    /**
     * @return Whether to allow credentials
     */
    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    /**
     * Sets whether to allow credentials.
     *
     * @param allowCredentials The credentials
     */
    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    /**
     * @return The max age. A value of -1 indicates no max age
     */
    public Long getMaxAge() {
        return maxAge;
    }

    /**
     * Sets the max age.
     *
     * @param maxAge The max age
     */
    public void setMaxAge(@Nullable Long maxAge) {
        if (maxAge == null) {
            this.maxAge = -1L;
        } else {
            this.maxAge = maxAge;
        }
    }
}
