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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;

import java.util.Collections;
import java.util.Set;

/**
 * Internal entry to match a filter.
 *
 * @author graemerocher
 * @since 2.0
 */
final class DefaultFilterEntry implements HttpFilterResolver.FilterEntry {
    private final GenericHttpFilter httpFilter;
    private final AnnotationMetadata annotationMetadata;
    private final Set<HttpMethod> filterMethods;
    private final String[] patterns;
    private final boolean hasMethods;
    private final boolean hasPatterns;
    private final FilterPatternStyle patternStyle;

    /**
     * Default constructor.
     * @param filter The filter
     * @param annotationMetadata The annotation metadata
     * @param httpMethods The methods
     * @param patternStyle the pattern style
     * @param patterns THe patterns
     */
    DefaultFilterEntry(
            GenericHttpFilter filter,
            AnnotationMetadata annotationMetadata,
            Set<HttpMethod> httpMethods,
            FilterPatternStyle patternStyle,
            String[] patterns) {
        this.httpFilter = filter;
        this.annotationMetadata = annotationMetadata;
        this.filterMethods = httpMethods != null ? Collections.unmodifiableSet(httpMethods) : Collections.emptySet();
        this.patterns = patterns != null ? patterns : StringUtils.EMPTY_STRING_ARRAY;
        this.patternStyle = patternStyle != null ? patternStyle : FilterPatternStyle.defaultStyle();
        this.hasMethods = CollectionUtils.isNotEmpty(filterMethods);
        this.hasPatterns = ArrayUtils.isNotEmpty(patterns);
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public GenericHttpFilter getFilter() {
        return httpFilter;
    }

    @Override
    public Set<HttpMethod> getFilterMethods() {
        return filterMethods;
    }

    @Override
    public String[] getPatterns() {
        return patterns;
    }

    @Override
    public FilterPatternStyle getPatternStyle() {
        return patternStyle;
    }

    @Override
    public boolean hasMethods() {
        return hasMethods;
    }

    @Override
    public boolean hasPatterns() {
        return hasPatterns;
    }
}
