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

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.micronaut.http.HttpHeaders.ORIGIN;
import static io.micronaut.http.HttpHeaders.VARY;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
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
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Responsible for handling CORS requests and responses.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter("/**")
public class CorsFilter implements HttpServerFilter {

    private static final ArgumentConversionContext<HttpMethod> CONVERSION_CONTEXT_HTTP_METHOD = ConversionContext.of(HttpMethod.class);

    protected final HttpServerConfiguration.CorsConfiguration corsConfiguration;

    /**
     * @param corsConfiguration The {@link CorsOriginConfiguration} instance
     */
    public CorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration) {
        this.corsConfiguration = corsConfiguration;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean originHeaderPresent = request.getHeaders().getOrigin().isPresent();
        if (originHeaderPresent) {
            MutableHttpResponse<?> response = handleRequest(request).orElse(null);
            if (response != null) {
                return Publishers.just(response);
            } else {
                return Publishers.map(chain.proceed(request), mutableHttpResponse -> {
                    handleResponse(request, mutableHttpResponse);
                    return mutableHttpResponse;
                });
            }
        } else {
            return chain.proceed(request);
        }
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
     */
    protected void handleResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        HttpHeaders headers = request.getHeaders();
        Optional<String> originHeader = headers.getOrigin();
        originHeader.ifPresent(requestOrigin -> {

            Optional<CorsOriginConfiguration> optionalConfig = getConfiguration(requestOrigin);

            if (optionalConfig.isPresent()) {
                CorsOriginConfiguration config = optionalConfig.get();

                if (CorsUtil.isPreflightRequest(request)) {
                    Optional<HttpMethod> result = headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD);
                    setAllowMethods(result.get(), response);
                    Optional<List<String>> allowedHeaders = headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING);
                    allowedHeaders.ifPresent(val ->
                        setAllowHeaders(val, response)
                    );

                    setMaxAge(config.getMaxAge(), response);
                }

                setOrigin(requestOrigin, response);
                setVary(response);
                setExposeHeaders(config.getExposedHeaders(), response);
                setAllowCredentials(config, response);
            }
        });
    }

    /**
     * Handles a CORS {@link HttpRequest}.
     *
     * @param request The {@link HttpRequest} object
     * @return An optional {@link MutableHttpResponse}. The request should proceed normally if empty
     */
    protected Optional<MutableHttpResponse<?>> handleRequest(HttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        Optional<String> originHeader = headers.getOrigin();
        if (originHeader.isPresent()) {

            String requestOrigin = originHeader.get();
            boolean preflight = CorsUtil.isPreflightRequest(request);

            Optional<CorsOriginConfiguration> optionalConfig = getConfiguration(requestOrigin);

            if (optionalConfig.isPresent()) {
                CorsOriginConfiguration config = optionalConfig.get();

                HttpMethod requestMethod = request.getMethod();

                List<HttpMethod> allowedMethods = config.getAllowedMethods();

                if (!isAnyMethod(allowedMethods)) {
                    HttpMethod methodToMatch = preflight ? headers.getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD).orElse(requestMethod) : requestMethod;
                    if (allowedMethods.stream().noneMatch(method -> method.equals(methodToMatch))) {
                        return Optional.of(HttpResponse.status(HttpStatus.FORBIDDEN));
                    }
                }

                if (preflight) {
                    Optional<List<String>> accessControlHeaders = headers.get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING);

                    List<String> allowedHeaders = config.getAllowedHeaders();

                    if (!isAny(allowedHeaders) && accessControlHeaders.isPresent()) {
                        if (!accessControlHeaders.get().stream()
                            .allMatch(header -> allowedHeaders.stream()
                                .anyMatch(allowedHeader -> allowedHeader.equalsIgnoreCase(header.trim())))) {
                            return Optional.of(HttpResponse.status(HttpStatus.FORBIDDEN));
                        }
                    }

                    MutableHttpResponse<Object> ok = HttpResponse.ok();
                    handleResponse(request, ok);
                    return Optional.of(ok);
                }
            }
        }

        return Optional.empty();
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
    protected void setOrigin(String origin, MutableHttpResponse response) {
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    }

    /**
     * @param method   The {@link HttpMethod} object
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setAllowMethods(HttpMethod method, MutableHttpResponse response) {
        response.header(ACCESS_CONTROL_ALLOW_METHODS, method);
    }

    /**
     * @param optionalAllowHeaders A list with optional allow headers
     * @param response             The {@link MutableHttpResponse} object
     */
    protected void setAllowHeaders(List<?> optionalAllowHeaders, MutableHttpResponse response) {
        List<String> allowHeaders = optionalAllowHeaders.stream().map(Object::toString).collect(Collectors.toList());
        if (corsConfiguration.isSingleHeader()) {
            String headerValue = String.join(",", allowHeaders);
            if (StringUtils.isNotEmpty(headerValue)) {
                response.header(ACCESS_CONTROL_ALLOW_HEADERS, headerValue);
            }
        } else {
            allowHeaders.forEach(header -> response.header(ACCESS_CONTROL_ALLOW_HEADERS, header));
        }
    }

    /**
     * @param maxAge   The max age
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setMaxAge(long maxAge, MutableHttpResponse response) {
        if (maxAge > -1) {
            response.header(ACCESS_CONTROL_MAX_AGE, Long.toString(maxAge));
        }
    }

    private Optional<CorsOriginConfiguration> getConfiguration(String requestOrigin) {
        Map<String, CorsOriginConfiguration> corsConfigurations = corsConfiguration.getConfigurations();
        for (Map.Entry<String, CorsOriginConfiguration> config : corsConfigurations.entrySet()) {
            List<String> allowedOrigins = config.getValue().getAllowedOrigins();
            if (!allowedOrigins.isEmpty()) {
                boolean matches = false;
                if (isAny(allowedOrigins)) {
                    matches = true;
                }
                if (!matches) {
                    matches = allowedOrigins.stream().anyMatch(origin -> {
                        if (origin.equals(requestOrigin)) {
                            return true;
                        }
                        Pattern p = Pattern.compile(origin);
                        Matcher m = p.matcher(requestOrigin);
                        return m.matches();
                    });
                }

                if (matches) {
                    return Optional.of(config.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isAny(List<String> values) {
        return values == CorsOriginConfiguration.ANY;
    }

    private boolean isAnyMethod(List<HttpMethod> allowedMethods) {
        return allowedMethods == CorsOriginConfiguration.ANY_METHOD;
    }
}
