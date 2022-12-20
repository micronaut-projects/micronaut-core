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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ImmutableArgumentConversionContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.HttpServerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpAttributes.AVAILABLE_HTTP_METHODS;
import static io.micronaut.http.HttpHeaders.*;
import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

/**
 * Responsible for handling CORS requests and responses.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter(MATCH_ALL_PATTERN)
public class CorsFilter implements HttpServerFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CorsFilter.class);
    private static final ArgumentConversionContext<HttpMethod> CONVERSION_CONTEXT_HTTP_METHOD = ImmutableArgumentConversionContext.of(HttpMethod.class);

    protected final HttpServerConfiguration.CorsConfiguration corsConfiguration;

    /**
     * @param corsConfiguration The {@link CorsOriginConfiguration} instance
     */
    public CorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
        this.corsConfiguration = corsConfiguration;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String origin = request.getHeaders().getOrigin().orElse(null);
        if (origin == null) {
            LOG.trace("Http Header " + HttpHeaders.ORIGIN + " not present. Proceeding with the request.");
            return chain.proceed(request);
        }
        CorsOriginConfiguration corsOriginConfiguration = getConfiguration(origin).orElse(null);
        if (corsOriginConfiguration != null) {
            if (CorsUtil.isPreflightRequest(request)) {
                return handlePreflightRequest(request, chain, corsOriginConfiguration);
            }
            if (!validateMethodToMatch(request, corsOriginConfiguration).isPresent()) {
                return forbidden();
            }
            return Publishers.then(chain.proceed(request), resp -> decorateResponseWithHeaders(request, resp, corsOriginConfiguration));
        }
        LOG.trace("CORS configuration not found for {} origin", origin);
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.METRICS.after();
    }

    /**
     * Handles a CORS response.
     *
     * @param request  The {@link HttpRequest} object
     * @param response The {@link MutableHttpResponse} object
     * @deprecated not used
     */
    @Deprecated
    protected void handleResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
    }

    /**
     * Handles a CORS {@link HttpRequest}.
     *
     * @param request The {@link HttpRequest} object
     * @return An optional {@link MutableHttpResponse}. The request should proceed normally if empty
     * @deprecated Not used any more.
     */
    @Deprecated
    protected Optional<MutableHttpResponse<?>> handleRequest(HttpRequest request) {
        return Optional.empty();
    }

    @NonNull
    private Optional<HttpMethod> validateMethodToMatch(@NonNull HttpRequest<?> request,
                                                       @NonNull CorsOriginConfiguration config) {
        HttpMethod methodToMatch = methodToMatch(request);
        if (!methodAllowed(config, methodToMatch)) {
            return Optional.empty();
        }
        return Optional.of(methodToMatch);
    }

    /**
     * @param config   The {@link CorsOriginConfiguration} instance
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setAllowCredentials(CorsOriginConfiguration config, MutableHttpResponse<?> response) {
        if (config.isAllowCredentials()) {
            response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS, Boolean.toString(true));
        }
    }

    /**
     * @param exposedHeaders A list of the exposed headers
     * @param response       The {@link MutableHttpResponse} object
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
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setVary(MutableHttpResponse<?> response) {
        response.header(VARY, ORIGIN);
    }

    /**
     * @param origin   The origin
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setOrigin(@Nullable String origin, @NonNull MutableHttpResponse<?> response) {
        if (origin != null) {
            response.header(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
    }

    /**
     * @param method   The {@link HttpMethod} object
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setAllowMethods(HttpMethod method, MutableHttpResponse<?> response) {
        response.header(ACCESS_CONTROL_ALLOW_METHODS, method);
    }

    /**
     * @param optionalAllowHeaders A list with optional allow headers
     * @param response             The {@link MutableHttpResponse} object
     */
    protected void setAllowHeaders(List<?> optionalAllowHeaders, MutableHttpResponse<?> response) {
        List<String> allowHeaders = optionalAllowHeaders.stream().map(Object::toString).collect(Collectors.toList());
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
     * @param maxAge   The max age
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setMaxAge(long maxAge, MutableHttpResponse<?> response) {
        if (maxAge > -1) {
            response.header(ACCESS_CONTROL_MAX_AGE, Long.toString(maxAge));
        }
    }

    @NonNull
    private Optional<CorsOriginConfiguration> getConfiguration(@NonNull String requestOrigin) {
        return corsConfiguration.getConfigurations().values().stream()
            .filter(config -> {
                List<String> allowedOrigins = config.getAllowedOrigins();
                return !allowedOrigins.isEmpty() && (isAny(allowedOrigins) || allowedOrigins.stream().anyMatch(origin -> matchesOrigin(origin, requestOrigin)));
            }).findFirst();
    }

    private boolean matchesOrigin(@NonNull String origin, @NonNull String requestOrigin) {
        if (origin.equals(requestOrigin)) {
            return true;
        }
        Pattern p = Pattern.compile(origin);
        Matcher m = p.matcher(requestOrigin);
        return m.matches();
    }

    private boolean isAny(List<String> values) {
        return values == CorsOriginConfiguration.ANY;
    }

    private boolean isAnyMethod(List<HttpMethod> allowedMethods) {
        return allowedMethods == CorsOriginConfiguration.ANY_METHOD;
    }

    private boolean methodAllowed(@NonNull CorsOriginConfiguration config,
                                  @NonNull HttpMethod methodToMatch) {
        List<HttpMethod> allowedMethods = config.getAllowedMethods();
        return isAnyMethod(allowedMethods) || allowedMethods.stream().anyMatch(method -> method.equals(methodToMatch));
    }

    @NonNull
    private HttpMethod methodToMatch(@NonNull HttpRequest<?> request) {
        HttpMethod requestMethod = request.getMethod();
        return CorsUtil.isPreflightRequest(request) ? request.getHeaders().getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD).orElse(requestMethod) : requestMethod;
    }

    private boolean hasAllowedHeaders(@NonNull HttpRequest<?> request, @NonNull CorsOriginConfiguration config) {
        Optional<List<String>> accessControlHeaders = request.getHeaders().get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING);
        List<String> allowedHeaders = config.getAllowedHeaders();
        return isAny(allowedHeaders) || (
            accessControlHeaders.isPresent() &&
                accessControlHeaders.get().stream().allMatch(header -> allowedHeaders.stream().anyMatch(allowedHeader -> allowedHeader.equalsIgnoreCase(header.trim())))
        );
    }

    @NotNull
    private static Publisher<MutableHttpResponse<?>> forbidden() {
        return Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN));
    }

    @NonNull
    private void decorateResponseWithHeadersForPreflightRequest(@NonNull HttpRequest<?> request,
                                                                @NonNull MutableHttpResponse<?> response,
                                                                @NonNull CorsOriginConfiguration config) {
        HttpHeaders headers = request.getHeaders();
        headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD)
            .ifPresent(methods -> setAllowMethods(methods, response));
        headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING)
            .ifPresent(val -> setAllowHeaders(val, response));
        setMaxAge(config.getMaxAge(), response);
    }

    @NonNull
    private void decorateResponseWithHeaders(@NonNull HttpRequest<?> request,
                                             @NonNull MutableHttpResponse<?> response,
                                             @NonNull CorsOriginConfiguration config) {
        HttpHeaders headers = request.getHeaders();
        setOrigin(headers.getOrigin().orElse(null), response);
        setVary(response);
        setExposeHeaders(config.getExposedHeaders(), response);
        setAllowCredentials(config, response);
    }

    @NonNull
    private Publisher<MutableHttpResponse<?>> handlePreflightRequest(@NonNull HttpRequest<?> request,
                                                                     @NonNull ServerFilterChain chain,
                                                                     @NonNull CorsOriginConfiguration corsOriginConfiguration) {
        Optional<HttpStatus> statusOptional = validatePreflightRequest(request, corsOriginConfiguration);
        if (statusOptional.isPresent()) {
            HttpStatus status = statusOptional.get();
            if (status.getCode() >= 400) {
                return Publishers.just(HttpResponse.status(status));
            }
            MutableHttpResponse<?> resp = HttpResponse.status(status);
            decorateResponseWithHeadersForPreflightRequest(request, resp, corsOriginConfiguration);
            decorateResponseWithHeaders(request, resp, corsOriginConfiguration);
            return Publishers.just(resp);
        }
        return Publishers.then(chain.proceed(request), resp -> {
            decorateResponseWithHeadersForPreflightRequest(request, resp, corsOriginConfiguration);
            decorateResponseWithHeaders(request, resp, corsOriginConfiguration);
        });
    }

    @NonNull
    private Optional<HttpStatus> validatePreflightRequest(@NonNull HttpRequest<?> request,
                                                          @NonNull CorsOriginConfiguration config) {
        Optional<HttpMethod> methodToMatchOptional = validateMethodToMatch(request, config);
        if (!methodToMatchOptional.isPresent()) {
            return Optional.of(HttpStatus.FORBIDDEN);
        }
        HttpMethod methodToMatch = methodToMatchOptional.get();

        Optional<? extends ArrayList<HttpMethod>> availableHttpMethods = (Optional<? extends ArrayList<HttpMethod>>) request.getAttribute(AVAILABLE_HTTP_METHODS, new ArrayList<HttpMethod>().getClass());
        if (CorsUtil.isPreflightRequest(request) &&
            availableHttpMethods.isPresent() &&
            availableHttpMethods.get().stream().anyMatch(method -> method.equals(methodToMatch))) {
            if (!hasAllowedHeaders(request, config)) {
                return Optional.of(HttpStatus.FORBIDDEN);
            }
            return Optional.of(HttpStatus.OK);
        }
        return Optional.empty();
    }
}
