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
package io.micronaut.http.client.filter;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Parameter;
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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpFilterResolver;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves filters for http clients.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.3.0
 */
@Internal
@Prototype
@BootstrapContextCompatible
public class HttpClientFilterResolver implements HttpFilterResolver {

    private final List<HttpClientFilterEntry> clientFilters;

    /**
     * Default constructor.
     *
     * @param clientIdentifiers          The client identifiers
     * @param annotationValue            The filter annotation
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param clientFilters              All client filters
     */
    public HttpClientFilterResolver(
            @Parameter @Nullable Collection<String> clientIdentifiers,
            @Parameter @Nullable AnnotationValue<?> annotationValue,
            @Nullable AnnotationMetadataResolver annotationMetadataResolver,
            List<HttpClientFilter> clientFilters) {
        if (clientIdentifiers == null) {
            clientIdentifiers = Collections.emptyList();
        }
        if (annotationMetadataResolver == null) {
            annotationMetadataResolver = AnnotationMetadataResolver.DEFAULT;
        }
        AnnotationMetadataResolver finalAnnotationMetadataResolver = annotationMetadataResolver;
        Collection<String> finalClientIdentifiers = clientIdentifiers;
        this.clientFilters = clientFilters.stream()
                .map(httpClientFilter -> {
                    AnnotationMetadata annotationMetadata;
                    annotationMetadata = finalAnnotationMetadataResolver.resolveMetadata(httpClientFilter);
                    HttpMethod[] methods = annotationMetadata.enumValues(Filter.class, "methods", HttpMethod.class);
                    final List<HttpMethod> httpMethods = new ArrayList<>(Arrays.asList(methods));
                    if (annotationMetadata.hasStereotype(FilterMatcher.class)) {
                        httpMethods.addAll(
                            Arrays.asList(annotationMetadata.enumValues(FilterMatcher.class, "methods", HttpMethod.class))
                        );
                    }

                    return new HttpClientFilterEntry(
                            httpClientFilter,
                            annotationMetadata,
                            httpMethods,
                            annotationMetadata.stringValues(Filter.class)
                    );
                }).filter(entry -> {
                    AnnotationMetadata annotationMetadata = entry.annotationMetadata;
                    boolean matches = !annotationMetadata.hasStereotype(FilterMatcher.class);
                    if (annotationValue != null && !matches) {
                        matches = annotationMetadata.hasAnnotation(annotationValue.getAnnotationName());
                    }

                    if (matches) {
                        String[] clients = annotationMetadata.stringValues(Filter.class, "serviceId");
                        boolean hasClients = ArrayUtils.isNotEmpty(clients);
                        if (hasClients) {
                            matches = containsIdentifier(finalClientIdentifiers, clients);
                        }
                    }
                    return matches;
                }).collect(Collectors.toList());
    }

    @Override
    public List<HttpClientFilter> resolveFilters(HttpRequest<?> request) {
        String requestPath = StringUtils.prependUri("/", request.getUri().getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        List<HttpClientFilter> filterList = new ArrayList<>(clientFilters.size());
        for (HttpClientFilterEntry filterEntry : clientFilters) {
            final HttpClientFilter filter = filterEntry.httpClientFilter;
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }
            boolean matches = true;
            if (filterEntry.hasMethods) {
                matches = anyMethodMatches(method, filterEntry.filterMethods);
            }
            if (filterEntry.hasPatterns) {
                matches = matches && anyPatternMatches(requestPath, filterEntry.patterns);
            }

            if (matches) {
                filterList.add(filter);
            }
        }
        return filterList;
    }

    private boolean containsIdentifier(Collection<String> clientIdentifiers, String[] clients) {
        return Arrays.stream(clients).anyMatch(clientIdentifiers::contains);
    }

    private boolean anyPatternMatches(String requestPath, String[] patterns) {
        return Arrays.stream(patterns).anyMatch(pattern -> PathMatcher.ANT.matches(pattern, requestPath));
    }

    private boolean anyMethodMatches(HttpMethod requestMethod, List<HttpMethod> methods) {
        return methods.contains(requestMethod);
    }

    /**
     * Internal entry to match a filter.
     */
    private final class HttpClientFilterEntry {
        private final HttpClientFilter httpClientFilter;
        private final AnnotationMetadata annotationMetadata;
        private final List<HttpMethod> filterMethods;
        private final String[] patterns;
        private final boolean hasMethods;
        private final boolean hasPatterns;

        HttpClientFilterEntry(
                HttpClientFilter httpClientFilter,
                AnnotationMetadata annotationMetadata,
                List<HttpMethod> httpMethods,
                String[] patterns) {
            this.httpClientFilter = httpClientFilter;
            this.annotationMetadata = annotationMetadata;
            this.filterMethods = httpMethods;
            this.patterns = patterns;
            this.hasMethods = !filterMethods.isEmpty();
            this.hasPatterns = ArrayUtils.isNotEmpty(patterns);
        }
    }
}
