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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility classes to work with {@link CrossOrigin}.
 * @author Sergio del Amo
 * @since 3.9.0
 */
public final class CrossOriginUtil {

    public static final String MEMBER_ALLOWED_ORIGINS = "allowedOrigins";
    public static final String MEMBER_ALLOWED_HEADERS = "allowedHeaders";
    public static final String MEMBER_EXPOSED_HEADERS = "exposedHeaders";
    public static final String MEMBER_ALLOWED_METHODS = "allowedMethods";
    public static final String MEMBER_ALLOW_CREDENTIALS = "allowCredentials";
    public static final String MEMBER_MAX_AGE = "maxAge";

    private CrossOriginUtil() {
    }

    /**
     * @param request the HTTP request for the configuration
     * @return the cors origin configuration for the given request
     */
    @NonNull
    public static Optional<CorsOriginConfiguration> getCorsOriginConfigurationForRequest(@NonNull HttpRequest<?> request) {
        RouteMatch<?> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
        return (routeMatch instanceof MethodBasedRouteMatch) ?
            getCorsOriginConfiguration((MethodBasedRouteMatch<?, ?>) routeMatch) :
            Optional.empty();
    }

    @NonNull
    private static Optional<CorsOriginConfiguration> getCorsOriginConfiguration(@NonNull MethodBasedRouteMatch<?, ?> methodRoute) {
        if (!methodRoute.hasAnnotation(CrossOrigin.class)) {
            return Optional.empty();
        }
        AnnotationValue<CrossOrigin> annotationValue = methodRoute.getDeclaredAnnotation(CrossOrigin.class);
        return annotationValue != null ?
            getCorsOriginConfiguration(annotationValue) :
            Optional.empty();
    }

    private static Optional<CorsOriginConfiguration> getCorsOriginConfiguration(@NonNull AnnotationValue<CrossOrigin> annotationValue) {
        CorsOriginConfiguration annotated = new CorsOriginConfiguration();
        annotated.setAllowedOrigins(Arrays.asList(annotationValue.stringValues(MEMBER_ALLOWED_ORIGINS)));

        annotated.setAllowedHeaders(Arrays.asList(annotationValue.stringValues(MEMBER_ALLOWED_HEADERS)));
        annotated.setExposedHeaders(Arrays.asList(annotationValue.stringValues(MEMBER_EXPOSED_HEADERS)));
        annotated.setAllowedMethods(Stream.of(annotationValue.stringValues(MEMBER_ALLOWED_METHODS))
            .map(HttpMethod::parse)
            .filter(method -> method != HttpMethod.CUSTOM)
            .collect(Collectors.toList()));

        annotationValue.booleanValue(MEMBER_ALLOW_CREDENTIALS)
            .ifPresent(annotated::setAllowCredentials);
        annotationValue.longValue(MEMBER_MAX_AGE)
            .ifPresent(annotated::setMaxAge);
        return Optional.of(annotated);
    }
}
