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
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A contract for resolving filters for a given request.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.3.0
 * @param <F> The filter type
 * @param <T> The resolution context type
 */
public interface HttpFilterResolver<F extends HttpFilter, T extends AnnotationMetadataProvider> {

    /**
     * Resolves the initial list of filters
     * @param context The context
     * @return The filters
     * @since 2.0
     */
    List<FilterEntry<F>> resolveFilterEntries(T context);

    /**
     * Returns which filters should apply for the given request.
     *
     * @param request The request
     * @param filterEntries the filter entries
     * @return The list of filters
     */
    List<F> resolveFilters(HttpRequest<?> request, List<FilterEntry<F>> filterEntries);

    /**
     * A resolved filter entry.
     * @param <F> The filter type
     */
    interface FilterEntry<F> extends AnnotationMetadataProvider {
        /**
         * @return The filter
         */
        @NonNull F getFilter();

        /**
         * @return The filter methods.
         */
        @NonNull
        Set<HttpMethod> getFilterMethods();

        /**
         * @return The filter patterns
         */
        @NonNull String[] getPatterns();

        /**
         * @return Does the entry define any methods.
         */
        default boolean hasMethods() {
            return CollectionUtils.isNotEmpty(getFilterMethods());
        }

        /**
         * @return Are any patterns defined
         */
        default boolean hasPatterns() {
            return ArrayUtils.isNotEmpty(getPatterns());
        }

        /**
         * Creates a filter entry for the given arguments.
         * @param filter The filter
         * @param annotationMetadata The annotation metadata
         * @param methods The methods
         * @param patterns The patterns
         * @return The filter entry
         * @param <FT> the filter type
         */
        static <FT extends HttpFilter> FilterEntry<FT> of(
                @NonNull FT filter,
                @Nullable AnnotationMetadata annotationMetadata,
                @Nullable Set<HttpMethod> methods, String...patterns) {
            return new DefaultFilterEntry<>(
                    Objects.requireNonNull(filter, "Filter cannot be null"),
                    annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA,
                    methods,
                    patterns
            );
        }
    }
}
