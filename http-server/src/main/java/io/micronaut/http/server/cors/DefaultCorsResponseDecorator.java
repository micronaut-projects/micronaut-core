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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.HttpServerConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK;
import static io.micronaut.http.HttpHeaders.ORIGIN;
import static io.micronaut.http.HttpHeaders.VARY;
import static io.micronaut.http.server.cors.CrossOriginUtil.CONVERSION_CONTEXT_HTTP_METHOD;

/**
 * Default {@link CorsResponseDecorator} implementation.
 *
 * <p>Values supplied by a validated preflight request are echoed where required by the CORS
 * protocol. Response headers with multiple values are written either as one comma-separated
 * header or as repeated header fields according to the server CORS configuration.</p>
 *
 * @since 5.2.0
 */
@Singleton
@Internal
class DefaultCorsResponseDecorator implements CorsResponseDecorator {
    private final HttpServerConfiguration.CorsConfiguration corsConfiguration;

    /**
     * Creates the default response decorator.
     *
     * @param corsConfiguration the server CORS configuration used to choose between a single
     * comma-separated header and repeated header fields
     */
    DefaultCorsResponseDecorator(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
        this.corsConfiguration = corsConfiguration;
    }

    @Override
    public void decorateResponseWithHeadersForPreflightRequest(HttpRequest<?> request,
                                                               MutableHttpResponse<?> response,
                                                               CorsOriginConfiguration config) {
        HttpHeaders headers = request.getHeaders();
        headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD)
            .ifPresent(methods -> setAllowMethods(methods, response));
        headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING)
            .ifPresent(val -> setAllowHeaders(val, response));
        headers.getFirst(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, ConversionContext.BOOLEAN)
            .filter(Boolean.TRUE::equals)
            .ifPresent(ignored -> setAllowPrivateNetwork(config, response));
        setMaxAge(config.getMaxAge(), response);
    }

    @Override
    public void decorateResponseWithHeaders(HttpRequest<?> request,
                                             MutableHttpResponse<?> response,
                                             CorsOriginConfiguration config) {
        setOrigin(request.getOrigin().orElse(null), response);
        setVary(response);
        setExposeHeaders(config.getExposedHeaders(), response);
        setAllowCredentials(config, response);
    }

    /**
     * Adds the {@value HttpHeaders#ACCESS_CONTROL_ALLOW_CREDENTIALS} header when credentials are
     * enabled for the resolved CORS configuration.
     *
     * @param config the resolved CORS configuration
     * @param response the response to decorate
     */
    protected void setAllowCredentials(CorsOriginConfiguration config, MutableHttpResponse<?> response) {
        if (config.isAllowCredentials()) {
            response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS, StringUtils.TRUE);
        }
    }

    /**
     * Adds the {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK} header when private-network
     * access is enabled for the resolved CORS configuration.
     *
     * @param config the resolved CORS configuration
     * @param response the response to decorate
     */
    protected void setAllowPrivateNetwork(CorsOriginConfiguration config, MutableHttpResponse<?> response) {
        if (config.isAllowPrivateNetwork()) {
            response.header(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, StringUtils.TRUE);
        }
    }

    /**
     * Adds the configured exposed header names. Depending on the server configuration, the names
     * are written as either one comma-separated header or multiple header values.
     *
     * @param exposedHeaders the header names exposed to the client
     * @param response the response to decorate
     */
    protected void setExposeHeaders(List<String> exposedHeaders, MutableHttpResponse<?> response) {
        if (corsConfiguration.isSingleHeader()) {
            String headerValue = String.join(",", exposedHeaders);
            if (StringUtils.isNotEmpty(headerValue)) {
                response.header(ACCESS_CONTROL_EXPOSE_HEADERS, headerValue);
            }
        } else {
            exposedHeaders.forEach(header -> response.header(ACCESS_CONTROL_EXPOSE_HEADERS, header));
        }
    }

    /**
     * Adds {@value HttpHeaders#ORIGIN} to the {@value HttpHeaders#VARY} response header.
     *
     * @param response the response to decorate
     */
    protected void setVary(MutableHttpResponse<?> response) {
        response.header(VARY, ORIGIN);
    }

    /**
     * Adds the request origin as the allowed origin when an origin is present.
     *
     * @param origin the request origin, or {@code null} if absent
     * @param response the response to decorate
     */
    protected void setOrigin(@Nullable String origin, MutableHttpResponse<?> response) {
        if (origin != null) {
            response.header(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
    }

    /**
     * Adds the requested method as the allowed method.
     *
     * @param method the method requested by the preflight request
     * @param response the response to decorate
     */
    protected void setAllowMethods(HttpMethod method, MutableHttpResponse<?> response) {
        response.header(ACCESS_CONTROL_ALLOW_METHODS, method);
    }

    /**
     * Adds the requested header names as allowed headers. Depending on the server configuration,
     * the names are written as either one comma-separated header or multiple header values.
     *
     * @param optionalAllowHeaders the header names requested by the preflight request
     * @param response the response to decorate
     */
    protected void setAllowHeaders(List<?> optionalAllowHeaders, MutableHttpResponse<?> response) {
        List<String> allowHeaders = optionalAllowHeaders.stream().map(Object::toString).toList();
        if (corsConfiguration.isSingleHeader()) {
            String headerValue = String.join(",", allowHeaders);
            if (StringUtils.isNotEmpty(headerValue)) {
                response.header(ACCESS_CONTROL_ALLOW_HEADERS, headerValue);
            }
        } else {
            allowHeaders
                .stream()
                .map(StringUtils::trimLeadingWhitespace)
                .forEach(header -> response.header(ACCESS_CONTROL_ALLOW_HEADERS, header));
        }
    }

    /**
     * Adds the preflight cache duration when it is non-negative.
     *
     * @param maxAge the maximum cache duration in seconds, or a negative value to omit the header
     * @param response the response to decorate
     */
    protected void setMaxAge(long maxAge, MutableHttpResponse<?> response) {
        if (maxAge > -1) {
            response.header(ACCESS_CONTROL_MAX_AGE, Long.toString(maxAge));
        }
    }
}
