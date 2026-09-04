/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ConditionalFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.resource.StaticResourceResolver;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK;
import static io.micronaut.http.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static io.micronaut.http.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;
import static io.micronaut.http.HttpHeaders.ORIGIN;
import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;
import static io.micronaut.http.server.cors.CrossOriginUtil.CONVERSION_CONTEXT_HTTP_METHOD;
import static io.micronaut.http.server.cors.CrossOriginUtil.isAny;
import static io.micronaut.http.server.cors.CrossOriginUtil.validateMethodToMatch;

/**
 * Responsible for handling CORS requests and responses.
 *
 * <p>The filter is enabled by default. Set {@code micronaut.server.cors.filter.enabled}
 * to {@code false} to disable all CORS request validation and response decoration, including
 * handling configured through {@link CrossOrigin}.</p>
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(property = HttpServerConfiguration.PREFIX + ".cors.filter.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@ServerFilter(MATCH_ALL_PATTERN)
public class CorsFilter implements Ordered, ConditionalFilter {
    public static final int CORS_FILTER_ORDER = ServerFilterPhase.METRICS.after();
    private static final Logger LOG = LoggerFactory.getLogger(CorsFilter.class);
    protected final HttpServerConfiguration.CorsConfiguration corsConfiguration;

    private final @Nullable HttpHostResolver httpHostResolver;
    private final Router router;
    private final CorsOriginConfigurationRetriever corsOriginConfigurationRetriever;
    private final PreflightRequestValidator preflightRequestValidator;
    private final CorsResponseDecorator corsResponseDecorator;

    /**
     * Creates the server filter that validates CORS requests and decorates CORS responses.
     *
     * @param corsConfiguration                the server-wide CORS configuration
     * @param router                           the router used to find routes targeted by preflight requests
     * @param httpHostResolver                 the host resolver used by drive-by localhost protection, or
     *                                         {@code null} when host resolution is unavailable
     * @param corsResponseDecorator            the component that adds CORS headers to responses
     * @param corsOriginConfigurationRetriever the component that resolves the applicable
     *                                         CORS configuration for each request
     * @param preflightRequestValidator        the component that validates preflight requests against
     *                                         the resolved CORS configuration and available routes
     */
    @Inject
    public CorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration,
                      Router router,
                      @Nullable HttpHostResolver httpHostResolver,
                      CorsResponseDecorator corsResponseDecorator,
                      CorsOriginConfigurationRetriever corsOriginConfigurationRetriever,
                      PreflightRequestValidator preflightRequestValidator) {
        this.httpHostResolver = httpHostResolver;
        this.corsConfiguration = corsConfiguration;
        this.router = router;
        this.corsResponseDecorator = corsResponseDecorator;
        this.corsOriginConfigurationRetriever = corsOriginConfigurationRetriever;
        this.preflightRequestValidator = preflightRequestValidator;
    }

    /**
     * @param corsConfiguration      The {@link CorsOriginConfiguration} instance
     * @param staticResourceResolver Static Resource Resolver
     * @param router                 Router
     * @param httpHostResolver       HTTP Host resolver
     * @deprecated Use the primary constructor that accepts the CORS strategy components instead.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    public CorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration,
                      @Nullable StaticResourceResolver staticResourceResolver,
                      Router router,
                      @Nullable HttpHostResolver httpHostResolver) {
        this(corsConfiguration,
            router,
            httpHostResolver,
            new DefaultCorsResponseDecorator(corsConfiguration),
            new DefaultCorsOriginConfigurationRetriever(corsConfiguration),
            new DefaultPreflightRequestValidator(staticResourceResolver, router)
        );
    }

    @Override
    public boolean isEnabled(HttpRequest<?> request) {
        if (request.getOrigin().isPresent()) {
            return true;
        }
        if (corsConfiguration.getCrossOriginEmbedderPolicy() != null) {
            return true;
        }
        if (corsConfiguration.getCrossOriginResourcePolicy() != null) {
            return true;
        }
        LOG.trace("Http Header {} not present and micronaut.server.cors.cross-origin-embedder-policy and micronaut.server.cors.cross-origin-resource-policy not set. Proceeding with the request.", ORIGIN);
        return false;
    }

    @PreMatching
    @RequestFilter
    @Nullable
    @Internal
    public final HttpResponse<?> filterPreFlightRequest(HttpRequest<?> request) {
        if (isEnabled(request) && CorsUtil.isPreflightRequest(request)) {
            CorsOriginConfiguration corsOriginConfiguration = getAnyConfiguration(request).orElse(null);
            if (corsOriginConfiguration != null) {
                return handlePreflightRequest(request, corsOriginConfiguration);
            }
        }
        return null; // proceed
    }

    @RequestFilter
    @Nullable
    @Internal
    public final HttpResponse<?> filterRequest(HttpRequest<?> request) {
        String origin = request.getOrigin().orElse(null);
        if (origin == null) {
            LOG.trace("Http Header {} not present. Proceeding with the request.", ORIGIN);
            return null; // proceed
        }
        CorsOriginConfiguration corsOriginConfiguration = corsOriginConfigurationRetriever.findCorsOriginConfiguration(request);
        if (corsOriginConfiguration != null) {
            // These validation might be configured on the actual route
            if (validateMethodToMatch(request, corsOriginConfiguration).isEmpty()) {
                return forbidden();
            }
            if (shouldDenyToPreventDriveByLocalhostAttack(corsOriginConfiguration, request)) {
                LOG.trace("The resolved configuration allows any origin. To prevent drive-by-localhost attacks the request is forbidden");
                return forbidden();
            }
            return null; // proceed
        } else if (shouldDenyToPreventDriveByLocalhostAttack(origin, request)) {
            LOG.trace("The request specifies an origin different than localhost. To prevent drive-by-localhost attacks the request is forbidden");
            return forbidden();
        }
        LOG.trace("CORS configuration not found for {} origin", origin);
        return null; // proceed
    }

    @ResponseFilter
    @Internal
    public final void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        CorsOriginConfiguration corsOriginConfiguration = corsOriginConfigurationRetriever.findCorsOriginConfiguration(request);
        if (corsOriginConfiguration != null) {
            if (CorsUtil.isPreflightRequest(request)) {
                decorateResponseWithHeadersForPreflightRequest(request, response, corsOriginConfiguration);
            }
            decorateResponseWithHeaders(request, response, corsOriginConfiguration);
        }
        setCrossOriginEmbedderPolicy(corsConfiguration, response);
        setCrossOriginResourcePolicy(corsConfiguration, response);
    }

    @Override
    public int getOrder() {
        return CORS_FILTER_ORDER;
    }

    private void decorateResponseWithHeadersForPreflightRequest(HttpRequest<?> request,
                                                                MutableHttpResponse<?> response,
                                                                CorsOriginConfiguration config) {
        // Keep the old hooks as the dispatch path for the default decorator so existing
        // CorsFilter subclasses can still customize CORS response headers.
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator) {
            request.getHeaders().getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD)
                .ifPresent(method -> setAllowMethods(method, response));
            request.getHeaders().get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING)
                .ifPresent(headers -> setAllowHeaders(headers, response));
            request.getHeaders().getFirst(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, ConversionContext.BOOLEAN)
                .filter(Boolean.TRUE::equals)
                .ifPresent(ignored -> setAllowPrivateNetwork(config, response));
            setMaxAge(config.getMaxAge(), response);
        } else {
            corsResponseDecorator.decorateResponseWithHeadersForPreflightRequest(request, response, config);
        }
    }

    private void decorateResponseWithHeaders(HttpRequest<?> request,
                                             MutableHttpResponse<?> response,
                                             CorsOriginConfiguration config) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator) {
            setOrigin(request.getOrigin().orElse(null), response);
            setVary(response);
            setExposeHeaders(config.getExposedHeaders(), response);
            setAllowCredentials(config, response);
        } else {
            corsResponseDecorator.decorateResponseWithHeaders(request, response, config);
        }
    }

    /**
     * @param config   The {@link CorsOriginConfiguration} instance
     * @param response The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setAllowCredentials(CorsOriginConfiguration config, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setAllowCredentials(config, response);
        }
    }

    /**
     * Sets the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK} in the response to {@code true}, if the {@link CorsOriginConfiguration#isAllowPrivateNetwork()} is {@code true}.
     *
     * @param config   The {@link CorsOriginConfiguration} instance
     * @param response The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setAllowPrivateNetwork(CorsOriginConfiguration config, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setAllowPrivateNetwork(config, response);
        }
    }

    /**
     * @param exposedHeaders A list of the exposed headers
     * @param response       The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setExposeHeaders(List<String> exposedHeaders, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setExposeHeaders(exposedHeaders, response);
        }
    }

    /**
     * @param response The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setVary(MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setVary(response);
        }
    }

    /**
     * @param origin   The origin
     * @param response The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setOrigin(@Nullable String origin, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setOrigin(origin, response);
        }
    }

    /**
     * @param method   The {@link HttpMethod} object
     * @param response The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setAllowMethods(HttpMethod method, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setAllowMethods(method, response);
        }
    }

    /**
     * @param optionalAllowHeaders A list with optional allow headers
     * @param response             The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setAllowHeaders(List<?> optionalAllowHeaders, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setAllowHeaders(optionalAllowHeaders, response);
        }
    }

    /**
     * @param maxAge   The max age
     * @param response The {@link MutableHttpResponse} object
     * @deprecated Use {@link CorsResponseDecorator} instead. This hook remains functional for
     * compatibility with existing {@link CorsFilter} subclasses.
     */
    @Deprecated(forRemoval = true, since = "5.2.0")
    protected void setMaxAge(long maxAge, MutableHttpResponse<?> response) {
        if (corsResponseDecorator instanceof DefaultCorsResponseDecorator decorator) {
            decorator.setMaxAge(maxAge, response);
        }
    }

    /**
     * @param corsOriginConfiguration CORS Origin configuration for request's HTTP Header origin.
     * @param request                 HTTP Request
     * @return {@literal true} if the resolved host is localhost or 127.0.0.1 address and the CORS configuration has any for allowed origins.
     */
    protected boolean shouldDenyToPreventDriveByLocalhostAttack(CorsOriginConfiguration corsOriginConfiguration,
                                                                HttpRequest<?> request) {
        if (corsConfiguration.isLocalhostPassThrough()) {
            return false;
        }
        if (httpHostResolver == null) {
            return false;
        }
        String origin = request.getOrigin().orElse(null);
        if (origin == null) {
            return false;
        }
        if (isOriginLocal(origin)) {
            return false;
        }
        String host = httpHostResolver.resolve(request);

        return (
            corsOriginConfiguration.getAllowedOriginsRegex().isEmpty() && isAny(corsOriginConfiguration.getAllowedOrigins())
        ) && isHostLocal(host);
    }

    /**
     * @param origin  HTTP Header {@link HttpHeaders#ORIGIN} value.
     * @param request HTTP Request
     * @return {@literal true} if the resolved host is localhost or 127.0.0.1 and origin is not one of these then deny it.
     */
    protected boolean shouldDenyToPreventDriveByLocalhostAttack(String origin,
                                                                HttpRequest<?> request) {
        if (corsConfiguration.isLocalhostPassThrough()) {
            return false;
        }
        if (httpHostResolver == null) {
            return false;
        }
        String host = httpHostResolver.resolve(request);
        return !isOriginLocal(origin) && isHostLocal(host);
    }

    /*
     * For Origin, we need to be more strict as otherwise an address like 127.malicious.com would be allowed.
     */
    private boolean isOriginLocal(String hostString) {
        try {
            URI uri = URI.create(hostString);
            String host = uri.getHost();
            return SocketUtils.LOCALHOST.equals(host) || "127.0.0.1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /*
     * We only need to check host for starting with "localhost" "127." (as there are multiple loopback addresses on linux)
     *
     * This is fine for host, as the request had to get here.
     *
     * We check the first character as a performance optimization prior to calling startsWith.
     */
    private boolean isHostLocal(String hostString) {
        if (hostString.isEmpty()) {
            return false;
        }
        char initialChar = hostString.charAt(0);
        if (initialChar != 'h' && initialChar != 'w') {
            return false;
        }
        return hostString.startsWith("http://localhost")
            || hostString.startsWith("https://localhost")
            || hostString.startsWith("http://127.")
            || hostString.startsWith("https://127.")
            || hostString.startsWith("ws://localhost")
            || hostString.startsWith("wss://localhost")
            || hostString.startsWith("ws://127.")
            || hostString.startsWith("wss://127.");
    }

    private Optional<CorsOriginConfiguration> getAnyConfiguration(HttpRequest<?> request) {
        String requestOrigin = request.getOrigin().orElse(null);
        List<UriRouteMatch<Object, Object>> routeMatches = router == null
            ? Collections.emptyList()
            : router.findAny(request);
        return Optional.ofNullable(corsOriginConfigurationRetriever.findCorsOriginConfiguration(requestOrigin, routeMatches));
    }

    private static MutableHttpResponse<Object> forbidden() {
        return HttpResponse.status(HttpStatus.FORBIDDEN);
    }

    private MutableHttpResponse<?> handlePreflightRequest(HttpRequest<?> request,
                                                          CorsOriginConfiguration corsOriginConfiguration) {
        boolean isValid = preflightRequestValidator.validatePreflightRequest(request, corsOriginConfiguration);
        if (!isValid) {
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }
        MutableHttpResponse<?> resp = HttpResponse.status(HttpStatus.OK);
        decorateResponseWithHeadersForPreflightRequest(request, resp, corsOriginConfiguration);
        decorateResponseWithHeaders(request, resp, corsOriginConfiguration);
        return resp;
    }

    /**
     * Sets the Cross-Origin-Embedder-Policy header from the CORS configuration when it is not already present.
     *
     * @param corsConfiguration CORS Configuration
     * @param response The {@link MutableHttpResponse} object
     * @since 5.2.0
     */
    private void setCrossOriginEmbedderPolicy(HttpServerConfiguration.CorsConfiguration corsConfiguration, MutableHttpResponse<?> response) {
        if (!response.getHeaders().contains(CROSS_ORIGIN_EMBEDDER_POLICY)) {
            CrossOriginEmbedderPolicy value = corsConfiguration.getCrossOriginEmbedderPolicy();
            if (value != null) {
                response.header(CROSS_ORIGIN_EMBEDDER_POLICY, value);
            }
        }
    }

    /**
     * Sets the Cross-Origin-Resource-Policy header from the CORS configuration when it is not already present.
     *
     * @param corsConfiguration CORS Configuration
     * @param response The {@link MutableHttpResponse} object
     * @since 5.2.0
     */
    private void setCrossOriginResourcePolicy(HttpServerConfiguration.CorsConfiguration corsConfiguration, MutableHttpResponse<?> response) {
        if (!response.getHeaders().contains(CROSS_ORIGIN_RESOURCE_POLICY)) {
            CrossOriginResourcePolicy value = corsConfiguration.getCrossOriginResourcePolicy();
            if (value != null) {
                response.header(CROSS_ORIGIN_RESOURCE_POLICY, value);
            }
        }
    }
}
