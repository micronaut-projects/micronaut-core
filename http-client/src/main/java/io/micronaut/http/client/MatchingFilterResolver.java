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
 */
package io.micronaut.http.client;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.HttpFilterQualifier;
import io.micronaut.http.filter.FilterProperties;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.MatchingHttpClientFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolver that responsible to finding all the http client filters that should be applied to given request.
 *
 * @since 1.2.4
 * @author svishnyakoff
 */
@Primary
@Prototype
@Internal
@BootstrapContextCompatible
public class MatchingFilterResolver implements FilterResolver {
    private static final Logger LOG = LoggerFactory.getLogger(MatchingFilterResolver.class);

    private final Collection<HttpClientFilter> filters;
    private final AnnotationMetadataResolver annotationMetadataResolver;
    private final Set<String> clientIdentifiers;
    private final Map<HttpClientFilter, FilterProperties> filterPropertiesMap;
    private final List<Class<? extends Annotation>> clientDeclaredFilterAnnotations;

    /**
     * @param annotationMetadata         annotation metadata of a declarative http client
     * @param clientIdentifiers          client identifiers associated with a client
     * @param annotationMetadataResolver annotation medata resolver
     * @param filters                    all filters
     */
    @Inject
    public MatchingFilterResolver(@Nullable @Parameter AnnotationMetadata annotationMetadata,
                                  @Nullable @Parameter Set<String> clientIdentifiers,
                                  @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                                  @Nullable Collection<HttpClientFilter> filters) {
        this.filters = filters;
        this.annotationMetadataResolver = Optional.ofNullable(annotationMetadataResolver)
                .orElse(AnnotationMetadataResolver.DEFAULT);
        this.clientIdentifiers = Optional.ofNullable(clientIdentifiers).orElse(Collections.emptySet());

        annotationMetadata = Optional.ofNullable(annotationMetadata).orElse(AnnotationMetadata.EMPTY_METADATA);
        this.filterPropertiesMap = Optional.ofNullable(filters).orElse(Collections.emptyList())
                .stream()
                .collect(Collectors.toMap(Function.identity(), this::buildFilterProperties));
        this.clientDeclaredFilterAnnotations = annotationMetadata.getAnnotationTypesByStereotype(HttpFilterQualifier.class);
    }

    /**
     * @param request    http request
     * @param requestURI path URI
     * @return filters that should be applied to request
     */
    @Override
    public List<HttpClientFilter> resolveFilters(io.micronaut.http.HttpRequest<?> request,
                                                 URI requestURI) {
        List<HttpClientFilter> filterList = new ArrayList<>(10);
        String requestPath = StringUtils.prependUri("/", requestURI.getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        for (HttpClientFilter filter : filters) {
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }

            FilterProperties filterProperties = filterPropertiesMap.get(filter);

            if (isPathMatches(filterProperties.getPatterns(), requestPath)
                    && isMethodMatches(filterProperties.getMethods(), method)
                    && isServiceIdMatches(filterProperties.getServiceId())
                    && isStereotypeMatches(filterProperties.getStereotypes(), clientDeclaredFilterAnnotations)
            ) {
                filterList.add(filter);
            }
        }

        return filterList;
    }

    private FilterProperties buildFilterProperties(HttpClientFilter filter) {

        Class<? extends Annotation>[] declaredFilterAnnotations = (Class<? extends Annotation>[]) annotationMetadataResolver.resolveMetadata(filter)
                .getAnnotationTypesByStereotype(HttpFilterQualifier.class)
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
                .orElse(new FilterProperties(declaredFilterAnnotations));

        FilterProperties instanceProperties = Optional.of(filter)
                .filter(f -> f instanceof MatchingFilterResolver)
                .map(f -> (MatchingHttpClientFilter) f)
                .map(MatchingHttpClientFilter::getFilterProperties)
                .orElse(FilterProperties.EMPTY_FILTER_PROPERTIES);

        for (Class<? extends Annotation> stereotype : instanceProperties.getStereotypes()) {
            if (!annotationMetadataResolver.resolveMetadata(stereotype).hasStereotype(HttpFilterQualifier.class)) {
                LOG.warn("Filter stereotype annotation {} is not marked with @FilterAnnotation that makes it completely ignored.", stereotype.getSimpleName());
            }
        }

        return instanceProperties.merge(annotationFilterProperties);
    }

    private boolean isPathMatches(String[] patterns, String requestPath) {
        return patterns.length == 0
                || Arrays.stream(patterns).anyMatch(pathPattern -> PathMatcher.ANT.matches(pathPattern, requestPath));
    }

    private boolean isMethodMatches(io.micronaut.http.HttpMethod[] methods, HttpMethod method) {
        return ArrayUtils.isEmpty(methods) || Arrays.asList(methods).contains(method);
    }

    private boolean isServiceIdMatches(String[] clients) {
        return ArrayUtils.isEmpty(clients) || Arrays.stream(clients).anyMatch(clientIdentifiers::contains);
    }

    private boolean isStereotypeMatches(Class[] filterStereotypes,
                                        List<Class<? extends Annotation>> declaredFilterAnnotations) {

        Set<Class> declaredAnnotationSet = new HashSet<>(declaredFilterAnnotations);
        boolean filterAnnotationPresent = Arrays.stream(filterStereotypes).anyMatch(declaredAnnotationSet::contains);
        boolean filterByAnnotationIsRequired = filterStereotypes.length > 0;

        return !filterByAnnotationIsRequired || filterAnnotationPresent;
    }
}
