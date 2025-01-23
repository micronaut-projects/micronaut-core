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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ImmutableArgumentConversionContext;
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
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

/**
 * Responsible for handling CORS requests and responses.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@ServerFilter(MATCH_ALL_PATTERN)
public class CorsFilter implements Ordered, ConditionalFilter {
    public static final int CORS_FILTER_ORDER = ServerFilterPhase.METRICS.after();

    private static final Logger LOG = LoggerFactory.getLogger(CorsFilter.class);
    private static final ArgumentConversionContext<HttpMethod> CONVERSION_CONTEXT_HTTP_METHOD = ImmutableArgumentConversionContext.of(HttpMethod.class);

    protected final HttpServerConfiguration.CorsConfiguration corsConfiguration;

    @Nullable
    private final HttpHostResolver httpHostResolver;

    private final Router router;

    /**
     * @param corsConfiguration The {@link CorsOriginConfiguration} instance
     * @param httpHostResolver  HTTP Host resolver
     * @deprecated use {@link CorsFilter(HttpServerConfiguration.CorsConfiguration, HttpHostResolver, Router)} instead.
     */
    @Deprecated(since = "4.7", forRemoval = true)
    public CorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration,
                      @Nullable HttpHostResolver httpHostResolver) {
        this.corsConfiguration = corsConfiguration;
        this.httpHostResolver = httpHostResolver;
        this.router = null;
    }

    /**
     * @param corsConfiguration The {@link CorsOriginConfiguration} instance
     * @param httpHostResolver  HTTP Host resolver
     * @param router  Router
     */
    @Inject
    public CorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration,
                      @Nullable HttpHostResolver httpHostResolver,
                      Router router) {
        this.corsConfiguration = corsConfiguration;
        this.httpHostResolver = httpHostResolver;
        this.router = router;
    }

    @Override
    public boolean isEnabled(HttpRequest<?> request) {
        String origin = request.getOrigin().orElse(null);
        if (origin == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Http Header " + HttpHeaders.ORIGIN + " not present. Proceeding with the request.");
            }
            return false;
        }
        return true;
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
            LOG.trace("Http Header {} not present. Proceeding with the request.", HttpHeaders.ORIGIN);
            return null; // proceed
        }
        CorsOriginConfiguration corsOriginConfiguration = getConfiguration(request).orElse(null);
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
        CorsOriginConfiguration corsOriginConfiguration = getConfiguration(request).orElse(null);
        if (corsOriginConfiguration != null) {
            if (CorsUtil.isPreflightRequest(request)) {
                decorateResponseWithHeadersForPreflightRequest(request, response, corsOriginConfiguration);
            }
            decorateResponseWithHeaders(request, response, corsOriginConfiguration);
        }
    }

    /**
     * @param corsOriginConfiguration CORS Origin configuration for request's HTTP Header origin.
     * @param request                 HTTP Request
     * @return {@literal true} if the resolved host is localhost or 127.0.0.1 address and the CORS configuration has any for allowed origins.
     */
    protected boolean shouldDenyToPreventDriveByLocalhostAttack(@NonNull CorsOriginConfiguration corsOriginConfiguration,
                                                                @NonNull HttpRequest<?> request) {
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
    protected boolean shouldDenyToPreventDriveByLocalhostAttack(@NonNull String origin,
                                                                @NonNull HttpRequest<?> request) {
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
     * We only need to check host for starting with "localhost" "127." (as there are multiple loopback addresses on linux)
     *
     * This is fine for host, as the request had to get here.
     *
     * We check the first character as a performance optimization prior to calling startsWith.
     */
    private boolean isHostLocal(@NonNull String hostString) {
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

    /*
     * For Origin, we need to be more strict as otherwise an address like 127.malicious.com would be allowed.
     */
    private boolean isOriginLocal(@NonNull String hostString) {
        try {
            URI uri = URI.create(hostString);
            String host = uri.getHost();
            return SocketUtils.LOCALHOST.equals(host) || "127.0.0.1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return CORS_FILTER_ORDER;
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
            response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS, StringUtils.TRUE);
        }
    }

    /**
     * Sets the HTTP Header {@value HttpHeaders#ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK} in the response to {@code true}, if the {@link CorsOriginConfiguration#isAllowPrivateNetwork()} is {@code true}.
     *
     * @param config   The {@link CorsOriginConfiguration} instance
     * @param response The {@link MutableHttpResponse} object
     */
    protected void setAllowPrivateNetwork(CorsOriginConfiguration config, MutableHttpResponse<?> response) {
        if (config.isAllowPrivateNetwork()) {
            response.header(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, StringUtils.TRUE);
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
    private Optional<CorsOriginConfiguration> getConfiguration(@NonNull HttpRequest<?> request) {
        String requestOrigin = request.getOrigin().orElse(null);
        if (requestOrigin == null) {
            return Optional.empty();
        }
        Optional<CorsOriginConfiguration> originConfiguration = CrossOriginUtil.getCorsOriginConfigurationForRequest(request);
        if (originConfiguration.isPresent() && matchesOrigin(originConfiguration.get(), requestOrigin)) {
            return originConfiguration;
        }
        if (!corsConfiguration.isEnabled()) {
            return Optional.empty();
        }
        return corsConfiguration.getConfigurations().values().stream()
            .filter(config -> matchesOrigin(config, requestOrigin))
            .findFirst();
    }

    @NonNull
    private Optional<CorsOriginConfiguration> getAnyConfiguration(@NonNull HttpRequest<?> request) {
        String requestOrigin = request.getOrigin().orElse(null);
        if (requestOrigin == null) {
            return Optional.empty();
        }
        for (UriRouteMatch<Object, Object> routeMatch : router.findAny(request)) {
            Optional<CorsOriginConfiguration> corsOriginConfiguration = CrossOriginUtil.getCorsOriginConfiguration(routeMatch);
            if (corsOriginConfiguration.isPresent() && matchesOrigin(corsOriginConfiguration.get(), requestOrigin)) {
                return corsOriginConfiguration;
            }
        }
        if (!corsConfiguration.isEnabled()) {
            return Optional.empty();
        }
        return corsConfiguration.getConfigurations().values().stream()
            .filter(config -> matchesOrigin(config, requestOrigin))
            .findFirst();
    }

    private static boolean matchesOrigin(@NonNull CorsOriginConfiguration config, String requestOrigin) {
        if (config.getAllowedOriginsRegex().map(regex -> matchesOrigin(regex, requestOrigin)).orElse(false)) {
            return true;
        }
        List<String> allowedOrigins = config.getAllowedOrigins();
        return !allowedOrigins.isEmpty() && (
            (config.getAllowedOriginsRegex().isEmpty() && isAny(allowedOrigins)) ||
                allowedOrigins.stream().anyMatch(origin -> origin.equals(requestOrigin))
        );
    }

    private static boolean matchesOrigin(@NonNull String originRegex, @NonNull String requestOrigin) {
        Pattern p = Pattern.compile(originRegex);
        Matcher m = p.matcher(requestOrigin);
        return m.matches();
    }

    private static boolean isAny(List<String> values) {
        return values == CorsOriginConfiguration.ANY;
    }

    private static boolean isAnyMethod(List<HttpMethod> allowedMethods) {
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

    @NonNull
    private static MutableHttpResponse<Object> forbidden() {
        return HttpResponse.status(HttpStatus.FORBIDDEN);
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
        headers.getFirst(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, ConversionContext.BOOLEAN)
            .ifPresent(value -> setAllowPrivateNetwork(config, response));
        setMaxAge(config.getMaxAge(), response);
    }

    @NonNull
    private void decorateResponseWithHeaders(@NonNull HttpRequest<?> request,
                                             @NonNull MutableHttpResponse<?> response,
                                             @NonNull CorsOriginConfiguration config) {
        setOrigin(request.getOrigin().orElse(null), response);
        setVary(response);
        setExposeHeaders(config.getExposedHeaders(), response);
        setAllowCredentials(config, response);
    }

    @NonNull
    private MutableHttpResponse<?> handlePreflightRequest(@NonNull HttpRequest<?> request,
                                                          @NonNull CorsOriginConfiguration corsOriginConfiguration) {
        boolean isValid = validatePreflightRequest(request, corsOriginConfiguration);
        if (!isValid) {
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }
        MutableHttpResponse<?> resp = HttpResponse.status(HttpStatus.OK);
        decorateResponseWithHeadersForPreflightRequest(request, resp, corsOriginConfiguration);
        decorateResponseWithHeaders(request, resp, corsOriginConfiguration);
        return resp;
    }

    @Nullable
    private boolean validatePreflightRequest(@NonNull HttpRequest<?> request,
                                                @NonNull CorsOriginConfiguration config) {
        Optional<HttpMethod> methodToMatchOptional = validateMethodToMatch(request, config);
        if (methodToMatchOptional.isEmpty()) {
            return false;
        }
        HttpMethod methodToMatch = methodToMatchOptional.get();

        if (!CorsUtil.isPreflightRequest(request)) {
            return false;
        }
        List<HttpMethod> availableHttpMethods = router.findAny(request).stream().map(UriRouteMatch::getHttpMethod).toList();
        if (availableHttpMethods.stream().noneMatch(method -> method.equals(methodToMatch))) {
            return false;
        }

        if (!hasAllowedHeaders(request, config)) {
            return false;
        }

        if (request.getHeaders().contains(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK)) {
            boolean accessControlRequestPrivateNetwork = request.getHeaders().get(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, Boolean.class, Boolean.FALSE);
            if (accessControlRequestPrivateNetwork && !config.isAllowPrivateNetwork()) {
                return false;
            }
        }
        return true;
    }
}
