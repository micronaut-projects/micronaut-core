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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.QueryStringDecoder;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.inject.DelegatingExecutableMethod;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of the {@link RouteMatch} interface for matches to URIs.
 *
 * @param <T> The target type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class DefaultUriRouteMatch<T, R> extends AbstractRouteMatch<T, R> implements UriRouteMatch<T, R> {

    private final UriMatchInfo matchInfo;
    private final Charset defaultCharset;
    private final boolean plusAsSpace;
    private final @Nullable MediaType selectedMediaType;
    /**
     * The status a route selector rejected the request with, {@code 406} or {@code 415}, see
     * {@link io.micronaut.web.router.spi.RouteMatchSelector.Selection#rejectionStatus()}.
     */
    private final @Nullable HttpStatus rejectionStatus;
    @Nullable
    private Map<String, Object> variables;
    private @Nullable Map<String, Object> rawVariables;
    private @Nullable ExecutableMethod<T, R> inheritingMethod;

    /**
     * @param matchInfo The URI match info
     * @param routeInfo The URI route
     * @param defaultCharset The default charset
     * @param plusAsSpace Whether a {@code +} in a path variable is decoded as a space, see {@link RouteAssembly#PATH_VARIABLE_PLUS_AS_SPACE}
     * @param conversionService The conversion service
     */
    DefaultUriRouteMatch(UriMatchInfo matchInfo,
                         UriRouteInfo<T, R> routeInfo,
                         Charset defaultCharset,
                         boolean plusAsSpace,
                         ConversionService conversionService
    ) {
        this(matchInfo, routeInfo, defaultCharset, plusAsSpace, conversionService, null);
    }

    DefaultUriRouteMatch(UriMatchInfo matchInfo,
                                 UriRouteInfo<T, R> routeInfo,
                                 Charset defaultCharset,
                                 boolean plusAsSpace,
                                 ConversionService conversionService,
                                 @Nullable MediaType selectedMediaType) {
        this(matchInfo, routeInfo, defaultCharset, plusAsSpace, conversionService, selectedMediaType, null);
    }

    private DefaultUriRouteMatch(UriMatchInfo matchInfo,
                                 UriRouteInfo<T, R> routeInfo,
                                 Charset defaultCharset,
                                 boolean plusAsSpace,
                                 ConversionService conversionService,
                                 @Nullable MediaType selectedMediaType,
                                 @Nullable HttpStatus rejectionStatus) {
        super(routeInfo, conversionService);
        this.matchInfo = matchInfo;
        this.defaultCharset = defaultCharset;
        this.plusAsSpace = plusAsSpace;
        this.selectedMediaType = selectedMediaType;
        this.rejectionStatus = rejectionStatus;
    }

    /**
     * @param mediaType The media type of the response a route selector negotiated
     * @return A new match of the same route and path with the media type
     */
    DefaultUriRouteMatch<T, R> withSelectedMediaType(MediaType mediaType) {
        return new DefaultUriRouteMatch<>(matchInfo, getRouteInfo(), defaultCharset, plusAsSpace, conversionService, mediaType);
    }

    /**
     * @param matchInfo The match info of the same route, e.g. with the variables of the prefixes of locators
     * @return A new match with the match info and the negotiated media type of this match
     */
    DefaultUriRouteMatch<T, R> withMatchInfo(UriMatchInfo matchInfo) {
        return new DefaultUriRouteMatch<>(matchInfo, getRouteInfo(), defaultCharset, plusAsSpace, conversionService, selectedMediaType, rejectionStatus);
    }

    /**
     * @param status The status a route selector rejected the request with, {@code 406} or {@code 415}
     * @return A new match of the same route and path, rejected with the status
     */
    DefaultUriRouteMatch<T, R> withRejectionStatus(HttpStatus status) {
        return new DefaultUriRouteMatch<>(matchInfo, getRouteInfo(), defaultCharset, plusAsSpace, conversionService, selectedMediaType, status);
    }

    /**
     * The status the route selector of the route rejected the request with: a match the router
     * finds for the routes of any method, see {@link Router#findAny(io.micronaut.http.HttpRequest)},
     * which the request is answered with {@code 406} or {@code 415} for, with the types of the route.
     *
     * @return The status, or {@code null} if the match is not rejected
     * @since 5.3.0
     */
    public @Nullable HttpStatus rejectionStatus() {
        return rejectionStatus;
    }

    @Override
    public Optional<MediaType> getSelectedMediaType() {
        return Optional.ofNullable(selectedMediaType);
    }

    @Override
    @Nullable MediaType selectedMediaType() {
        return selectedMediaType;
    }

    /**
     * @return The URI match info, with the raw values of the variables
     */
    UriMatchInfo matchInfo() {
        return matchInfo;
    }

    @Override
    @Nullable Object resolvedTarget() {
        return matchInfo instanceof DynamicRouteTarget.ResolvedMatchInfo resolved ? resolved.target() : null;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        ExecutableMethod<T, R> inheriting = inheritingMethod();
        return inheriting == null ? super.getAnnotationMetadata() : inheriting.getAnnotationMetadata();
    }

    @Override
    public ExecutableMethod<T, R> getExecutableMethod() {
        ExecutableMethod<T, R> inheriting = inheritingMethod();
        return inheriting == null ? super.getExecutableMethod() : inheriting;
    }

    /**
     * The method of a route resolved by a {@link DynamicRouteTarget} that inherits annotations
     * from the resolution, e.g. a route reached through a route locator, which has the annotations
     * of the groups of the locator routes: the readers of the annotations of the match and of its
     * method, such as the route match filters, see them, overridden by the ones of the route.
     *
     * @return The method with the inherited annotations, or {@code null} if the route inherits none
     */
    private @Nullable ExecutableMethod<T, R> inheritingMethod() {
        ExecutableMethod<T, R> inheriting = inheritingMethod;
        if (inheriting == null) {
            if (!(matchInfo instanceof DynamicRouteTarget.ResolvedMatchInfo resolved) || resolved.annotationMetadata().isEmpty()) {
                return null;
            }
            AnnotationMetadata metadata = RouteLocator.layered(resolved.annotationMetadata(), super.getExecutableMethod().getAnnotationMetadata());
            inheriting = new InheritingExecutableMethod<>(super.getExecutableMethod(), metadata);
            inheritingMethod = inheriting;
        }
        return inheriting;
    }

    @Override
    public String getUri() {
        return matchInfo.getUri();
    }

    @Override
    public Map<String, Object> getVariableValues() {
        if (variables == null) {
            Map<String, Object> matchVariables = matchInfo.getVariableValues();
            if (CollectionUtils.isNotEmpty(matchVariables)) {
                variables = CollectionUtils.newLinkedHashMap(matchVariables.size());
                matchVariables.forEach((k, v) -> {
                    if (v instanceof CharSequence) {
                        v = plusAsSpace
                            ? URLDecoder.decode(v.toString(), defaultCharset)
                            : QueryStringDecoder.decodePathComponent(v.toString(), defaultCharset);
                    }
                    variables.put(k, v);
                });
            } else {
                variables = Map.of();
            }
        }
        return variables;
    }

    @Override
    public Map<String, Object> getRawVariableValues() {
        return Collections.unmodifiableMap(rawVariableValues());
    }

    @Override
    Map<String, Object> rawVariableValues() {
        Map<String, Object> raw = rawVariables;
        if (raw == null) {
            Map<String, Object> matchVariables = matchInfo.getVariableValues();
            if (CollectionUtils.isEmpty(matchVariables)) {
                raw = Map.of();
            } else if (matchVariables.containsValue(null)) {
                // an optional variable that is absent has no value
                raw = CollectionUtils.newLinkedHashMap(matchVariables.size());
                for (Map.Entry<String, Object> entry : matchVariables.entrySet()) {
                    if (entry.getValue() != null) {
                        raw.put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                raw = matchVariables;
            }
            rawVariables = raw;
        }
        return raw;
    }

    @Override
    public List<UriMatchVariable> getVariables() {
        return matchInfo.getVariables();
    }

    @Override
    public Map<String, UriMatchVariable> getVariableMap() {
        return matchInfo.getVariableMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public UriRouteInfo<T, R> getRouteInfo() {
        // the route info of the superclass, which the constructors only take as a URI route
        return (UriRouteInfo<T, R>) routeInfo;
    }

    @Override
    public HttpMethod getHttpMethod() {
        return getRouteInfo().getHttpMethod();
    }

    @Override
    public String toString() {
        return getRouteInfo().getHttpMethod() + " - " + matchInfo.getUri();
    }

    /**
     * The method of a route with the annotations it inherits.
     *
     * @param target             The method of the route
     * @param annotationMetadata The inherited annotations, overridden by the ones of the method
     * @param <T>                The target type
     * @param <R>                The return type
     */
    private record InheritingExecutableMethod<T, R>(ExecutableMethod<T, R> target,
                                                    AnnotationMetadata annotationMetadata) implements DelegatingExecutableMethod<T, R> {

        @Override
        public ExecutableMethod<T, R> getTarget() {
            return target;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }
    }
}
