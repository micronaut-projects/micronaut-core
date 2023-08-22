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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.filter.BaseFilterProcessor;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
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
public class DefaultHttpClientFilterResolver extends BaseFilterProcessor<ClientFilter> implements HttpClientFilterResolver<ClientFilterResolutionContext> {

    private final List<ClientFilterEntry> clientFilters;

    /**
     * Default constructor.
     *
     * @param beanContext                The bean context
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param legacyClientFilters        All client filters
     */
    public DefaultHttpClientFilterResolver(
        BeanContext beanContext,
        AnnotationMetadataResolver annotationMetadataResolver,
        List<HttpClientFilter> legacyClientFilters) {
        super(beanContext, ClientFilter.class);
        this.clientFilters = legacyClientFilters.stream()
            .map(legacyClientFilter -> createClientFilterEntry(annotationMetadataResolver, legacyClientFilter))
            .collect(Collectors.toList());
    }

    @Override
    public List<FilterEntry> resolveFilterEntries(ClientFilterResolutionContext context) {
        return clientFilters.stream()
            .filter(entry -> matchesClientFilterEntry(context, entry))
            .collect(Collectors.toList());
    }

    @Override
    public List<GenericHttpFilter> resolveFilters(HttpRequest<?> request, List<FilterEntry> filterEntries) {
        String requestPath = StringUtils.prependUri("/", request.getUri().getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        List<GenericHttpFilter> filterList = new ArrayList<>(filterEntries.size());
        for (FilterEntry filterEntry : filterEntries) {
            final GenericHttpFilter filter = filterEntry.getFilter();
            if (!GenericHttpFilter.isEnabled(filter)) {
                continue;
            }
            if (matchesFilterEntry(method, requestPath, filterEntry)) {
                filterList.add(filter);
            }
        }
        return filterList;
    }

    private boolean containsIdentifier(Collection<String> clientIdentifiers, Collection<String> clients) {
        return clients.stream().anyMatch(clientIdentifiers::contains);
    }

    private boolean anyPatternMatches(String requestPath, String[] patterns, FilterPatternStyle patternStyle) {
        return Arrays.stream(patterns).anyMatch(pattern -> patternStyle.getPathMatcher().matches(pattern, requestPath));
    }

    private boolean anyMethodMatches(HttpMethod requestMethod, Collection<HttpMethod> methods) {
        return methods.contains(requestMethod);
    }

    @Override
    protected void addFilter(Supplier<GenericHttpFilter> factory, AnnotationMetadata methodAnnotations, FilterMetadata metadata) {
        clientFilters.add(new ClientFilterEntry(
            factory.get(),
            methodAnnotations,
            metadata.methods() != null ? new HashSet<HttpMethod>(metadata.methods()) : Collections.emptySet(),
            metadata.patternStyle(),
            metadata.patterns(),
            metadata.serviceId(),
            metadata.excludeServiceId()
        ));
    }

    private boolean matchesFilterEntry(@NonNull HttpMethod method,
                                       @NonNull String requestPath,
                                       @NonNull FilterEntry filterEntry) {
        boolean matches = true;
        if (filterEntry.hasMethods()) {
            matches = anyMethodMatches(method, filterEntry.getFilterMethods());
        }
        if (filterEntry.hasPatterns()) {
            matches = matches && anyPatternMatches(requestPath, filterEntry.getPatterns(), filterEntry.getPatternStyle());
        }
        return matches;
    }

    private boolean matchesClientFilterEntry(@NonNull ClientFilterResolutionContext context,
                                             @NonNull ClientFilterEntry entry) {
        AnnotationMetadata annotationMetadata = entry.getAnnotationMetadata();
        boolean matches = !annotationMetadata.hasStereotype(FilterMatcher.class);
        String filterAnnotation = annotationMetadata.getAnnotationNameByStereotype(FilterMatcher.class).orElse(null);
        if (filterAnnotation != null && !matches) {
            matches = context.getAnnotationMetadata().hasStereotype(filterAnnotation);
        }

        if (matches && entry.serviceIds != null) {
            matches = containsIdentifier(context.getClientIds(), entry.serviceIds);
        }
        if (matches && entry.excludeServiceIds != null) {
            matches = !containsIdentifier(context.getClientIds(), entry.excludeServiceIds);
        }
        return matches;
    }

    @NonNull
    private ClientFilterEntry createClientFilterEntry(@NonNull AnnotationMetadataResolver annotationMetadataResolver,
                                                      @NonNull HttpClientFilter httpClientFilter) {
        AnnotationMetadata annotationMetadata = annotationMetadataResolver.resolveMetadata(httpClientFilter);
        FilterPatternStyle patternStyle = annotationMetadata.enumValue(Filter.class,
            "patternStyle", FilterPatternStyle.class).orElse(FilterPatternStyle.ANT);
        return new ClientFilterEntry(
            GenericHttpFilter.createLegacyFilter(httpClientFilter, new FilterOrder.Dynamic(OrderUtil.getOrder(annotationMetadata))),
            annotationMetadata,
            methodsForFilter(annotationMetadata),
            patternStyle,
            List.of(annotationMetadata.stringValues(Filter.class)),
            serviceIdsForFilter(annotationMetadata),
            excludeServiceIdsForFilter(annotationMetadata)
        );
    }

    @Nullable
    private static List<String> excludeServiceIdsForFilter(@NonNull AnnotationMetadata annotationMetadata) {
        return idsForFilter(annotationMetadata, "excludeServiceId");
    }

    @Nullable
    private static List<String> serviceIdsForFilter(@NonNull AnnotationMetadata annotationMetadata) {
        return idsForFilter(annotationMetadata, "serviceId");
    }

    @Nullable
    private static List<String> idsForFilter(@NonNull AnnotationMetadata annotationMetadata, @NonNull String member) {
        String[] ids = annotationMetadata.stringValues(Filter.class, member);
        return ArrayUtils.isNotEmpty(ids) ? List.of(ids) : null;
    }

    @NonNull
    private static Set<HttpMethod> methodsForFilter(@NonNull AnnotationMetadata annotationMetadata) {
        HttpMethod[] methods = annotationMetadata.enumValues(Filter.class, "methods", HttpMethod.class);
        final Set<HttpMethod> httpMethods = new HashSet<>(Arrays.asList(methods));
        if (annotationMetadata.hasStereotype(FilterMatcher.class)) {
            httpMethods.addAll(
                Arrays.asList(annotationMetadata.enumValues(FilterMatcher.class, "methods", HttpMethod.class))
            );
        }
        return httpMethods;
    }

    private record ClientFilterEntry(
        GenericHttpFilter filter,
        AnnotationMetadata annotationMetadata,
        Set<HttpMethod> httpMethods,
        FilterPatternStyle patternStyle,
        List<String> patterns,
        @Nullable List<String> serviceIds,
        @Nullable List<String> excludeServiceIds
    ) implements FilterEntry {

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public GenericHttpFilter getFilter() {
            return filter;
        }

        @Override
        public Set<HttpMethod> getFilterMethods() {
            return httpMethods;
        }

        @Override
        public String[] getPatterns() {
            return patterns.toArray(String[]::new);
        }

        @Override
        public FilterPatternStyle getPatternStyle() {
            return patternStyle;
        }

        @Override
        public boolean hasMethods() {
            return CollectionUtils.isNotEmpty(httpMethods);
        }

        @Override
        public boolean hasPatterns() {
            return CollectionUtils.isNotEmpty(patterns);
        }
    }
}
