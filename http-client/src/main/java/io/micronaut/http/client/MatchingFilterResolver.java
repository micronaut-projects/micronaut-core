/*
 *  Copyright 2017-2019 original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.micronaut.http.client;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterAnnotation;
import io.micronaut.http.filter.FilterProperties;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.MatchingHttpClientFilter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolver that responsible to finding all the http client filters that should be applied to given request.
 *
 * @since 1.2.4
 * @author svishnyakoff
 */
@Primary
@Singleton
public class MatchingFilterResolver implements FilterResolver {

    private final Collection<HttpClientFilter> filters;
    private final AnnotationMetadata annotationMetadata;
    private final AnnotationMetadataResolver annotationMetadataResolver;
    private final Set<String> clientIdentifiers;

    /**
     * @param annotationMetadata         annotation metadata of a declarative http client
     * @param clientIdentifiers          client identifiers associated with a client
     * @param annotationMetadataResolver annotation medata resolver
     * @param filters                    all filters
     */
    @Inject
    MatchingFilterResolver(@Nullable @Parameter AnnotationMetadata annotationMetadata,
                           @Nullable @Parameter Set<String> clientIdentifiers,
                           @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                           Collection<HttpClientFilter> filters) {
        this.filters = filters;
        this.annotationMetadata = Optional.ofNullable(annotationMetadata).orElse(AnnotationMetadata.EMPTY_METADATA);
        this.annotationMetadataResolver = Optional.ofNullable(annotationMetadataResolver)
                .orElse(AnnotationMetadataResolver.DEFAULT);
        this.clientIdentifiers = Optional.ofNullable(clientIdentifiers).orElse(Collections.emptySet());
    }

    /**
     * @param request    http request
     * @param requestURI path URI
     * @return filters that should be applied to request
     */
    public List<HttpClientFilter> resolveFilters(io.micronaut.http.HttpRequest<?> request,
                                          URI requestURI) {
        List<HttpClientFilter> filterList = new ArrayList<>();
        String requestPath = StringUtils.prependUri("/", requestURI.getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        for (HttpClientFilter filter : filters) {
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }

            Class<? extends Annotation>[] clientDeclaredFilterAnnotations = (Class<? extends Annotation>[]) annotationMetadata
                    .getAnnotationTypesByStereotype(FilterAnnotation.class)
                    .stream()
                    .toArray(Class[]::new);

            Class<? extends Annotation>[] declaredFilterAnnotations = (Class<? extends Annotation>[]) annotationMetadataResolver.resolveMetadata(filter)
                    .getAnnotationTypesByStereotype(FilterAnnotation.class)
                    .stream()
                    .toArray(Class[]::new);

            Optional<AnnotationValue<Filter>> filterAnnotationMetadataOpt = annotationMetadataResolver.resolveMetadata(filter)
                    .findAnnotation(Filter.class);

            FilterProperties annotationFilterProperties = filterAnnotationMetadataOpt
                    .map(f -> new FilterProperties(
                            f.stringValues("patterns"),
                            f.get("methods", io.micronaut.http.HttpMethod[].class, null),
                            f.stringValues("serviceId"),
                            declaredFilterAnnotations)
                    )
                    .orElse(FilterProperties.EMPTY_FILTER_PROPERTIES);

            FilterProperties instanceProperties = Optional.of(filter)
                    .filter(f -> f instanceof MatchingFilterResolver)
                    .map(f -> (MatchingHttpClientFilter) f)
                    .map(MatchingHttpClientFilter::getFilterProperties)
                    .orElse(FilterProperties.EMPTY_FILTER_PROPERTIES);

            FilterProperties filterProperties = instanceProperties.merge(annotationFilterProperties);

            BooleanSupplier pathMatches = () -> filterByPath(filterProperties.getPatterns(), requestPath);
            BooleanSupplier methodMatches = () -> filterByMethod(filterProperties.getMethods(), method);
            BooleanSupplier serviceIdMatches = () -> filterByServiceId(filterProperties.getServiceId());
            BooleanSupplier filterStereotypeMatched = () -> filterByStereotype(filterProperties.getStereotypes(), clientDeclaredFilterAnnotations);

            boolean filterMatched = Stream.of(pathMatches, methodMatches, serviceIdMatches, filterStereotypeMatched)
                    .allMatch(BooleanSupplier::getAsBoolean);

            if (filterMatched) {
                filterList.add(filter);
            }
        }

        return filterList;
    }

    private boolean filterByPath(String[] patterns, String requestPath) {
        return patterns.length == 0
                || Arrays.stream(patterns).anyMatch(pathPattern -> PathMatcher.ANT.matches(pathPattern, requestPath));
    }

    private boolean filterByMethod(io.micronaut.http.HttpMethod[] methods, HttpMethod method) {
        return ArrayUtils.isEmpty(methods) || Arrays.asList(methods).contains(method);
    }

    private boolean filterByServiceId(String[] clients) {
        return ArrayUtils.isEmpty(clients) || Arrays.stream(clients).anyMatch(clientIdentifiers::contains);
    }

    private boolean filterByStereotype(Class[] filterStereotypes,
                                       Class[] declaredFilterAnnotations) {

        Set<Class> declaredAnnotationSet = Arrays.stream(declaredFilterAnnotations).collect(Collectors.toSet());
        boolean markerPresent = Arrays.stream(filterStereotypes).anyMatch(declaredAnnotationSet::contains);
        boolean markerIsRequired = filterStereotypes.length > 0;

        return !markerIsRequired || markerPresent;
    }
}
