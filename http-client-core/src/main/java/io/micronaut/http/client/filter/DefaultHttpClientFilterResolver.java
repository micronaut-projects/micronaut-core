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
package io.micronaut.http.client.filter;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
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
import io.micronaut.http.filter.HttpClientFilterResolver;
import javax.inject.Singleton;
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
@Singleton
@BootstrapContextCompatible
public class DefaultHttpClientFilterResolver implements HttpClientFilterResolver<ClientFilterResolutionContext> {

    private final List<HttpClientFilter> clientFilters;
    private final AnnotationMetadataResolver annotationMetadataResolver;

    /**
     * Default constructor.
     *
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param clientFilters              All client filters
     */
    public DefaultHttpClientFilterResolver(
            AnnotationMetadataResolver annotationMetadataResolver,
            List<HttpClientFilter> clientFilters) {
        this.annotationMetadataResolver = annotationMetadataResolver;
        this.clientFilters = clientFilters;
    }

    @Override
    public List<FilterEntry<HttpClientFilter>> resolveFilterEntries(ClientFilterResolutionContext context) {
        return clientFilters.stream()
                .map(httpClientFilter -> {
                    AnnotationMetadata annotationMetadata = annotationMetadataResolver.resolveMetadata(httpClientFilter);
                    HttpMethod[] methods = annotationMetadata.enumValues(Filter.class, "methods", HttpMethod.class);
                    final Set<HttpMethod> httpMethods = new HashSet<>(Arrays.asList(methods));
                    if (annotationMetadata.hasStereotype(FilterMatcher.class)) {
                        httpMethods.addAll(
                                Arrays.asList(annotationMetadata.enumValues(FilterMatcher.class, "methods", HttpMethod.class))
                        );
                    }

                    return FilterEntry.of(
                            httpClientFilter,
                            annotationMetadata,
                            httpMethods,
                            annotationMetadata.stringValues(Filter.class)
                    );
                }).filter(entry -> {
                    AnnotationMetadata annotationMetadata = entry.getAnnotationMetadata();
                    boolean matches = !annotationMetadata.hasStereotype(FilterMatcher.class);
                    String filterAnnotation = annotationMetadata.getAnnotationNameByStereotype(FilterMatcher.class).orElse(null);
                    if (filterAnnotation != null && !matches) {
                        matches = context.getAnnotationMetadata().hasAnnotation(filterAnnotation);
                    }

                    if (matches) {
                        String[] clients = annotationMetadata.stringValues(Filter.class, "serviceId");
                        boolean hasClients = ArrayUtils.isNotEmpty(clients);
                        if (hasClients) {
                            matches = containsIdentifier(context.getClientIds(), clients);
                        }
                    }
                    return matches;
                }).collect(Collectors.toList());
    }

    @Override
    public List<HttpClientFilter> resolveFilters(HttpRequest<?> request, List<FilterEntry<HttpClientFilter>> filterEntries) {
        String requestPath = StringUtils.prependUri("/", request.getUri().getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        List<HttpClientFilter> filterList = new ArrayList<>(filterEntries.size());
        for (FilterEntry<HttpClientFilter> filterEntry : filterEntries) {
            final HttpClientFilter filter = filterEntry.getFilter();
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }
            boolean matches = true;
            if (filterEntry.hasMethods()) {
                matches = anyMethodMatches(method, filterEntry.getFilterMethods());
            }
            if (filterEntry.hasPatterns()) {
                matches = matches && anyPatternMatches(requestPath, filterEntry.getPatterns());
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

    private boolean anyMethodMatches(HttpMethod requestMethod, Collection<HttpMethod> methods) {
        return methods.contains(requestMethod);
    }

}
