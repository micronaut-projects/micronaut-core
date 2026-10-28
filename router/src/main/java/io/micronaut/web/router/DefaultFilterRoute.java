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
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.GenericHttpFilter;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
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
@Internal
final class DefaultFilterRoute implements FilterRoute {

    private final List<String> patterns = new ArrayList<>(1);
    private final List<String> contextPathPatterns = new ArrayList<>(0);
    private @Nullable String contextPath;
    private final Supplier<GenericHttpFilter> filterSupplier;
    private final AnnotationMetadataResolver annotationMetadataResolver;
    private Set<HttpMethod> httpMethods;
    private FilterPatternStyle patternStyle;
    private volatile GenericHttpFilter filter;
    private AnnotationMetadata annotationMetadata;
    private final boolean isPreMatching;
    private String matchingAnnotation;

    DefaultFilterRoute(Supplier<GenericHttpFilter> filter, AnnotationMetadataResolver annotationMetadataResolver, boolean isPreMatching) {
        Objects.requireNonNull(filter, "HttpFilter argument is required");
        this.filterSupplier = filter;
        this.annotationMetadataResolver = annotationMetadataResolver;
        this.isPreMatching = isPreMatching;
    }

    DefaultFilterRoute(Supplier<GenericHttpFilter> filter, AnnotationMetadata annotationMetadata, boolean isPreMatching) {
        Objects.requireNonNull(filter, "HttpFilter argument is required");
        this.filterSupplier = filter;
        this.annotationMetadataResolver = AnnotationMetadataResolver.DEFAULT;
        this.annotationMetadata = annotationMetadata;
        this.isPreMatching = isPreMatching;
    }

    /**
     * @param pattern A pattern
     * @param filter A {@link Supplier} for an HTTP filter
     * @param annotationMetadataResolver The annotation metadata resolver
     */
    DefaultFilterRoute(String pattern, Supplier<GenericHttpFilter> filter, AnnotationMetadataResolver annotationMetadataResolver) {
        this(pattern, filter, annotationMetadataResolver, false);
    }

    /**
     * @param pattern A pattern
     * @param filter A {@link Supplier} for an HTTP filter
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param isPreMatching Is pre-matching filter
     * @since 4.6
     */
    DefaultFilterRoute(String pattern, Supplier<GenericHttpFilter> filter, AnnotationMetadataResolver annotationMetadataResolver, boolean isPreMatching) {
        this(filter, annotationMetadataResolver, isPreMatching);
        Objects.requireNonNull(pattern, "Pattern argument is required");
        this.patterns.add(pattern);
    }

    /**
     * @param pattern A pattern
     * @param filter A {@link Supplier} for an HTTP filter
     * @param annotationMetadata The annotation metadata
     * @param isPreMatching Is pre-matching filter
     * @since 4.6
     */
    DefaultFilterRoute(String pattern, Supplier<GenericHttpFilter> filter, AnnotationMetadata annotationMetadata, boolean isPreMatching) {
        this(filter, AnnotationMetadataResolver.DEFAULT, isPreMatching);
        Objects.requireNonNull(pattern, "Pattern argument is required");
        this.patterns.add(pattern);
        this.annotationMetadata = annotationMetadata;
        this.matchingAnnotation = FilterRoute.super.findMatchingAnnotation();
    }

    /**
     * @param pattern A pattern
     * @param filter A {@link Supplier} for an HTTP filter
     */
    DefaultFilterRoute(String pattern, Supplier<GenericHttpFilter> filter) {
       this(pattern, filter, AnnotationMetadataResolver.DEFAULT);
    }

    @Override
    public boolean isPreMatching() {
        return isPreMatching;
    }

    @Override
    public String findMatchingAnnotation() {
        if (matchingAnnotation == null) {
            matchingAnnotation = FilterRoute.super.findMatchingAnnotation();
        }
        return matchingAnnotation;
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
    @NonNull
    public GenericHttpFilter getFilter() {
        GenericHttpFilter filter = this.filter;
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
        String contextPath = this.contextPath;
        if (contextPath == null || contextPathPatterns.isEmpty()) {
            return patterns.toArray(StringUtils.EMPTY_STRING_ARRAY);
        }
        List<String> all = new ArrayList<>(patterns.size() + contextPathPatterns.size());
        all.addAll(patterns);
        for (String pattern : contextPathPatterns) {
            all.add(StringUtils.prependUri(contextPath, pattern));
        }
        return all.toArray(StringUtils.EMPTY_STRING_ARRAY);
    }

    /**
     * Makes the patterns added so far relative to the server context path. A pattern that already starts
     * with the context path segment is kept as is. A relative pattern matches the part of a request path
     * after the context path, and the whole path of a request outside the context path (routes such as
     * WebSocket or function routes that are not mounted under the context path).
     * Must be called after the patterns and the pattern style are set.
     *
     * @param contextPath The server context path
     */
    void contextPath(@Nullable String contextPath) {
        if (contextPath == null) {
            return;
        }
        String normalized = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return;
        }
        this.contextPath = normalized;
        boolean ant = getPatternStyle() == FilterPatternStyle.ANT;
        for (Iterator<String> i = patterns.iterator(); i.hasNext(); ) {
            String pattern = i.next();
            if (!isUnderContextPath(normalized, pattern)) {
                i.remove();
                contextPathPatterns.add(ant && !pattern.startsWith("/") ? "/" + pattern : pattern);
            }
        }
    }

    private static boolean isUnderContextPath(String contextPath, String path) {
        return path.startsWith(contextPath) && (path.length() == contextPath.length() || path.charAt(contextPath.length()) == '/');
    }

    @Override
    public FilterPatternStyle getPatternStyle() {
        return patternStyle != null ? patternStyle : FilterPatternStyle.defaultStyle();
    }

    @Override
    public Optional<GenericHttpFilter> match(HttpMethod method, URI uri) {
        return match(method, uri.getRawPath());
    }

    @Override
    public Optional<GenericHttpFilter> match(HttpMethod method, String path) {
        if (httpMethods != null && !httpMethods.contains(method)) {
            return Optional.empty();
        }
        PathMatcher matcher = getPatternStyle().getPathMatcher();
        for (String pattern : patterns) {
            if (matches(matcher, pattern, path)) {
                return enabledFilter();
            }
        }
        String contextPath = this.contextPath;
        if (contextPath != null && !contextPathPatterns.isEmpty()) {
            String relativePath = path;
            if (isUnderContextPath(contextPath, path)) {
                relativePath = path.length() == contextPath.length() ? "/" : path.substring(contextPath.length());
            }
            for (String pattern : contextPathPatterns) {
                if (matches(matcher, pattern, relativePath)) {
                    return enabledFilter();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<GenericHttpFilter> enabledFilter() {
        GenericHttpFilter filter = getFilter();
        if (!GenericHttpFilter.isEnabled(filter)) {
            return Optional.empty();
        }
        return Optional.of(filter);
    }

    /**
     * Match a filter pattern against the request path and against the forms of the path the
     * router and the static resource resolver use: the path without its trailing slash, and the
     * percent-decoded path.
     *
     * @param matcher The path matcher
     * @param pattern The filter pattern
     * @param path    The raw request path
     * @return Whether any form of the path matches the pattern
     */
    static boolean matches(PathMatcher matcher, String pattern, String path) {
        if (matcher.matches(pattern, path)) {
            return true;
        }
        if (path.isEmpty()) {
            return matcher.matches(pattern, "/");
        }
        String stripped = trimTrailingSlash(path);
        if (stripped != null && matcher.matches(pattern, stripped)) {
            return true;
        }
        if (path.indexOf('%') == -1) {
            return false;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (matcher.matches(pattern, decoded)) {
            return true;
        }
        String decodedStripped = trimTrailingSlash(decoded);
        return decodedStripped != null && matcher.matches(pattern, decodedStripped);
    }

    @Nullable
    private static String trimTrailingSlash(String path) {
        int length = path.length();
        if (length > 1 && path.charAt(length - 1) == '/') {
            return path.substring(0, length - 1);
        }
        return null;
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

    @Override
    public FilterRoute patternStyle(FilterPatternStyle patternStyle) {
        this.patternStyle = patternStyle;
        return this;
    }
}
