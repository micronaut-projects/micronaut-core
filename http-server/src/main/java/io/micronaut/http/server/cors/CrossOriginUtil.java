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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteAttributes;

import java.util.Arrays;
import java.util.List;
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
    public static final String MEMBER_ALLOWED_ORIGINS_REGEX = "allowedOriginsRegex";
    public static final String MEMBER_ALLOWED_HEADERS = "allowedHeaders";
    public static final String MEMBER_EXPOSED_HEADERS = "exposedHeaders";
    public static final String MEMBER_ALLOWED_METHODS = "allowedMethods";
    public static final String MEMBER_ALLOW_CREDENTIALS = "allowCredentials";
    public static final String MEMBER_ALLOW_PRIVATE_NETWORK = "allowPrivateNetwork";
    public static final String MEMBER_MAX_AGE = "maxAge";

    private CrossOriginUtil() {
    }

    /**
     * @param request the HTTP request for the configuration
     * @return the cors origin configuration for the given request
     */
    @NonNull
    public static Optional<CorsOriginConfiguration> getCorsOriginConfigurationForRequest(@NonNull HttpRequest<?> request) {
        return RouteAttributes.getRouteMatch(request)
            .flatMap(rm -> getCorsOriginConfiguration((AnnotationMetadata) rm));
    }

    /**
     * @param annotationMetadata The route annotation metadata
     * @return The possible CORS configuration
     */
    public static Optional<CorsOriginConfiguration> getCorsOriginConfiguration(@NonNull AnnotationMetadata annotationMetadata) {
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
            .collect(Collectors.toList());
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
