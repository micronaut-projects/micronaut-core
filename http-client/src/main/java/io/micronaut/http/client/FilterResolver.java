/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.http.client;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.FilterProperties;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.RuntimeHttpClientFilter;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolver that responsible to finding all the http client filters that should be applied to given request.
 */
class FilterResolver {

    private final Collection<HttpClientFilter> filters;
    private final AnnotationMetadata annotationMetadata;
    private final AnnotationMetadataResolver annotationMetadataResolver;
    private final  Supplier<Set<String>> clientIdentifiers;

    /**
     * @param filters                    all filters
     * @param annotationMetadata         annotation metadata of a declarative http client
     * @param annotationMetadataResolver annotation medata resolver
     * @param clientIdentifiers          client identifiers associated with a client
     */
    FilterResolver(Collection<HttpClientFilter> filters,
                   AnnotationMetadata annotationMetadata,
                   AnnotationMetadataResolver annotationMetadataResolver,
                   Supplier<Set<String>> clientIdentifiers) {
        this.filters = filters;
        this.annotationMetadata = annotationMetadata;
        this.annotationMetadataResolver = annotationMetadataResolver;
        this.clientIdentifiers = clientIdentifiers;
    }

    /**
     * @param request    http request
     * @param requestURI path URI
     * @return filters that should be applied to request
     */
    List<HttpClientFilter> resolveFilters(io.micronaut.http.HttpRequest<?> request,
                                          URI requestURI) {
        List<HttpClientFilter> filterList = new ArrayList<>();
        String requestPath = StringUtils.prependUri("/", requestURI.getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        for (HttpClientFilter filter : filters) {
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }

            FilterProperties filterProperties = annotationMetadataResolver.resolveMetadata(filter)
                    .findAnnotation(Filter.class)
                    .map(FilterProperties::new)
                    .orElse(FilterProperties.EMPTY_FILTER_PROPERTIES);

            if (filter instanceof RuntimeHttpClientFilter) {
                filterProperties = filterProperties.merge(((RuntimeHttpClientFilter) filter).getFilterProperties());
            }

            boolean filterMatched = Optional.of(filterProperties)
                    .filter(this::filterByAnnotationMarker)
                    .filter(this::filterByServiceId)
                    .filter(filterAnnotation -> filterByMethod(filterAnnotation, method))
                    .filter(filterAnnotation -> filterByPath(filterAnnotation, requestPath))
                    .isPresent();

            if (filterMatched) {
                filterList.add(filter);
            }
        }

        return filterList;
    }

    private boolean filterByPath(FilterProperties filterProperties, String requestPath) {
        String[] patterns = filterProperties.getPatterns();

        return patterns.length == 0
                || Arrays.stream(patterns).anyMatch(pathPattern -> PathMatcher.ANT.matches(pathPattern, requestPath));
    }

    private boolean filterByMethod(FilterProperties filterProperties, HttpMethod method) {
        io.micronaut.http.HttpMethod[] methods = filterProperties.getMethods();

        return ArrayUtils.isEmpty(methods) || Arrays.asList(methods).contains(method);
    }

    private boolean filterByServiceId(FilterProperties filterProperties) {
        String[] clients = filterProperties.getServiceId();

        return ArrayUtils.isEmpty(clients) || Arrays.stream(clients).anyMatch(clientIdentifiers.get()::contains);
    }

    private boolean filterByAnnotationMarker(FilterProperties filterProperties) {
        Class<? extends Annotation>[] annotationMarkers = filterProperties.getAnnotationMarkers();

        boolean markerPresent = Arrays.stream(annotationMarkers).anyMatch(annotationMetadata::hasStereotype);
        boolean markerIsRequired = annotationMarkers.length > 0;

        return !markerIsRequired || markerPresent;
    }
}
