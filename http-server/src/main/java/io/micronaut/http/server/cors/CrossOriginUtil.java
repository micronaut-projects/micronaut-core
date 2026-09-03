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
package io.micronaut.http.server.cors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ImmutableArgumentConversionContext;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;

/**
 * Utilities for resolving and validating CORS settings declared with {@link CrossOrigin}.
 *
 * <p>This class converts annotation metadata into {@link CorsOriginConfiguration} instances
 * and provides the matching operations shared by the server CORS infrastructure.</p>
 *
 * @author Sergio del Amo
 * @since 3.9.0
 */
public final class CrossOriginUtil {
    /**
     * Conversion context for the HTTP method supplied by a CORS preflight request.
     *
     * @since 5.2.0
     */
    public static final ArgumentConversionContext<HttpMethod> CONVERSION_CONTEXT_HTTP_METHOD = ImmutableArgumentConversionContext.of(HttpMethod.class);

    /** The {@link CrossOrigin#allowedOrigins()} annotation member. */
    public static final String MEMBER_ALLOWED_ORIGINS = "allowedOrigins";
    /** The {@link CrossOrigin#allowedOriginsRegex()} annotation member. */
    public static final String MEMBER_ALLOWED_ORIGINS_REGEX = "allowedOriginsRegex";
    /** The {@link CrossOrigin#allowedHeaders()} annotation member. */
    public static final String MEMBER_ALLOWED_HEADERS = "allowedHeaders";
    /** The {@link CrossOrigin#exposedHeaders()} annotation member. */
    public static final String MEMBER_EXPOSED_HEADERS = "exposedHeaders";
    /** The {@link CrossOrigin#allowedMethods()} annotation member. */
    public static final String MEMBER_ALLOWED_METHODS = "allowedMethods";
    /** The {@link CrossOrigin#allowCredentials()} annotation member. */
    public static final String MEMBER_ALLOW_CREDENTIALS = "allowCredentials";
    /** The {@link CrossOrigin#allowPrivateNetwork()} annotation member. */
    public static final String MEMBER_ALLOW_PRIVATE_NETWORK = "allowPrivateNetwork";
    /** The {@link CrossOrigin#maxAge()} annotation member. */
    public static final String MEMBER_MAX_AGE = "maxAge";

    private CrossOriginUtil() {
    }

    /**
     * Determines whether the request origin is allowed by the CORS configuration.
     *
     * @param config the CORS configuration
     * @param requestOrigin the origin supplied by the request
     * @return {@code true} if the origin is allowed
     * @since 5.2.0
     */
    public static boolean matchesOrigin(CorsOriginConfiguration config, String requestOrigin) {
        if (config.getAllowedOriginsRegex().map(regex -> matchesOrigin(regex, requestOrigin)).orElse(false)) {
            return true;
        }
        List<String> allowedOrigins = config.getAllowedOrigins();
        return !allowedOrigins.isEmpty() && (
            (config.getAllowedOriginsRegex().isEmpty() && isAny(allowedOrigins)) ||
                allowedOrigins.stream().anyMatch(origin -> origin.equals(requestOrigin))
        );
    }

    /**
     * Determines whether an origin completely matches a regular expression.
     *
     * @param originRegex the regular expression configured for allowed origins
     * @param requestOrigin the origin supplied by the request
     * @return {@code true} if the complete request origin matches the expression
     */
    public static boolean matchesOrigin(String originRegex, String requestOrigin) {
        Pattern p = Pattern.compile(originRegex);
        Matcher m = p.matcher(requestOrigin);
        return m.matches();
    }

    /**
     * Determines whether a list represents the wildcard CORS value.
     *
     * @param values the configured values
     * @return {@code true} if the values allow any value
     */
    public static boolean isAny(List<String> values) {
        return Objects.equals(values, CorsOriginConfiguration.ANY);
    }

    /**
     * Determines whether a method list represents all HTTP methods.
     *
     * @param allowedMethods the configured methods
     * @return {@code true} if every HTTP method is allowed
     */
    public static boolean isAnyMethod(List<HttpMethod> allowedMethods) {
        return Objects.equals(allowedMethods, CorsOriginConfiguration.ANY_METHOD);
    }

    /**
     * Determines whether the method is allowed by the CORS configuration.
     *
     * @param config the CORS configuration
     * @param methodToMatch the method to validate
     * @return {@code true} if the method is allowed
     * @since 5.2.0
     */
    public static boolean methodAllowed(CorsOriginConfiguration config,
                                        HttpMethod methodToMatch) {
        List<HttpMethod> allowedMethods = config.getAllowedMethods();
        return isAnyMethod(allowedMethods) || allowedMethods.stream().anyMatch(method -> method.equals(methodToMatch));
    }

    /**
     * Resolves the method to validate for a CORS request.
     *
     * @param request the HTTP request
     * @return the requested method for a preflight request, or the request method otherwise
     * @since 5.2.0
     */
    public static HttpMethod methodToMatch(HttpRequest<?> request) {
        HttpMethod requestMethod = request.getMethod();
        return CorsUtil.isPreflightRequest(request) ? request.getHeaders().getFirst(ACCESS_CONTROL_REQUEST_METHOD, CONVERSION_CONTEXT_HTTP_METHOD).orElse(requestMethod) : requestMethod;
    }

    /**
     * Determines whether the headers requested by a preflight request are allowed.
     *
     * @param request the HTTP request
     * @param config the CORS configuration
     * @return {@code true} if all requested headers are allowed
     * @since 5.2.0
     */
    public static boolean hasAllowedHeaders(HttpRequest<?> request, CorsOriginConfiguration config) {
        Optional<List<String>> accessControlHeaders = request.getHeaders().get(ACCESS_CONTROL_REQUEST_HEADERS, ConversionContext.LIST_OF_STRING);
        List<String> allowedHeaders = config.getAllowedHeaders();
        return isAny(allowedHeaders) || (
            accessControlHeaders.isPresent() &&
                accessControlHeaders.get().stream().allMatch(header -> allowedHeaders.stream().anyMatch(allowedHeader -> allowedHeader.equalsIgnoreCase(header.trim())))
        );
    }

    /**
     * Validates the method supplied by a CORS request against the configuration.
     *
     * @param request the HTTP request
     * @param config the CORS configuration
     * @return the requested method if it is allowed, or an empty optional otherwise
     * @since 5.2.0
     */
    public static Optional<HttpMethod> validateMethodToMatch(HttpRequest<?> request,
                                                              CorsOriginConfiguration config) {
        HttpMethod methodToMatch = methodToMatch(request);
        if (!methodAllowed(config, methodToMatch)) {
            return Optional.empty();
        }
        return Optional.of(methodToMatch);
    }

    /**
     * Determines whether the method represented by a CORS request is allowed by the
     * configuration.
     *
     * <p>For a preflight request, this validates the method declared by the
     * {@value io.micronaut.http.HttpHeaders#ACCESS_CONTROL_REQUEST_METHOD} header. For an
     * actual CORS request, it validates the request's HTTP method.</p>
     *
     * @param request the CORS request
     * @param config the CORS configuration
     * @return {@code true} if the request method is allowed
     * @since 5.2.0
     */
    public static boolean matchesMethod(HttpRequest<?> request,
                                        CorsOriginConfiguration config) {
        return validateMethodToMatch(request, config).isPresent();
    }

    /**
     * Resolves CORS configuration associated with the route for an HTTP request.
     *
     * @param request the HTTP request for the configuration
     * @return the route's CORS configuration, or an empty optional if the request has no
     * matching route or the route is not annotated with {@link CrossOrigin}
     */
    public static Optional<CorsOriginConfiguration> getCorsOriginConfigurationForRequest(HttpRequest<?> request) {
        return RouteAttributes.getRouteMatch(request)
            .flatMap(CrossOriginUtil::getCorsOriginConfigurationForAnnotationMetadataProvider);
    }

    /**
     * Resolves CORS configuration from an annotation metadata provider.
     *
     * @param annotationMetadataProvider the annotation metadata provider
     * @return the CORS configuration declared by the provider, or an empty optional if it
     * does not declare {@link CrossOrigin}
     * @since 5.4.0
     */
    public static Optional<CorsOriginConfiguration> getCorsOriginConfigurationForAnnotationMetadataProvider(AnnotationMetadataProvider annotationMetadataProvider) {
        return getCorsOriginConfiguration(annotationMetadataProvider.getAnnotationMetadata());
    }

    /**
     * Creates CORS configuration from {@link CrossOrigin} annotation metadata.
     *
     * <p>Empty annotation members are mapped to the defaults understood by the server: any
     * origin, header, or method. If an origin regular expression is present, the wildcard
     * origin default is not added, so only the expression and explicitly declared origins
     * are considered.</p>
     *
     * @param annotationMetadata the route annotation metadata
     * @return the resolved CORS configuration, or an empty optional if the metadata does not
     * contain {@link CrossOrigin}
     */
    public static Optional<CorsOriginConfiguration> getCorsOriginConfiguration(AnnotationMetadata annotationMetadata) {
        if (!annotationMetadata.hasAnnotation(CrossOrigin.class)) {
            return Optional.empty();
        }

        CorsOriginConfiguration config = new CorsOriginConfiguration();
        String[] allowedOrigins = annotationMetadata.stringValues(CrossOrigin.class, MEMBER_ALLOWED_ORIGINS);
        annotationMetadata.stringValue(CrossOrigin.class, MEMBER_ALLOWED_ORIGINS_REGEX).ifPresentOrElse(
            regex -> {
                config.setAllowedOriginsRegex(regex);
                // when allowed-origins-regex is set, don't default allowed-origins to ANY, use both iff set explicitly
                config.setAllowedOrigins(Arrays.asList(allowedOrigins));
            },
            () -> config.setAllowedOrigins(allowedOrigins.length == 0 ? CorsOriginConfiguration.ANY : Arrays.asList(allowedOrigins))
        );

        String[] allowedHeaders = annotationMetadata.stringValues(CrossOrigin.class, MEMBER_ALLOWED_HEADERS);
        List<String> allowedHeadersList = allowedHeaders.length == 0 ? CorsOriginConfiguration.ANY : Arrays.asList(allowedHeaders);
        config.setAllowedHeaders(allowedHeadersList);
        config.setExposedHeaders(Arrays.asList(annotationMetadata.stringValues(CrossOrigin.class, MEMBER_EXPOSED_HEADERS)));

        List<HttpMethod> allowedMethods = Stream.of(annotationMetadata.stringValues(CrossOrigin.class, MEMBER_ALLOWED_METHODS))
            .map(HttpMethod::parse)
            .filter(method -> method != HttpMethod.CUSTOM)
            .toList();
        config.setAllowedMethods(CollectionUtils.isNotEmpty(allowedMethods) ? allowedMethods : CorsOriginConfiguration.ANY_METHOD);

        annotationMetadata.booleanValue(CrossOrigin.class, MEMBER_ALLOW_CREDENTIALS)
            .ifPresent(config::setAllowCredentials);
        annotationMetadata.booleanValue(CrossOrigin.class, MEMBER_ALLOW_PRIVATE_NETWORK)
                .ifPresent(config::setAllowPrivateNetwork);
        annotationMetadata.longValue(CrossOrigin.class, MEMBER_MAX_AGE)
            .ifPresent(config::setMaxAge);
        return Optional.of(config);
    }
}
