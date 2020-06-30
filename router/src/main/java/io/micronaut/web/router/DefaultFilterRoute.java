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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.util.*;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.HttpFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Default implementation of {@link FilterRoute}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultFilterRoute implements FilterRoute {

    private final List<String> patterns = new ArrayList<>(1);
    private final Supplier<HttpFilter> filterSupplier;
    private final AnnotationMetadataResolver annotationMetadataResolver;
    private Set<HttpMethod> httpMethods;
    private HttpFilter filter;
    private AnnotationMetadata annotationMetadata;

    /**
     * @param pattern A pattern
     * @param filter A {@link Supplier} for an HTTP filter
     * @param annotationMetadataResolver The annotation metadata resolver
     */
    DefaultFilterRoute(String pattern, Supplier<HttpFilter> filter, AnnotationMetadataResolver annotationMetadataResolver) {
        Objects.requireNonNull(pattern, "Pattern argument is required");
        Objects.requireNonNull(pattern, "HttpFilter argument is required");
        this.filterSupplier = filter;
        this.patterns.add(pattern);
        this.annotationMetadataResolver = annotationMetadataResolver;
    }

    /**
     * @param pattern A pattern
     * @param filter A {@link Supplier} for an HTTP filter
     */
    DefaultFilterRoute(String pattern, Supplier<HttpFilter> filter) {
       this(pattern, filter, AnnotationMetadataResolver.DEFAULT);
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        AnnotationMetadata annotationMetadata = this.annotationMetadata;
        if (annotationMetadata == null) {
            synchronized (this) { // double check
                annotationMetadata = this.annotationMetadata;
                if (annotationMetadata == null) {
                    annotationMetadata = annotationMetadataResolver.resolveMetadata(getFilter());
                    this.annotationMetadata = annotationMetadata;
                }
            }
        }
        return annotationMetadata;
    }

    @Override
    public HttpFilter getFilter() {
        HttpFilter filter = this.filter;
        if (filter == null) {
            synchronized (this) { // double check
                filter = this.filter;
                if (filter == null) {
                    filter = filterSupplier.get();
                    this.filter = filter;
                }
            }
        }
        return filter;
    }

    @NonNull
    @Override
    public Set<HttpMethod> getFilterMethods() {
        return httpMethods;
    }

    @NonNull
    @Override
    public String[] getPatterns() {
        return patterns.toArray(StringUtils.EMPTY_STRING_ARRAY);
    }

    @Override
    public Optional<HttpFilter> match(HttpMethod method, URI uri) {
        if (httpMethods != null && !httpMethods.contains(method)) {
            return Optional.empty();
        }
        String uriStr = uri.getPath();
        AntPathMatcher matcher = PathMatcher.ANT;
        for (String pattern : patterns) {
            if (matcher.matches(pattern, uriStr)) {
                HttpFilter filter = getFilter();
                if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                    return Optional.empty();
                }
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }

    @Override
    public FilterRoute pattern(String pattern) {
        if (StringUtils.isNotEmpty(pattern)) {
            this.patterns.add(pattern);
        }
        return this;
    }

    @Override
    public FilterRoute methods(HttpMethod... methods) {
        if (ArrayUtils.isNotEmpty(methods)) {
            if (httpMethods == null) {
                httpMethods = new HashSet<>();
            }
            httpMethods.addAll(Arrays.asList(methods));
        }
        return this;
    }
}
