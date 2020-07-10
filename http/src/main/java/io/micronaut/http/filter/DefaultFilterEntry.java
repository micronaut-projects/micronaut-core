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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
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
 * @param <T> The filter type
 */
final class DefaultFilterEntry<T extends HttpFilter> implements HttpFilterResolver.FilterEntry<T> {
    private final T httpFilter;
    private final AnnotationMetadata annotationMetadata;
    private final Set<HttpMethod> filterMethods;
    private final String[] patterns;
    private final boolean hasMethods;
    private final boolean hasPatterns;

    /**
     * Default constructor.
     * @param filter The filter
     * @param annotationMetadata The annotation metadata
     * @param httpMethods The methods
     * @param patterns THe patterns
     */
    DefaultFilterEntry(
            T filter,
            AnnotationMetadata annotationMetadata,
            Set<HttpMethod> httpMethods,
            String[] patterns) {
        this.httpFilter = filter;
        this.annotationMetadata = annotationMetadata;
        this.filterMethods = httpMethods != null ? Collections.unmodifiableSet(httpMethods) : Collections.emptySet();
        this.patterns = patterns != null ? patterns : StringUtils.EMPTY_STRING_ARRAY;
        this.hasMethods = CollectionUtils.isNotEmpty(filterMethods);
        this.hasPatterns = ArrayUtils.isNotEmpty(patterns);
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public T getFilter() {
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
    public boolean hasMethods() {
        return hasMethods;
    }

    @Override
    public boolean hasPatterns() {
        return hasPatterns;
    }
}
